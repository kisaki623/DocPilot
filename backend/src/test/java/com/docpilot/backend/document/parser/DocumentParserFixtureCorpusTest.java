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
    void shouldKeepNaturalHtmlArticleStructureWithoutAsideNoise() {
        HtmlDocumentParser parser = new HtmlDocumentParser(fileContentReader);
        when(fileContentReader.readText("service-recovery.html")).thenReturn("""
                <!doctype html>
                <html>
                  <body>
                    <article>
                      <h1>Service Recovery Guide</h1>
                      <p>Owners record the incident decision before closing the service ticket.</p>
                      <h2>Incident Review</h2>
                      <ol><li>Capture the customer impact.</li><li>Confirm the recovery owner.</li></ol>
                      <h3>Evidence Fields</h3>
                      <table>
                        <tr><th>Field</th><th>Required value</th></tr>
                        <tr><td>Trace ID</td><td>Attach to the review</td></tr>
                      </table>
                    </article>
                    <aside><p>Related promotion noise must not enter the knowledge base.</p></aside>
                  </body>
                </html>
                """);

        ParseResult result = parser.parse(input("service-recovery.html", "html", "text/html", 4096L));

        assertThat(result.fullText())
                .contains("# Service Recovery Guide")
                .contains("## Incident Review")
                .contains("### Evidence Fields")
                .contains("Capture the customer impact.")
                .contains("Trace ID | Attach to the review")
                .doesNotContain("Related promotion noise");
        assertThat(result.blocks())
                .filteredOn(block -> block.blockType() == BlockType.LIST)
                .allMatch(block -> "Service Recovery Guide / Incident Review".equals(block.sectionPath()));
        assertThat(result.blocks())
                .filteredOn(block -> block.blockType() == BlockType.TABLE)
                .hasSize(2)
                .allMatch(block -> "Service Recovery Guide / Incident Review / Evidence Fields"
                        .equals(block.sectionPath()));
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
    void shouldResetDocxSectionPathWhenNaturalDocumentStartsAnotherTopLevelSection() throws Exception {
        DocxDocumentParser parser = new DocxDocumentParser(fileContentReader);
        when(fileContentReader.openStream("operations-policy.docx"))
                .thenReturn(new ByteArrayInputStream(naturalDocxBytes()));

        ParseResult result = parser.parse(input("operations-policy.docx", "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 4096L));

        assertThat(result.fullText())
                .contains("# Retention Policy")
                .contains("## Review Window")
                .contains("# Escalation Policy")
                .contains("Escalation owner marker")
                .contains("Priority | Response");
        assertThat(result.blocks())
                .filteredOn(block -> block.blockType() == BlockType.TABLE)
                .hasSize(2)
                .extracting(DocumentBlock::sectionPath)
                .containsExactly("Retention Policy / Review Window", "Escalation Policy");
        assertThat(result.blocks())
                .filteredOn(block -> "Escalation owner marker".equals(block.text()))
                .singleElement()
                .satisfies(block -> assertThat(block.sectionPath()).isEqualTo("Escalation Policy"));
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

    private byte[] naturalDocxBytes() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Retention Policy");
            XWPFParagraph nestedHeading = document.createParagraph();
            nestedHeading.setStyle("Heading2");
            nestedHeading.createRun().setText("Review Window");
            XWPFTable retentionTable = document.createTable(2, 2);
            retentionTable.getRow(0).getCell(0).setText("Record");
            retentionTable.getRow(0).getCell(1).setText("Review day");
            retentionTable.getRow(1).getCell(0).setText("Incident");
            retentionTable.getRow(1).getCell(1).setText("30");
            XWPFParagraph nextHeading = document.createParagraph();
            nextHeading.setStyle("Heading1");
            nextHeading.createRun().setText("Escalation Policy");
            XWPFParagraph owner = document.createParagraph();
            owner.createRun().setText("Escalation owner marker");
            XWPFTable escalationTable = document.createTable(2, 2);
            escalationTable.getRow(0).getCell(0).setText("Priority");
            escalationTable.getRow(0).getCell(1).setText("Response");
            escalationTable.getRow(1).getCell(0).setText("P1");
            escalationTable.getRow(1).getCell(1).setText("Immediate");
            document.write(output);
            return output.toByteArray();
        }
    }
}
