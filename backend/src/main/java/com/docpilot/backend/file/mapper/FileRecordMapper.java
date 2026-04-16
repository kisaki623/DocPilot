package com.docpilot.backend.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.file.entity.FileRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileRecordMapper extends BaseMapper<FileRecord> {

		@Select("""
						SELECT id,
									 user_id,
									 file_name,
									 file_ext,
									 content_type,
									 file_size,
									 storage_path,
									 file_hash,
									 create_time,
									 update_time
							FROM tb_file_record
						 WHERE user_id = #{userId}
							 AND file_hash = #{fileHash}
							 AND file_size = #{fileSize}
						 ORDER BY id DESC
						 LIMIT 1
						""")
		FileRecord selectLatestByUserAndHashAndSize(@Param("userId") Long userId,
																								@Param("fileHash") String fileHash,
																								@Param("fileSize") Long fileSize);
}

