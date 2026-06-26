package com.docpilot.backend.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.conversation.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("""
            SELECT *
              FROM tb_conversation
             WHERE id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
            """)
    Conversation selectActiveByIdAndUserId(@Param("userId") Long userId,
                                           @Param("conversationId") Long conversationId);

    @Select("""
            SELECT *
              FROM tb_conversation
             WHERE id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
             FOR UPDATE
            """)
    Conversation selectActiveForUpdate(@Param("userId") Long userId,
                                       @Param("conversationId") Long conversationId);

    @Select("""
            SELECT *
              FROM tb_conversation
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
             ORDER BY COALESCE(last_message_time, create_time) DESC, id DESC
             LIMIT #{limit}
            """)
    List<Conversation> selectActiveByUserId(@Param("userId") Long userId,
                                            @Param("limit") int limit);

    @Update("""
            UPDATE tb_conversation
               SET last_message_time = #{lastMessageTime}
             WHERE id = #{conversationId}
               AND user_id = #{userId}
            """)
    int updateLastMessageTime(@Param("userId") Long userId,
                              @Param("conversationId") Long conversationId,
                              @Param("lastMessageTime") LocalDateTime lastMessageTime);

    @Update("""
            UPDATE tb_conversation
               SET bound_knowledge_base_id = #{knowledgeBaseId}
             WHERE id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
            """)
    int updateBoundKnowledgeBase(@Param("userId") Long userId,
                                 @Param("conversationId") Long conversationId,
                                 @Param("knowledgeBaseId") Long knowledgeBaseId);
}
