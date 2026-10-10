package com.edusync.pdfupdater.service;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extracts text and raster images from a PDF and converts it into semantic HTML.
 * Note: Complex vector diagrams drawn with PDF paths may be lost. This implementation
 * robustly extracts raster images (photos, PNG/JPEG embedded diagrams) in reading order.
 */
public class HtmlPdfTextStripper extends PDFTextStripper {

    private final StringBuilder htmlContent;
    private float lastY = -1;
    private boolean isNewParagraph = true;
    private boolean inHeading = false;
    private boolean inH1 = true;
    private boolean inParagraph = false;
    private char lastChar = '>';

    private static final float H1_FONT_SIZE_THRESHOLD = 15.0f;
    private static final float H2_FONT_SIZE_THRESHOLD = 13.0f;

    private float dominantFontSize = 12.0f;
    private int dominantFontCount = 0;
    private float currentBlockFontSize = 0;

    private int pageCount = 0;

    // Image tracking
    private List<ImageInfo> currentImages = new ArrayList<>();
    private int currentImageIndex = 0;

    public HtmlPdfTextStripper() throws IOException {
        super();
        this.htmlContent = new StringBuilder();
    }

    public String getHtmlContent() {
        closeBlocks();
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

    private void closeBlocks() {
        if (inHeading) {
            htmlContent.append(inH1 ? "</h1>\n" : "</h2>\n");
            inHeading = false;
        }
        if (inParagraph) {
            htmlContent.append("</p>\n");
            inParagraph = false;
        }
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        pageCount++;
        
        closeBlocks();
        
        if (pageCount > 1) {
            htmlContent.append("<div style=\"page-break-before: always;\"></div>\n");
        }
        
        lastY = -1;
        isNewParagraph = true;
        lastChar = '>';

        // Extract images for the current page
        currentImages.clear();
        currentImageIndex = 0;
        try {
            ImageExtractor extractor = new ImageExtractor(page.getMediaBox().getHeight());
            extractor.processPage(page);
            currentImages.addAll(extractor.getImages());
            
            // Sort images: Primary Y (top to bottom), Secondary X (left to right)
            currentImages.sort((img1, img2) -> {
                // Y values within 5 points are considered the same row
                if (Math.abs(img1.y - img2.y) < 5.0f) {
                    return Float.compare(img1.x, img2.x);
                }
                return Float.compare(img1.y, img2.y);
            });
        } catch (Exception e) {
            System.err.println("Warning: Failed to extract images on page " + pageCount + ". Text processing will continue. Error: " + e.getMessage());
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        // Output any remaining images that appear after all text on this page
        while (currentImageIndex < currentImages.size()) {
            insertImage(currentImages.get(currentImageIndex));
            currentImageIndex++;
        }
        super.endPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (textPositions == null || textPositions.isEmpty()) {
            return;
        }

        TextPosition firstPos = textPositions.get(0);
        float fontSize = firstPos.getFontSizeInPt();
        float currentY = firstPos.getYDirAdj();
        String fontName = firstPos.getFont().getName();
        boolean isBold = fontName != null && fontName.toLowerCase().contains("bold");

        // Check if there are images above this text block to output
        while (currentImageIndex < currentImages.size()) {
            ImageInfo img = currentImages.get(currentImageIndex);
            // If image is above current text (Y is smaller)
            if (img.y < currentY) {
                insertImage(img);
                currentImageIndex++;
            } else {
                break;
            }
        }

        if (fontSize <= 12.5f && fontSize >= 9.0f) {
            dominantFontCount++;
            dominantFontSize = dominantFontSize + (fontSize - dominantFontSize) / dominantFontCount;
        }

        if (lastY != -1) {
            float yDiff = Math.abs(currentY - lastY);
            if (yDiff > fontSize * 1.8f) {
                isNewParagraph = true;
            }
        }

        lastY = currentY;

        boolean isH1 = fontSize >= H1_FONT_SIZE_THRESHOLD;
        boolean isH2 = !isH1 && fontSize >= H2_FONT_SIZE_THRESHOLD;
        boolean isHeadingText = isH1 || isH2;

        if (isNewParagraph) {
            closeBlocks();

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
            if (lastChar != ' ' && lastChar != '>') {
                htmlContent.append(" ");
                lastChar = ' ';
            }
        }

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

    private void insertImage(ImageInfo img) {
        closeBlocks();
        htmlContent.append("<div class=\"image-container\">\n");
        htmlContent.append("<img src=\"data:image/png;base64,")
                   .append(img.base64)
                   .append("\" alt=\"Extracted image\" />\n");
        htmlContent.append("</div>\n");
        isNewParagraph = true; // force a paragraph break
    }

    private void appendEscaped(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&': htmlContent.append("&amp;"); break;
                case '<': htmlContent.append("&lt;"); break;
                case '>': htmlContent.append("&gt;"); break;
                case '"': htmlContent.append("&quot;"); break;
                default: htmlContent.append(c);
            }
        }
    }

    private static class ImageInfo {
        float x;
        float y;
        String base64;

        ImageInfo(float x, float y, String base64) {
            this.x = x;
            this.y = y;
            this.base64 = base64;
        }
    }

    private static class ImageExtractor extends PDFStreamEngine {
        private final List<ImageInfo> images = new ArrayList<>();
        private final float pageHeight;

        public ImageExtractor(float pageHeight) throws IOException {
            this.pageHeight = pageHeight;
            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        public List<ImageInfo> getImages() {
            return images;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName())) {
                try {
                    COSName objectName = (COSName) operands.get(0);
                    PDXObject xobject = getResources().getXObject(objectName);
                    if (xobject instanceof PDImageXObject) {
                        PDImageXObject image = (PDImageXObject) xobject;
                        Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();
                        
                        float x = ctmNew.getTranslateX();
                        float y = ctmNew.getTranslateY();
                        float scaleY = ctmNew.getScaleY();
                        
                        // Convert Y to top-down coordinates
                        float topDownY = pageHeight - y - scaleY;

                        BufferedImage bImage = image.getImage();
                        if (bImage != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(bImage, "png", baos);
                            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                            images.add(new ImageInfo(x, topDownY, base64));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to process an image object. Error: " + e.getMessage());
                }
            } else {
                super.processOperator(operator, operands);
            }
        }
    }
}
