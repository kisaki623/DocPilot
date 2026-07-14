package com.docpilot.backend.document.parser;

import com.docpilot.backend.file.storage.FileContentReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PdfDocumentParser implements DocumentParser {

    private final FileContentReader fileContentReader;

    public PdfDocumentParser(FileContentReader fileContentReader) {
        this.fileContentReader = fileContentReader;
    }

    @Override
    public String parserName() {
        return "pdfbox";
    }

    @Override
    public String parserVersion() {
        return "1";
    }

    @Override
    public boolean supports(ParserInput input) {
        return input != null
                && ("pdf".equals(input.fileExt()) || input.contentType().contains("application/pdf"));
    }

    @Override
    public ParseResult parse(ParserInput input) {
        long start = System.nanoTime();
        byte[] bytes = fileContentReader.readBytes(input.storagePath(), input.options().maxFileSizeBytes());
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();
            List<DocumentBlock> blocks = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = ParserTextUtils.normalize(stripper.getText(document));
                if (pageText.isBlank()) {
                    warnings.add("empty_page:" + page);
                    continue;
                }
                int startOffset = ParserTextUtils.appendText(fullText, pageText);
                blocks.add(new DocumentBlock(
                        blocks.size(),
                        BlockType.PAGE,
                        pageText,
                        page,
                        "",
                        "",
                        startOffset,
                        startOffset + pageText.length(),
                        "page:" + page
                ));
            }
            return ParserTextUtils.result(
                    input,
                    fullText.toString(),
                    blocks,
                    Map.of("format", "pdf", "pageCount", String.valueOf(pageCount)),
                    warnings,
                    this,
                    start,
                    pageCount
            );
        } catch (ParserException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ParserException(ParseErrorCode.CORRUPTED_FILE, "pdf parser failed to read document", ex);
        } catch (RuntimeException ex) {
            throw new ParserException(ParseErrorCode.IO_ERROR, "pdf parser failed", ex);
        }
    }
}
