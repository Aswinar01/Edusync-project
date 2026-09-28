package com.edusync.pdfupdater.service;

import com.edusync.pdfupdater.model.UpdateResponse;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PdfProcessorService {

    private final AiService aiService;

    public PdfProcessorService(AiService aiService) {
        this.aiService = aiService;
    }

    public byte[] processPdf(InputStream pdfInputStream) throws Exception {
        // 1. Extract semantic HTML structure from the PDF
        log.info("Step 1: Extracting text from PDF...");
        String extractedHtml;
        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            HtmlPdfTextStripper stripper = new HtmlPdfTextStripper();
            stripper.setSortByPosition(true);
            stripper.getText(document); // triggers writeString processing
            extractedHtml = stripper.getHtmlContent();
        }
        log.info("Extracted HTML length: " + extractedHtml.length());

        // 2. Parse the semantic HTML DOM using JSoup
        Document dom = Jsoup.parse(extractedHtml);

        // Extract plain text for the AI (normalized, no tags)
        String plainText = dom.body().text();
        log.info("Plain text length for AI: " + plainText.length());

        if (plainText.trim().length() < 10) {
            throw new IllegalArgumentException("PDF contains no selectable text. Please ensure the PDF is not a scanned image.");
        }

        // 3. Get outdated sentence updates from Gemini AI
        log.info("Step 2: Calling Gemini AI for fact-checking...");
        List<UpdateResponse.SentenceUpdate> updates = aiService.getOutdatedSentences(plainText);
        if (updates == null) {
            updates = java.util.Collections.emptyList();
        }
        log.info("Step 3: AI returned " + updates.size() + " updates");

        // Deduplicate updates based on original sentence to prevent identical updates from repeating pages
        java.util.Map<String, UpdateResponse.SentenceUpdate> uniqueUpdates = new java.util.LinkedHashMap<>();
        for (UpdateResponse.SentenceUpdate update : updates) {
            if (update.getOriginalSentence() != null) {
                uniqueUpdates.putIfAbsent(update.getOriginalSentence().trim(), update);
            }
        }

        // 4. Inject updates into the DOM
        for (UpdateResponse.SentenceUpdate update : uniqueUpdates.values()) {
            String orig = update.getOriginalSentence();
            if (orig == null || orig.isEmpty()) continue;

            log.info("Replacing: \"" + orig.substring(0, Math.min(50, orig.length())) + "...\"");

            // Normalize original sentence for robust comparison
            String normalizedOrig = orig.replaceAll("\\s+", "");

            // Search through all text-containing elements
            boolean found = false;
            for (Element el : dom.body().getAllElements()) {
                // Skip if this element or its children already contain a correction block (prevents double-injection)
                if (el.hasClass("correction-block") || el.hasClass("correction-old") || !el.select(".correction-block").isEmpty()) {
                    continue;
                }

                String ownText = el.ownText();
                String normalizedOwnText = ownText.replaceAll("\\s+", "");
                
                if (normalizedOwnText.contains(normalizedOrig)) {
                    String replacement =
                            "<span class=\"correction-block\">" +
                            "<span class=\"correction-old\">" + escapeHtml(orig) + "</span>" +
                            "<span class=\"correction-new\">" + escapeHtml(update.getUpdatedSentence()) + "</span>" +
                            "<span class=\"correction-source\">[Source: " + escapeHtml(update.getSource()) + "]</span>" +
                            "</span>";

                    String currentHtml = el.html();
                    String newHtml = currentHtml.replace(escapeHtml(orig), replacement);
                    if (newHtml.equals(currentHtml)) {
                        newHtml = currentHtml.replace(orig, replacement);
                    }
                    
                    if (!newHtml.equals(currentHtml)) {
                        el.html(newHtml);
                        found = true;
                        log.info("Successfully replaced exact text in element: " + el.tagName());
                    } else {
                        // Fallback: If exact replace fails due to hidden formatting/whitespace,
                        // either fully replace the html if it's a tight wrapper, or append safely.
                        if (normalizedOwnText.length() <= normalizedOrig.length() + 50) {
                            el.html(replacement);
                            found = true;
                            log.info("Fallback: Replaced entire element HTML: " + el.tagName());
                        } else {
                            el.append(" " + replacement);
                            found = true;
                            log.info("Fallback: Appended to element: " + el.tagName());
                        }
                    }
                    // Removed 'break;' to ensure identical errors across all pages are fixed simultaneously!
                }
            }
            if (!found) {
                log.warn("Could not find original text in DOM: \"" + orig.substring(0, Math.min(80, orig.length())) + "...\"");
            }
        }

        // 5. Inject production-grade CSS into the document head
        injectProductionCss(dom);

        // 6. Configure JSoup to output valid XHTML (required by OpenHTMLToPDF)
        dom.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8");

        String finalHtml = dom.outerHtml();
        log.info("Step 4: Rendering HTML to PDF (" + finalHtml.length() + " chars)...");

        // 7. Render the XHTML to a PDF using OpenHTMLToPDF
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(finalHtml, "");
            builder.toStream(baos);
            builder.run();
            log.info("Step 5: PDF generated successfully (" + baos.size() + " bytes)");
            return baos.toByteArray();
        }
    }

    /**
     * Injects production-grade CSS styling into the document.
     * Configures A4 page size, proper margins, professional typography,
     * and styling for the update annotations.
     */
    private void injectProductionCss(Document dom) {
        // Remove any existing style tags to avoid conflicts
        dom.select("style").remove();

        String css = "<style>\n" +
                "/* Page setup for A4 with page numbers */\n" +
                "@page {\n" +
                "    size: A4;\n" +
                "    margin: 2.5cm 2cm 2.5cm 2cm;\n" +
                "    @bottom-right {\n" +
                "        content: \"Page \" counter(page);\n" +
                "        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;\n" +
                "        font-size: 9pt;\n" +
                "        color: #777;\n" +
                "    }\n" +
                "    @bottom-left {\n" +
                "        content: \"EduSync AI Verified Document\";\n" +
                "        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;\n" +
                "        font-size: 9pt;\n" +
                "        color: #777;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "/* Base body styling */\n" +
                "body {\n" +
                "    font-family: 'Georgia', serif;\n" +
                "    font-size: 11pt;\n" +
                "    line-height: 1.7;\n" +
                "    color: #2c3e50;\n" +
                "    padding: 0;\n" +
                "    margin: 0;\n" +
                "}\n" +
                "\n" +
                "/* Main heading */\n" +
                "h1 {\n" +
                "    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;\n" +
                "    font-size: 22pt;\n" +
                "    font-weight: 700;\n" +
                "    color: #1a252f;\n" +
                "    text-align: left;\n" +
                "    margin-top: 0;\n" +
                "    margin-bottom: 20pt;\n" +
                "    padding-bottom: 10pt;\n" +
                "    border-bottom: 2pt solid #ecf0f1;\n" +
                "}\n" +
                "\n" +
                "/* Sub heading */\n" +
                "h2 {\n" +
                "    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;\n" +
                "    font-size: 16pt;\n" +
                "    font-weight: 600;\n" +
                "    color: #2c3e50;\n" +
                "    margin-top: 18pt;\n" +
                "    margin-bottom: 10pt;\n" +
                "}\n" +
                "\n" +
                "/* Body paragraphs */\n" +
                "p {\n" +
                "    font-size: 11pt;\n" +
                "    margin-top: 0;\n" +
                "    margin-bottom: 12pt;\n" +
                "    text-align: justify;\n" +
                "}\n" +
                "\n" +
                "/* Bold text */\n" +
                "strong {\n" +
                "    font-weight: bold;\n" +
                "    color: #1a252f;\n" +
                "}\n" +
                "\n" +
                "/* Correction Highlight System */\n" +
                ".correction-block {\n" +
                "    display: inline;\n" +
                "}\n" +
                "\n" +
                ".correction-old {\n" +
                "    text-decoration: line-through;\n" +
                "    color: #95a5a6;\n" +
                "    background-color: #f9f9f9;\n" +
                "    padding: 0 2pt;\n" +
                "    border-radius: 2pt;\n" +
                "}\n" +
                "\n" +
                ".correction-new {\n" +
                "    color: #27ae60;\n" +
                "    font-weight: bold;\n" +
                "    background-color: #eafaf1;\n" +
                "    padding: 0 2pt;\n" +
                "    border-radius: 2pt;\n" +
                "    margin-left: 4pt;\n" +
                "}\n" +
                "\n" +
                ".correction-source {\n" +
                "    color: #2980b9;\n" +
                "    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;\n" +
                "    font-size: 8.5pt;\n" +
                "    font-style: italic;\n" +
                "    margin-left: 4pt;\n" +
                "}\n" +
                "</style>\n";

        Element head = dom.head();
        if (head == null) {
            dom.prepend("<head></head>");
            head = dom.head();
        }
        head.append(css);
    }

    /**
     * Escapes HTML special characters in a string.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
