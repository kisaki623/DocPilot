package com.docpilot.backend.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
              FROM tb_conversation_message
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
            """)
    int selectMaxSequenceNo(@Param("userId") Long userId,
                            @Param("conversationId") Long conversationId);

    @Select("""
            SELECT *
              FROM tb_conversation_message
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
             ORDER BY sequence_no DESC
             LIMIT #{limit}
            """)
    List<ConversationMessage> selectRecentActive(@Param("userId") Long userId,
                                                 @Param("conversationId") Long conversationId,
                                                 @Param("limit") int limit);

    @Select("""
            SELECT *
              FROM tb_conversation_message
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
             ORDER BY sequence_no ASC
             LIMIT #{limit}
            """)
    List<ConversationMessage> selectActiveByConversation(@Param("userId") Long userId,
                                                         @Param("conversationId") Long conversationId,
                                                         @Param("limit") int limit);
}
