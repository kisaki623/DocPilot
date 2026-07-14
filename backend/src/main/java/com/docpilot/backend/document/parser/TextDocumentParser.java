package com.docpilot.backend.document.parser;

import com.docpilot.backend.file.storage.FileContentReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TextDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md");

    private final FileContentReader fileContentReader;

    public TextDocumentParser(FileContentReader fileContentReader) {
        this.fileContentReader = fileContentReader;
    }

    @Override
    public String parserName() {
        return "text";
    }

    @Override
    public String parserVersion() {
        return "1";
    }

    @Override
    public boolean supports(ParserInput input) {
        return input != null && SUPPORTED_EXTENSIONS.contains(input.fileExt());
    }

    @Override
    public ParseResult parse(ParserInput input) {
        long start = System.nanoTime();
        try {
            String fullText = fileContentReader.readText(input.storagePath());
            return ParserTextUtils.result(
                    input,
                    fullText,
                    ParserTextUtils.paragraphBlocks(fullText),
                    Map.of("format", input.fileExt().isBlank() ? "text" : input.fileExt()),
                    List.of(),
                    this,
                    start,
                    0
            );
        } catch (ParserException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ParserException(ParseErrorCode.IO_ERROR, "read text document failed", ex);
        }
    }
}
