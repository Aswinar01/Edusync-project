package com.edusync.pdfupdater.service;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

/**
 * Extracts text from a PDF and converts it into semantic HTML.
 * Classifies text blocks as headings (h1, h2) or paragraphs (p) based on font size,
 * and detects bold text to wrap in <strong> tags.
 */
public class HtmlPdfTextStripper extends PDFTextStripper {

    private final StringBuilder htmlContent;
    private float lastY = -1;
    private boolean isNewParagraph = true;
    private boolean inHeading = false;
    private boolean inH1 = true;
    private boolean inParagraph = false;
    private char lastChar = '>';

    // Font size thresholds for heading detection
    private static final float H1_FONT_SIZE_THRESHOLD = 15.0f;
    private static final float H2_FONT_SIZE_THRESHOLD = 13.0f;

    // Track the dominant (most common) font size to better classify headings
    private float dominantFontSize = 12.0f;
    private int dominantFontCount = 0;
    private float currentBlockFontSize = 0;

    public HtmlPdfTextStripper() throws IOException {
        super();
        this.htmlContent = new StringBuilder();
    }

    /**
     * Returns the complete XHTML document string.
     * Uses XHTML syntax required by OpenHTMLToPDF.
     */
    public String getHtmlContent() {
        // Close any remaining open tags
        if (inHeading) {
            htmlContent.append(inH1 ? "</h1>\n" : "</h2>\n");
        }
        if (inParagraph) {
            htmlContent.append("</p>\n");
        }

        StringBuilder doc = new StringBuilder();
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        doc.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ");
        doc.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        doc.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        doc.append("<head>\n");
        doc.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        doc.append("<title>EduSync Updated Document</title>\n");
        doc.append("</head>\n");
        doc.append("<body>\n");
        doc.append(htmlContent);
        doc.append("</body>\n</html>");

        return doc.toString();
    }

    private int pageCount = 0;

    @Override
    protected void startPage(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
        super.startPage(page);
        pageCount++;
        
        // Close any open tags before page break
        if (inHeading) {
            htmlContent.append(inH1 ? "</h1>\n" : "</h2>\n");
            inHeading = false;
        }
        if (inParagraph) {
            htmlContent.append("</p>\n");
            inParagraph = false;
        }
        
        // Insert a CSS page break for all pages after the first
        if (pageCount > 1) {
            htmlContent.append("<div style=\"page-break-before: always;\"></div>\n");
        }
        
        // Reset Y-coordinate tracking for the new page
        lastY = -1;
        isNewParagraph = true;
        lastChar = '>';
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (textPositions == null || textPositions.isEmpty()) {
            return;
        }

        // Analyze the font properties of this text chunk
        TextPosition firstPos = textPositions.get(0);
        float fontSize = firstPos.getFontSizeInPt();
        float currentY = firstPos.getYDirAdj();
        String fontName = firstPos.getFont().getName();
        boolean isBold = fontName != null && fontName.toLowerCase().contains("bold");

        // Track the dominant font size for better classification
        if (fontSize <= 12.5f && fontSize >= 9.0f) {
            dominantFontCount++;
            // Running average
            dominantFontSize = dominantFontSize + (fontSize - dominantFontSize) / dominantFontCount;
        }

        // Detect paragraph breaks based on Y position change
        if (lastY != -1) {
            float yDiff = Math.abs(currentY - lastY);
            // A gap larger than ~1.8x the font size indicates a new paragraph
            if (yDiff > fontSize * 1.8f) {
                isNewParagraph = true;
            }
        }

        lastY = currentY;

        // Classify text as heading or body
        boolean isH1 = fontSize >= H1_FONT_SIZE_THRESHOLD;
        boolean isH2 = !isH1 && fontSize >= H2_FONT_SIZE_THRESHOLD;
        boolean isHeadingText = isH1 || isH2;

        if (isNewParagraph) {
            // Close any previous open block
            if (inHeading) {
                htmlContent.append(inH1 ? "</h1>\n" : "</h2>\n");
                inHeading = false;
            }
            if (inParagraph) {
                htmlContent.append("</p>\n");
                inParagraph = false;
            }

            // Open a new block
            if (isH1) {
                htmlContent.append("<h1>");
                inHeading = true;
                inH1 = true;
            } else if (isH2) {
                htmlContent.append("<h2>");
                inHeading = true;
                inH1 = false;
            } else {
                htmlContent.append("<p>");
                inParagraph = true;
            }
            currentBlockFontSize = fontSize;
            isNewParagraph = false;
            lastChar = '>';
        } else {
            // Continuation of same block — add a space if needed
            if (lastChar != ' ' && lastChar != '>') {
                htmlContent.append(" ");
                lastChar = ' ';
            }
        }

        // Wrap bold text in <strong> tags (only for body text, not headings)
        if (isBold && !isHeadingText) {
            htmlContent.append("<strong>");
            appendEscaped(text);
            htmlContent.append("</strong>");
        } else {
            appendEscaped(text);
        }

        if (!text.isEmpty()) {
            lastChar = text.charAt(text.length() - 1);
        }
    }

    /**
     * Appends text to the HTML content, escaping special XML characters.
     */
    private void appendEscaped(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    htmlContent.append("&amp;");
                    break;
                case '<':
                    htmlContent.append("&lt;");
                    break;
                case '>':
                    htmlContent.append("&gt;");
                    break;
                case '"':
                    htmlContent.append("&quot;");
                    break;
                default:
                    htmlContent.append(c);
            }
        }
    }
}
