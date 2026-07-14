package com.docpilot.backend.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.knowledge.entity.KnowledgeBaseDocument;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeBaseDocumentMapper extends BaseMapper<KnowledgeBaseDocument> {

    @Select("""
            SELECT id,
                   knowledge_base_id,
                   user_id,
                   document_id,
                   status,
                   create_time,
                   update_time
              FROM tb_knowledge_base_document
             WHERE knowledge_base_id = #{knowledgeBaseId}
               AND document_id = #{documentId}
             LIMIT 1
            """)
    KnowledgeBaseDocument selectByKnowledgeBaseIdAndDocumentId(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                                               @Param("documentId") Long documentId);

    @Select("""
            SELECT kbd.id AS id,
                   kbd.knowledge_base_id AS knowledgeBaseId,
                   kbd.document_id AS documentId,
                   d.title AS documentTitle,
                   d.parse_status AS parseStatus,
                   kbd.status AS status,
                   kbd.create_time AS createTime,
                   kbd.update_time AS updateTime
              FROM tb_knowledge_base_document kbd
             JOIN tb_document d ON kbd.document_id = d.id
             WHERE kbd.user_id = #{userId}
               AND kbd.knowledge_base_id = #{knowledgeBaseId}
               AND kbd.status = 'ACTIVE'
               AND d.status = 'ACTIVE'
             ORDER BY kbd.create_time DESC, kbd.id DESC
            """)
    List<KnowledgeBaseDocumentResponse> selectActiveDocumentResponses(@Param("userId") Long userId,
                                                                      @Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            SELECT COUNT(1)
              FROM tb_knowledge_base_document
             WHERE user_id = #{userId}
               AND knowledge_base_id = #{knowledgeBaseId}
               AND status = 'ACTIVE'
            """)
    Integer countActiveDocuments(@Param("userId") Long userId,
                                 @Param("knowledgeBaseId") Long knowledgeBaseId);

    @Update("""
            UPDATE tb_knowledge_base_document
               SET status = #{status},
                   update_time = CURRENT_TIMESTAMP
             WHERE knowledge_base_id = #{knowledgeBaseId}
               AND document_id = #{documentId}
            """)
    int updateStatus(@Param("knowledgeBaseId") Long knowledgeBaseId,
                     @Param("documentId") Long documentId,
                     @Param("status") String status);

    @Update("""
            UPDATE tb_knowledge_base_document
               SET status = #{status},
                   update_time = CURRENT_TIMESTAMP
             WHERE user_id = #{userId}
               AND document_id = #{documentId}
               AND status = 'ACTIVE'
            """)
    int updateActiveStatusByUserAndDocumentId(@Param("userId") Long userId,
                                              @Param("documentId") Long documentId,
                                              @Param("status") String status);
}
