package com.docpilot.backend.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.mq.entity.ParseTaskConsumeRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ParseTaskConsumeRecordMapper extends BaseMapper<ParseTaskConsumeRecord> {

    @Insert("""
            INSERT INTO tb_parse_task_consume_record (message_key, task_id, status, consume_count)
            VALUES (#{messageKey}, #{taskId}, 'PROCESSING', 1)
            """)
    int insertProcessing(@Param("messageKey") String messageKey,
                         @Param("taskId") Long taskId);

    @Select("""
            SELECT id,
                   message_key,
                   task_id,
                   status,
                   consume_count,
                   last_error,
                   create_time,
                   update_time
              FROM tb_parse_task_consume_record
             WHERE message_key = #{messageKey}
             LIMIT 1
            """)
    ParseTaskConsumeRecord selectByMessageKey(@Param("messageKey") String messageKey);

    @Update("""
            UPDATE tb_parse_task_consume_record
               SET status = 'PROCESSING',
                   consume_count = consume_count + 1,
                   last_error = NULL
             WHERE message_key = #{messageKey}
               AND status = 'FAILED'
            """)
    int takeoverFailed(@Param("messageKey") String messageKey);

    @Update("""
            UPDATE tb_parse_task_consume_record
               SET status = 'SUCCESS',
                   last_error = NULL
             WHERE message_key = #{messageKey}
            """)
    int markSuccess(@Param("messageKey") String messageKey);

    @Update("""
            UPDATE tb_parse_task_consume_record
               SET status = 'FAILED',
                   last_error = #{lastError}
             WHERE message_key = #{messageKey}
            """)
    int markFailed(@Param("messageKey") String messageKey,
                   @Param("lastError") String lastError);
}

