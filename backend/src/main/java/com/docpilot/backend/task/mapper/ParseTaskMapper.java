package com.docpilot.backend.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.task.entity.ParseTask;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ParseTaskMapper extends BaseMapper<ParseTask> {

		@Select("""
						SELECT id,
									 user_id,
									 document_id,
									 file_record_id,
									 status,
									 error_msg,
									 retry_count,
									 start_time,
									 finish_time,
									 create_time,
									 update_time
							FROM tb_parse_task
						 WHERE user_id = #{userId}
							 AND document_id = #{documentId}
						 ORDER BY id DESC
						 LIMIT 1
						""")
		ParseTask selectLatestByUserAndDocumentId(@Param("userId") Long userId,
																							@Param("documentId") Long documentId);

		@Update("""
				UPDATE tb_parse_task
				   SET status = 'PENDING',
				       error_msg = NULL,
				       start_time = NULL,
				       finish_time = NULL,
				       retry_count = #{retryCount}
				 WHERE id = #{taskId}
				   AND user_id = #{userId}
				   AND status = 'FAILED'
				""")
		int resetFailedTaskForRetry(@Param("taskId") Long taskId,
												 @Param("userId") Long userId,
												 @Param("retryCount") Integer retryCount);

		@Update("""
				UPDATE tb_parse_task
				   SET status = 'PENDING',
				       error_msg = NULL,
				       start_time = NULL,
				       finish_time = NULL
				 WHERE id = #{taskId}
				   AND user_id = #{userId}
				   AND status IN ('SUCCESS', 'FAILED')
				""")
		int resetTerminalTaskForReparse(@Param("taskId") Long taskId,
													 @Param("userId") Long userId);
}

