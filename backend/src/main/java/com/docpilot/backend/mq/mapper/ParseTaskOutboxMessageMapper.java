package com.docpilot.backend.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ParseTaskOutboxMessageMapper extends BaseMapper<ParseTaskOutboxMessage> {

    @Select("""
            SELECT id,
                   message_key,
                   task_id,
                   document_id,
                   file_record_id,
                   status,
                   retry_count,
                   next_retry_time,
                   last_error,
                   sent_time,
                   create_time,
                   update_time
              FROM tb_parse_task_outbox
             WHERE status IN ('PENDING', 'FAILED')
               AND retry_count < #{maxRetryCount}
               AND next_retry_time <= #{now}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<ParseTaskOutboxMessage> selectDispatchable(@Param("now") LocalDateTime now,
                                                    @Param("limit") Integer limit,
                                                    @Param("maxRetryCount") Integer maxRetryCount);

    @Update("""
            UPDATE tb_parse_task_outbox
               SET status = 'SENT',
                   sent_time = #{sentTime},
                   last_error = NULL
             WHERE id = #{id}
               AND status <> 'SENT'
            """)
    int markSent(@Param("id") Long id,
                 @Param("sentTime") LocalDateTime sentTime);

    @Update("""
            UPDATE tb_parse_task_outbox
               SET status = 'FAILED',
                   retry_count = retry_count + 1,
                   next_retry_time = #{nextRetryTime},
                   last_error = #{lastError}
             WHERE id = #{id}
               AND status <> 'SENT'
            """)
    int markFailed(@Param("id") Long id,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("lastError") String lastError);

    @Select("""
            SELECT id,
                   message_key,
                   task_id,
                   document_id,
                   file_record_id,
                   status,
                   retry_count,
                   next_retry_time,
                   last_error,
                   sent_time,
                   create_time,
                   update_time
              FROM tb_parse_task_outbox
             WHERE task_id = #{taskId}
             ORDER BY id DESC
             LIMIT 1
            """)
    ParseTaskOutboxMessage selectLatestByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT id,
                   message_key,
                   task_id,
                   document_id,
                   file_record_id,
                   status,
                   retry_count,
                   next_retry_time,
                   last_error,
                   sent_time,
                   create_time,
                   update_time
              FROM tb_parse_task_outbox
             WHERE status = 'FAILED'
               AND retry_count >= #{maxRetryCount}
             ORDER BY update_time ASC
             LIMIT #{limit}
            """)
    List<ParseTaskOutboxMessage> selectExhaustedFailed(@Param("maxRetryCount") Integer maxRetryCount,
                                                       @Param("limit") Integer limit);
}

