package com.docpilot.backend.document.parser;

import com.docpilot.backend.file.storage.FileContentReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HtmlDocumentParser implements DocumentParser {

    private final FileContentReader fileContentReader;

    public HtmlDocumentParser(FileContentReader fileContentReader) {
        this.fileContentReader = fileContentReader;
    }

    @Override
    public String parserName() {
        return "jsoup-html";
    }

    @Override
    public String parserVersion() {
        return "1";
    }

    @Override
    public boolean supports(ParserInput input) {
        return input != null
                && ("html".equals(input.fileExt())
                || "htm".equals(input.fileExt())
                || input.contentType().contains("text/html"));
    }

    @Override
    public ParseResult parse(ParserInput input) {
        long start = System.nanoTime();
        try {
            String html = fileContentReader.readText(input.storagePath());
            Document document = Jsoup.parse(html, "");
            document.select("script,style,nav,footer,header,noscript,svg,canvas,iframe").remove();

            StringBuilder fullText = new StringBuilder();
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> sectionPath = new ArrayList<>();
            Elements elements = document.body() == null
                    ? new Elements()
                    : document.body().select("h1,h2,h3,h4,h5,h6,p,li,tr,a");
            for (Element element : elements) {
                if (shouldSkipLink(element)) {
                    continue;
                }
                String text = ParserTextUtils.normalize(element.text());
                if (text.isBlank()) {
                    continue;
                }
                BlockType blockType = blockType(element);
                if (blockType == BlockType.HEADING) {
                    updateSectionPath(sectionPath, headingDepth(element), text);
                }
                String fullTextBlock = blockType == BlockType.HEADING
                        ? markdownHeading(headingDepth(element), text)
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
                        startOffset + fullTextBlock.length(),
                        "html:" + element.normalName() + ":" + blocks.size()
                ));
            }
            return ParserTextUtils.result(
                    input,
                    fullText.toString(),
                    blocks,
                    Map.of("format", "html"),
                    List.of(),
                    this,
                    start,
                    0
            );
        } catch (ParserException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ParserException(ParseErrorCode.IO_ERROR, "html parser failed", ex);
        }
    }

    private boolean shouldSkipLink(Element element) {
        if (!"a".equals(element.normalName())) {
            return false;
        }
        Element parent = element.parent();
        return parent != null && ("p".equals(parent.normalName()) || "li".equals(parent.normalName()));
    }

    private BlockType blockType(Element element) {
        String tag = element.normalName();
        if (tag.matches("h[1-6]")) {
            return BlockType.HEADING;
        }
        if ("li".equals(tag)) {
            return BlockType.LIST;
        }
        if ("tr".equals(tag)) {
            return BlockType.TABLE;
        }
        if ("a".equals(tag)) {
            return BlockType.LINK;
        }
        return BlockType.PARAGRAPH;
    }

    private int headingDepth(Element element) {
        String tag = element.normalName();
        if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
            return Math.max(1, Math.min(6, tag.charAt(1) - '0'));
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
