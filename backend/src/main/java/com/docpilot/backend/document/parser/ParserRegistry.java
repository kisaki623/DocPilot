package com.docpilot.backend.document.parser;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ParserRegistry {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final List<DocumentParser> parsers;

    public ParserRegistry(List<DocumentParser> parsers) {
        this.parsers = parsers == null
                ? List.of()
                : parsers.stream()
                        .sorted(Comparator.comparing(DocumentParser::parserName))
                        .toList();
    }

    public DocumentParser select(ParserInput input) {
        if (input == null) {
            throw new ParserException(ParseErrorCode.UNSUPPORTED_TYPE, "parser input is required");
        }
        if (input.fileSize() != null && input.fileSize() > input.options().maxFileSizeBytes()) {
            throw new ParserException(ParseErrorCode.FILE_TOO_LARGE, "file size exceeds parser limit");
        }
        return parsers.stream()
                .filter(parser -> parser.supports(input))
                .findFirst()
                .orElseThrow(() -> new ParserException(
                        ParseErrorCode.UNSUPPORTED_TYPE,
                        "unsupported document type: " + safeType(input)
                ));
    }

    public ParseResult parse(ParserInput input) {
        DocumentParser parser = select(input);
        ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory(parser.parserName()));
        Future<ParseResult> future = executor.submit(new ParserCallable(parser, input));
        try {
            return future.get(input.options().timeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ParserException(ParseErrorCode.PARSE_TIMEOUT, "document parser timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ParserException(ParseErrorCode.PARSE_TIMEOUT, "document parser interrupted", ex);
        } catch (ParserException ex) {
            throw ex;
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof ParserException parserException) {
                throw parserException;
            }
            throw new ParserException(ParseErrorCode.IO_ERROR, "document parser failed: " + cause.getClass().getSimpleName(), cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private ThreadFactory threadFactory(String parserName) {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "docpilot-parser-" + parserName + "-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private String safeType(ParserInput input) {
        if (!input.fileExt().isBlank()) {
            return input.fileExt();
        }
        if (!input.contentType().isBlank()) {
            return input.contentType();
        }
        return "unknown";
    }

    private record ParserCallable(DocumentParser parser, ParserInput input) implements Callable<ParseResult> {

        @Override
        public ParseResult call() {
            return parser.parse(input);
        }
    }
}
