package com.docpilot.backend.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SummaryUtilsTest {

    @Test
    void shouldRemoveBasicMarkdownMarkersInSummary() {
        String content = "# 标题\n\n> 引导语\n\n- 第一条\n- 第二条\n\n**正文说明**";

        String summary = SummaryUtils.buildSummary(content, 200);

        Assertions.assertFalse(summary.contains("#"));
        Assertions.assertFalse(summary.contains(">"));
        Assertions.assertFalse(summary.contains("**"));
        Assertions.assertTrue(summary.contains("标题"));
        Assertions.assertTrue(summary.contains("正文说明"));
    }

    @Test
    void shouldPreferWholeSentenceWhenTruncating() {
        String content = "第一句讲项目定位。第二句讲工程边界。第三句讲后续规划。";

        String summary = SummaryUtils.buildSummary(content, 9);

        Assertions.assertEquals("第一句讲项目定位。", summary);
    }

    @Test
    void shouldFallbackToPrefixWhenNoSentenceDelimiter() {
        String content = "abcdefghijklmnopqrstuvwxyz";

        String summary = SummaryUtils.buildSummary(content, 10);

        Assertions.assertEquals("abcdefghij", summary);
    }
}


