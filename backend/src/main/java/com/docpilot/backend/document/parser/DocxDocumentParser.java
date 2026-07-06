package com.docpilot.backend.document.parser;

import com.docpilot.backend.file.storage.FileContentReader;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DocxDocumentParser implements DocumentParser {

    private final FileContentReader fileContentReader;

    public DocxDocumentParser(FileContentReader fileContentReader) {
        this.fileContentReader = fileContentReader;
    }

    @Override
    public String parserName() {
        return "poi-docx";
    }

    @Override
    public String parserVersion() {
        return "1";
    }

    @Override
    public boolean supports(ParserInput input) {
        return input != null
                && ("docx".equals(input.fileExt())
                || input.contentType().contains("officedocument.wordprocessingml.document"));
    }

    @Override
    public ParseResult parse(ParserInput input) {
        long start = System.nanoTime();
        try (InputStream stream = fileContentReader.openStream(input.storagePath());
             XWPFDocument document = new XWPFDocument(stream)) {
            StringBuilder fullText = new StringBuilder();
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> sectionPath = new ArrayList<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(paragraph, fullText, sectionPath, blocks);
                } else if (element instanceof XWPFTable table) {
                    appendTable(table, fullText, sectionPath, blocks);
                }
            }
            return ParserTextUtils.result(
                    input,
                    fullText.toString(),
                    blocks,
                    Map.of("format", "docx"),
                    List.of(),
                    this,
                    start,
                    0
            );
        } catch (ParserException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ParserException(ParseErrorCode.CORRUPTED_FILE, "docx parser failed to read document", ex);
        }
    }

    private void appendParagraph(XWPFParagraph paragraph,
                                 StringBuilder fullText,
                                 List<String> sectionPath,
                                 List<DocumentBlock> blocks) {
        String text = ParserTextUtils.normalize(paragraph.getText());
        if (text.isBlank()) {
            return;
        }
        BlockType blockType = isHeading(paragraph) ? BlockType.HEADING : BlockType.PARAGRAPH;
        if (blockType == BlockType.HEADING) {
            updateSectionPath(sectionPath, headingDepth(paragraph), text);
        }
        String fullTextBlock = blockType == BlockType.HEADING
                ? markdownHeading(headingDepth(paragraph), text)
                : text;
        int startOffset = ParserTextUtils.appendText(fullText, fullTextBlock);
        String currentSection = sectionPath.isEmpty() ? "" : sectionPath.get(sectionPath.size() - 1);
        blocks.add(new DocumentBlock(
                blocks.size(),
                blockType,
                text,
                null,
                currentSection,
                String.join(" / ", sectionPath),
                startOffset,
                startOffset + text.length(),
                "docx:paragraph:" + blocks.size()
        ));
    }

    private void appendTable(XWPFTable table,
                             StringBuilder fullText,
                             List<String> sectionPath,
                             List<DocumentBlock> blocks) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = ParserTextUtils.normalize(cell.getText());
                if (!cellText.isBlank()) {
                    cells.add(cellText);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(String.join(" | ", cells));
            }
        }
        String text = ParserTextUtils.normalize(String.join("\n", rows));
        if (text.isBlank()) {
            return;
        }
        int startOffset = ParserTextUtils.appendText(fullText, text);
        String currentSection = sectionPath.isEmpty() ? "" : sectionPath.get(sectionPath.size() - 1);
        blocks.add(new DocumentBlock(
                blocks.size(),
                BlockType.TABLE,
                text,
                null,
                currentSection,
                String.join(" / ", sectionPath),
                startOffset,
                startOffset + text.length(),
                "docx:table:" + blocks.size()
        ));
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && style.toLowerCase().startsWith("heading");
    }

    private int headingDepth(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null || style.isBlank()) {
            return 1;
        }
        for (int i = 0; i < style.length(); i++) {
            if (Character.isDigit(style.charAt(i))) {
                return Math.max(1, Math.min(6, style.charAt(i) - '0'));
            }
        }
        return 1;
    }

    private void updateSectionPath(List<String> sectionPath, int depth, String title) {
        while (sectionPath.size() >= depth) {
            sectionPath.remove(sectionPath.size() - 1);
        }
        sectionPath.add(title);
    }

    private String markdownHeading(int depth, String title) {
        return "#".repeat(Math.max(1, Math.min(6, depth))) + " " + title;
    }
}
