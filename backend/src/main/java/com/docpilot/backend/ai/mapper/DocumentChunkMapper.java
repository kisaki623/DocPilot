package com.docpilot.backend.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    @Select("""
            SELECT id,
                   document_id,
                   user_id,
                   chunk_index,
                   content,
                   content_hash,
                   start_offset,
                   end_offset,
                   token_count,
                   index_status,
                   index_version,
                   embedding_model,
                   vector_id,
                   create_time,
                   update_time
              FROM tb_document_chunk
             WHERE document_id = #{documentId}
             ORDER BY index_version ASC, chunk_index ASC, id ASC
            """)
    List<DocumentChunkEntity> selectByDocumentId(@Param("documentId") Long documentId);

    @Select("""
            SELECT id,
                   document_id,
                   user_id,
                   chunk_index,
                   content,
                   content_hash,
                   start_offset,
                   end_offset,
                   token_count,
                   index_status,
                   index_version,
                   embedding_model,
                   vector_id,
                   create_time,
                   update_time
              FROM tb_document_chunk
             WHERE document_id = #{documentId}
               AND index_version = #{indexVersion}
             ORDER BY chunk_index ASC, id ASC
            """)
    List<DocumentChunkEntity> selectByDocumentIdAndVersion(@Param("documentId") Long documentId,
                                                           @Param("indexVersion") Integer indexVersion);

    @Delete("""
            DELETE FROM tb_document_chunk
             WHERE document_id = #{documentId}
               AND index_version = #{indexVersion}
            """)
    int deleteByDocumentIdAndVersion(@Param("documentId") Long documentId,
                                     @Param("indexVersion") Integer indexVersion);
}
