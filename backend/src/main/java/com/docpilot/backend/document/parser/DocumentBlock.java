package com.docpilot.backend.document.parser;

public record DocumentBlock(
        int blockIndex,
        BlockType blockType,
        String text,
        Integer pageNumber,
        String sectionTitle,
        String sectionPath,
        int startOffset,
        int endOffset,
        String sourceLocator
) {

    public DocumentBlock {
        if (blockIndex < 0) {
            throw new IllegalArgumentException("blockIndex must be non-negative");
        }
        if (blockType == null) {
            blockType = BlockType.PARAGRAPH;
        }
        text = text == null ? "" : text.trim();
        sectionTitle = sectionTitle == null ? "" : sectionTitle.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        sourceLocator = sourceLocator == null ? "" : sourceLocator.trim();
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("offsets must be non-negative and ordered");
        }
    }
}
