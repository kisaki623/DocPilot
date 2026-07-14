package com.docpilot.backend.task.service;

import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import com.docpilot.backend.mq.mapper.ParseTaskOutboxMessageMapper;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseTaskRecoveryServiceTest {

    @Mock
    private ParseTaskMapper parseTaskMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ParseTaskOutboxMessageMapper outboxMessageMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private ParseTaskRecoveryService buildService() {
        return new ParseTaskRecoveryService(
                parseTaskMapper,
                documentMapper,
                outboxMessageMapper,
                stringRedisTemplate,
                30L,
                20,
                3
        );
    }

    @Test
    void shouldFailClosedStaleProcessingTask() {
        ParseTaskRecoveryService service = buildService();
        ParseTask staleTask = task(11L, 100L, 22L, "INDEXING");
        when(parseTaskMapper.selectStaleProcessingTasks(any(LocalDateTime.class), eq(20))).thenReturn(List.of(staleTask));
        when(parseTaskMapper.markStaleTaskFailed(eq(11L), any(String.class), any(LocalDateTime.class))).thenReturn(1);

        int recovered = service.recoverStaleProcessingTasks();

        assertThat(recovered).isEqualTo(1);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(parseTaskMapper).markStaleTaskFailed(eq(11L), errorCaptor.capture(), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue())
                .contains("PARSE_TASK_STALE_TIMEOUT")
                .contains("[stage=INDEXING]");
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getId()).isEqualTo(22L);
        assertThat(documentCaptor.getValue().getParseStatus()).isEqualTo("FAILED");
        verify(stringRedisTemplate).delete("docpilot:document:detail:u:100:d:22");
    }

    @Test
    void shouldSkipTerminalTaskWhenOutboxIsExhausted() {
        ParseTaskRecoveryService service = buildService();
        ParseTaskOutboxMessage message = new ParseTaskOutboxMessage();
        message.setTaskId(11L);
        when(outboxMessageMapper.selectExhaustedFailed(3, 20)).thenReturn(List.of(message));
        when(parseTaskMapper.selectById(11L)).thenReturn(task(11L, 100L, 22L, "SUCCESS"));

        int recovered = service.recoverExhaustedOutboxTasks();

        assertThat(recovered).isZero();
        verify(parseTaskMapper, never()).markStaleTaskFailed(any(), any(), any());
        verify(documentMapper, never()).updateById(any(Document.class));
    }

    @Test
    void shouldFailClosedTaskWhenOutboxRetriesExhausted() {
        ParseTaskRecoveryService service = buildService();
        ParseTaskOutboxMessage message = new ParseTaskOutboxMessage();
        message.setTaskId(11L);
        when(outboxMessageMapper.selectExhaustedFailed(3, 20)).thenReturn(List.of(message));
        when(parseTaskMapper.selectById(11L)).thenReturn(task(11L, 100L, 22L, "PENDING"));
        when(parseTaskMapper.markStaleTaskFailed(eq(11L), any(String.class), any(LocalDateTime.class))).thenReturn(1);

        int recovered = service.recoverExhaustedOutboxTasks();

        assertThat(recovered).isEqualTo(1);
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(parseTaskMapper).markStaleTaskFailed(eq(11L), errorCaptor.capture(), any(LocalDateTime.class));
        assertThat(errorCaptor.getValue())
                .contains("PARSE_OUTBOX_EXHAUSTED")
                .contains("[stage=PENDING]");
    }

    private ParseTask task(Long id, Long userId, Long documentId, String status) {
        ParseTask task = new ParseTask();
        task.setId(id);
        task.setUserId(userId);
        task.setDocumentId(documentId);
        task.setFileRecordId(33L);
        task.setStatus(status);
        return task;
    }
}
