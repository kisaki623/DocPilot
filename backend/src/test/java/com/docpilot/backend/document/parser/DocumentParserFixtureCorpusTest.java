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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentParserFixtureCorpusTest {

    private final FileContentReader fileContentReader = mock(FileContentReader.class);

    @Test
    void shouldKeepPdfPageLocatorAndEmptyPageWarningsInRegressionCorpus() throws Exception {
        PdfDocumentParser parser = new PdfDocumentParser(fileContentReader);
        when(fileContentReader.readBytes(eq("fixture.pdf"), anyLong()))
                .thenReturn(pdfBytes(
                        "Alpha parser fixture overview",
                        "",
                        "Beta appendix source locator marker"
                ));

        ParseResult result = parser.parse(input("fixture.pdf", "pdf", "application/pdf", 4096L));

        assertThat(result.parserName()).isEqualTo("pdfbox");
        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.blockCount()).isEqualTo(2);
        assertThat(result.warnings()).containsExactly("empty_page:2");
        assertThat(result.blocks())
                .extracting(DocumentBlock::pageNumber)
                .containsExactly(1, 3);
        assertThat(result.blocks())
                .extracting(DocumentBlock::sourceLocator)
                .containsExactly("page:1", "page:3");
        assertThat(result.fullText())
                .contains("Alpha parser fixture overview")
                .contains("Beta appendix source locator marker");
    }

    @Test
    void shouldKeepHtmlStructureAndNoiseFilteringInRegressionCorpus() {
        HtmlDocumentParser parser = new HtmlDocumentParser(fileContentReader);
        when(fileContentReader.readText("fixture.html")).thenReturn("""
                <!doctype html>
                <html>
                  <head>
                    <title>DocPilot HTML Fixture</title>
                    <style>.hidden{display:none}</style>
                    <script>window.leak = 'noise';</script>
                  </head>
                  <body>
                    <header>Header noise</header>
                    <nav>Navigation noise</nav>
                    <main>
                      <h1>Operations Handbook</h1>
                      <p>HTML parser keeps useful inline <a href="/local">link text</a>.</p>
                      <h2>Escalation Table</h2>
                      <table>
                        <tr><th>Severity</th><th>Owner</th></tr>
                        <tr><td>P1</td><td>Core Team</td></tr>
                      </table>
                      <ul>
                        <li>Checklist marker</li>
                        <li><a href="/nested">Nested list link marker</a></li>
                      </ul>
                      <a href="/reference">Standalone reference link</a>
                    </main>
                    <footer>Footer noise</footer>
                  </body>
                </html>
                """);

        ParseResult result = parser.parse(input("fixture.html", "html", "text/html", 4096L));

        assertThat(result.parserName()).isEqualTo("jsoup-html");
        assertThat(result.fullText())
                .contains("# Operations Handbook")
                .contains("## Escalation Table")
                .contains("HTML parser keeps useful inline link text.")
                .contains("Severity | Owner")
                .contains("P1 | Core Team")
                .contains("Checklist marker")
                .contains("Nested list link marker")
                .contains("Standalone reference link")
                .doesNotContain("Navigation noise")
                .doesNotContain("Header noise")
                .doesNotContain("Footer noise")
                .doesNotContain("window.leak");
        assertThat(blockTypes(result)).contains(BlockType.HEADING, BlockType.PARAGRAPH, BlockType.TABLE,
                BlockType.LIST, BlockType.LINK);
        assertThat(result.blocks())
                .filteredOn(block -> block.blockType() == BlockType.TABLE)
                .allMatch(block -> "Operations Handbook / Escalation Table".equals(block.sectionPath()));
    }

    @Test
    void shouldKeepDocxHeadingListAndTableStructureInRegressionCorpus() throws Exception {
        DocxDocumentParser parser = new DocxDocumentParser(fileContentReader);
        when(fileContentReader.openStream("fixture.docx"))
                .thenReturn(new ByteArrayInputStream(docxBytes()));

        ParseResult result = parser.parse(input("fixture.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 4096L));

        assertThat(result.parserName()).isEqualTo("poi-docx");
        assertThat(result.fullText())
                .contains("# Customer Playbook")
                .contains("## Renewal Workflow")
                .contains("DOCX paragraph marker")
                .contains("DOCX list marker")
                .contains("Stage | Evidence")
                .contains("Review | Citation required");
        assertThat(blockTypes(result)).contains(BlockType.HEADING, BlockType.PARAGRAPH, BlockType.LIST,
                BlockType.TABLE);
        assertThat(result.blocks())
                .filteredOn(block -> block.blockType() == BlockType.TABLE)
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.sectionPath()).isEqualTo("Customer Playbook / Renewal Workflow");
                    assertThat(block.sourceLocator()).startsWith("docx:table:");
                });
    }

    @Test
    void shouldCoverSupportedParserTypesInRegressionCorpus() {
        ParserRegistry registry = new ParserRegistry(List.of(
                new TextDocumentParser(fileContentReader),
                new PdfDocumentParser(fileContentReader),
                new HtmlDocumentParser(fileContentReader),
                new DocxDocumentParser(fileContentReader)
        ));

        assertThat(registry.select(input("fixture.txt", "txt", "text/plain", 12L)).parserName()).isEqualTo("text");
        assertThat(registry.select(input("fixture.pdf", "pdf", "application/pdf", 12L)).parserName()).isEqualTo("pdfbox");
        assertThat(registry.select(input("fixture.html", "html", "text/html", 12L)).parserName()).isEqualTo("jsoup-html");
        assertThat(registry.select(input("fixture.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 12L)).parserName())
                .isEqualTo("poi-docx");
    }

    private Set<BlockType> blockTypes(ParseResult result) {
        return result.blocks().stream()
                .map(DocumentBlock::blockType)
                .collect(java.util.stream.Collectors.toSet());
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
                if (!pageText.isBlank()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.beginText();
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        contentStream.newLineAtOffset(72, 720);
                        contentStream.showText(pageText);
                        contentStream.endText();
                    }
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
            heading.createRun().setText("Customer Playbook");
            XWPFParagraph nestedHeading = document.createParagraph();
            nestedHeading.setStyle("Heading2");
            nestedHeading.createRun().setText("Renewal Workflow");
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("DOCX paragraph marker");
            XWPFParagraph list = document.createParagraph();
            list.setStyle("ListParagraph");
            list.createRun().setText("DOCX list marker");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Stage");
            table.getRow(0).getCell(1).setText("Evidence");
            table.getRow(1).getCell(0).setText("Review");
            table.getRow(1).getCell(1).setText("Citation required");
            document.write(output);
            return output.toByteArray();
        }
    }
}
