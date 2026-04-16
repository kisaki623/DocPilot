package com.docpilot.backend.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.document.vo.DocumentListItemResponse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

	@Select("""
			SELECT d.id AS documentId,
				   d.file_record_id AS fileRecordId,
				   f.file_name AS fileName,
				   f.file_ext AS fileType,
				   d.parse_status AS parseStatus,
				   d.summary AS summary,
				   d.create_time AS createTime,
				   d.update_time AS updateTime
			  FROM tb_document d
			  LEFT JOIN tb_file_record f ON d.file_record_id = f.id
			 WHERE d.user_id = #{userId}
			 ORDER BY d.create_time DESC
			 LIMIT #{offset}, #{pageSize}
			""")
	List<DocumentListItemResponse> selectUserDocumentPage(@Param("userId") Long userId,
														  @Param("offset") int offset,
														  @Param("pageSize") int pageSize);

	@Select("""
			SELECT COUNT(1)
			  FROM tb_document
			 WHERE user_id = #{userId}
			""")
	Long countUserDocuments(@Param("userId") Long userId);

	@Select("""
			SELECT id,
			       user_id,
			       file_record_id,
			       title,
			       summary,
			       content,
			       parse_status,
			       create_time,
			       update_time
			  FROM tb_document
			 WHERE user_id = #{userId}
			   AND file_record_id = #{fileRecordId}
			 ORDER BY id DESC
			 LIMIT 1
			""")
	Document selectLatestByUserAndFileRecordId(@Param("userId") Long userId,
										   @Param("fileRecordId") Long fileRecordId);

	@Select("""
			SELECT d.id AS documentId,
			       d.file_record_id AS fileRecordId,
			       d.title AS title,
			       f.file_name AS fileName,
			       f.file_ext AS fileType,
			       d.parse_status AS parseStatus,
			       d.summary AS summary,
			       d.content AS content,
			       d.create_time AS createTime,
			       d.update_time AS updateTime
			  FROM tb_document d
			  LEFT JOIN tb_file_record f ON d.file_record_id = f.id
			 WHERE d.id = #{documentId}
			   AND d.user_id = #{userId}
			 LIMIT 1
			""")
	DocumentDetailResponse selectUserDocumentDetail(@Param("documentId") Long documentId,
											@Param("userId") Long userId);
}

