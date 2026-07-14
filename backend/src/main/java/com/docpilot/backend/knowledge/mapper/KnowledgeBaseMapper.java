package com.docpilot.backend.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("""
            SELECT id,
                   user_id,
                   name,
                   description,
                   status,
                   create_time,
                   update_time
              FROM tb_knowledge_base
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
             ORDER BY create_time DESC, id DESC
            """)
    List<KnowledgeBase> selectActiveByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id,
                   name,
                   description,
                   status,
                   create_time,
                   update_time
              FROM tb_knowledge_base
             WHERE id = #{knowledgeBaseId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
             LIMIT 1
            """)
    KnowledgeBase selectActiveByIdAndUserId(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                            @Param("userId") Long userId);
}
