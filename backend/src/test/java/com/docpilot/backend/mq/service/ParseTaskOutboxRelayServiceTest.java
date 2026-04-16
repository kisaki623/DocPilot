package com.docpilot.backend.mq.service;

import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import com.docpilot.backend.mq.mapper.ParseTaskOutboxMessageMapper;
import com.docpilot.backend.mq.producer.ParseTaskMessageProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseTaskOutboxRelayServiceTest {

    @Mock
    private ParseTaskOutboxMessageMapper outboxMessageMapper;

    @Mock
    private ParseTaskMessageProducer parseTaskMessageProducer;

    private ParseTaskOutboxRelayService buildService() {
        ParseTaskOutboxRelayService service = new ParseTaskOutboxRelayService(outboxMessageMapper, parseTaskMessageProducer);
        ReflectionTestUtils.setField(service, "maxRetryCount", 20);
        ReflectionTestUtils.setField(service, "retryBackoffSeconds", 30);
        ReflectionTestUtils.setField(service, "scanBatchSize", 20);
        return service;
    }

    @Test
    void shouldMarkOutboxFailedWhenSendThrows() {
        ParseTaskOutboxRelayService service = buildService();

        ParseTaskOutboxMessage outbox = new ParseTaskOutboxMessage();
        outbox.setId(1L);
        outbox.setMessageKey("parse-task:1:create:demo");
        outbox.setTaskId(11L);
        outbox.setDocumentId(22L);
        outbox.setFileRecordId(33L);
        outbox.setStatus("PENDING");
        when(outboxMessageMapper.selectById(1L)).thenReturn(outbox);
        doThrow(new RuntimeException("mq down")).when(parseTaskMessageProducer).sendParseTaskCreated(any());

        boolean result = service.dispatchByOutboxId(1L);

        assertTrue(!result);
        verify(outboxMessageMapper).markFailed(eq(1L), any(LocalDateTime.class), eq("mq down"));
    }

    @Test
    void shouldDispatchDueMessagesByScan() {
        ParseTaskOutboxRelayService service = buildService();

        ParseTaskOutboxMessage pending = new ParseTaskOutboxMessage();
        pending.setId(2L);
        pending.setMessageKey("parse-task:2:create:demo");
        pending.setTaskId(12L);
        pending.setDocumentId(23L);
        pending.setFileRecordId(34L);
        pending.setStatus("FAILED");
        when(outboxMessageMapper.selectDispatchable(any(LocalDateTime.class), eq(20), eq(20))).thenReturn(List.of(pending));
        when(outboxMessageMapper.selectById(2L)).thenReturn(pending);

        int successCount = service.dispatchDueMessages();

        assertEquals(1, successCount);
        verify(parseTaskMessageProducer).sendParseTaskCreated(any());
        verify(outboxMessageMapper).markSent(eq(2L), any(LocalDateTime.class));
    }

    @Test
    void shouldAppendPendingOutboxRecord() {
        ParseTaskOutboxRelayService service = buildService();

        doAnswer(invocation -> {
            ParseTaskOutboxMessage outbox = invocation.getArgument(0);
            outbox.setId(99L);
            return 1;
        }).when(outboxMessageMapper).insert(any(ParseTaskOutboxMessage.class));

        Long outboxId = service.appendPending(9L, 8L, 7L, "create");

        assertEquals(99L, outboxId);
        ArgumentCaptor<ParseTaskOutboxMessage> captor = ArgumentCaptor.forClass(ParseTaskOutboxMessage.class);
        verify(outboxMessageMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertTrue(captor.getValue().getMessageKey().startsWith("parse-task:9:create:"));
    }
}

