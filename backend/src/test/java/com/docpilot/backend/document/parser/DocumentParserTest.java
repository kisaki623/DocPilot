package com.docpilot.backend.document.parser;

import com.docpilot.backend.file.storage.FileContentReader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentParserTest {

    private final FileContentReader fileContentReader = mock(FileContentReader.class);

    @Test
    void shouldSelectTextParserAndExtractMarkdownSections() {
        TextDocumentParser textParser = new TextDocumentParser(fileContentReader);
        when(fileContentReader.readText("demo.md")).thenReturn("# Alpha\n\nDocPilot parser marker.");

        ParseResult result = textParser.parse(input("demo.md", "md", "text/markdown", 34L));

        assertEquals("text", result.parserName());
        assertTrue(result.fullText().contains("DocPilot parser marker"));
        assertEquals(BlockType.HEADING, result.blocks().get(0).blockType());
        assertEquals("Alpha", result.blocks().get(0).sectionTitle());
    }

    @Test
    void shouldExtractPdfTextWithPageBlocks() throws Exception {
        PdfDocumentParser parser = new PdfDocumentParser(fileContentReader);
        when(fileContentReader.readBytes(eq("demo.pdf"), anyLong()))
                .thenReturn(pdfBytes("PDF first page marker", "PDF second page marker"));

        ParseResult result = parser.parse(input("demo.pdf", "pdf", "application/pdf", 1024L));

        assertEquals("pdfbox", result.parserName());
        assertEquals(2, result.pageCount());
        assertEquals(2, result.blocks().size());
        assertEquals(1, result.blocks().get(0).pageNumber());
        assertTrue(result.fullText().contains("PDF first page marker"));
        assertTrue(result.fullText().contains("PDF second page marker"));
    }

    @Test
    void shouldRejectCorruptedPdfWithControlledError() {
        PdfDocumentParser parser = new PdfDocumentParser(fileContentReader);
        when(fileContentReader.readBytes(eq("broken.pdf"), anyLong())).thenReturn(new byte[]{1, 2, 3});

        ParserException ex = assertThrows(ParserException.class,
                () -> parser.parse(input("broken.pdf", "pdf", "application/pdf", 3L)));

        assertEquals(ParseErrorCode.CORRUPTED_FILE, ex.getErrorCode());
    }

    @Test
    void shouldExtractHtmlBodyAndRemoveNoisyTags() {
        HtmlDocumentParser parser = new HtmlDocumentParser(fileContentReader);
        when(fileContentReader.readText("demo.html")).thenReturn("""
                <html><head><style>.x{}</style><script>alert(1)</script></head>
                <body><nav>Navigation noise</nav><h1>Alpha Title</h1>
                <p>Useful body marker <a href="/local">local link</a></p>
                <ul><li>List marker</li></ul><footer>Footer noise</footer></body></html>
                """);

        ParseResult result = parser.parse(input("demo.html", "html", "text/html", 200L));

        assertEquals("jsoup-html", result.parserName());
        assertTrue(result.fullText().contains("# Alpha Title"));
        assertTrue(result.fullText().contains("Useful body marker local link"));
        assertTrue(result.fullText().contains("List marker"));
        assertFalse(result.fullText().contains("Navigation noise"));
        assertFalse(result.fullText().contains("alert"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.HEADING));
        DocumentBlock heading = result.blocks().stream()
                .filter(block -> block.blockType() == BlockType.HEADING)
                .findFirst()
                .orElseThrow();
        assertEquals("# Alpha Title",
                result.fullText().substring(heading.startOffset(), heading.endOffset()));
    }

    @Test
    void shouldExtractDocxParagraphHeadingAndTable() throws Exception {
        DocxDocumentParser parser = new DocxDocumentParser(fileContentReader);
        when(fileContentReader.openStream("demo.docx")).thenReturn(new ByteArrayInputStream(docxBytes()));

        ParseResult result = parser.parse(input("demo.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 2048L));

        assertEquals("poi-docx", result.parserName());
        assertTrue(result.fullText().contains("# Docx Section"));
        assertTrue(result.fullText().contains("Docx paragraph marker"));
        assertTrue(result.fullText().contains("Parser | DOCX"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TABLE));
        DocumentBlock heading = result.blocks().stream()
                .filter(block -> block.blockType() == BlockType.HEADING)
                .findFirst()
                .orElseThrow();
        assertEquals("# Docx Section",
                result.fullText().substring(heading.startOffset(), heading.endOffset()));
    }

    @Test
    void shouldSelectParserFromRegistryAndRejectUnsupportedType() {
        ParserRegistry registry = new ParserRegistry(List.of(
                new TextDocumentParser(fileContentReader),
                new PdfDocumentParser(fileContentReader),
                new HtmlDocumentParser(fileContentReader),
                new DocxDocumentParser(fileContentReader)
        ));

        assertEquals("jsoup-html", registry.select(input("demo.htm", "htm", "text/html", 10L)).parserName());

        ParserException ex = assertThrows(ParserException.class,
                () -> registry.select(input("demo.bin", "bin", "application/octet-stream", 10L)));

        assertEquals(ParseErrorCode.UNSUPPORTED_TYPE, ex.getErrorCode());
    }

    @Test
    void shouldRejectFileOverParserLimitBeforeReading() {
        ParserRegistry registry = new ParserRegistry(List.of(new TextDocumentParser(fileContentReader)));
        ParserInput input = new ParserInput(1L, 2L, "large.txt", "txt", "text/plain", 11L, "large.txt",
                new ParserOptions(10L, 10_000L));

        ParserException ex = assertThrows(ParserException.class, () -> registry.parse(input));

        assertEquals(ParseErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
    }

    private ParserInput input(String fileName, String fileExt, String contentType, Long fileSize) {
        return new ParserInput(1L, 2L, fileName, fileExt, contentType, fileSize, fileName, ParserOptions.defaults());
    }

    private byte[] pdfBytes(String... pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docxBytes() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Docx Section");
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Docx paragraph marker");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Name");
            table.getRow(0).getCell(1).setText("Value");
            table.getRow(1).getCell(0).setText("Parser");
            table.getRow(1).getCell(1).setText("DOCX");
            document.write(output);
            return output.toByteArray();
        }
    }
}
