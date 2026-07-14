package com.docpilot.backend.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.conversation.entity.ConversationSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationSummaryMapper extends BaseMapper<ConversationSummary> {

    @Select("""
            SELECT *
              FROM tb_conversation_summary
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
               AND status = 'ACTIVE'
             ORDER BY summary_version DESC, id DESC
             LIMIT 1
            """)
    ConversationSummary selectActiveSummary(@Param("userId") Long userId,
                                            @Param("conversationId") Long conversationId);

    @Update("""
            UPDATE tb_conversation_summary
               SET status = 'DELETED'
             WHERE conversation_id = #{conversationId}
               AND user_id = #{userId}
               AND status <> 'DELETED'
            """)
    int softDeleteByConversation(@Param("userId") Long userId,
                                 @Param("conversationId") Long conversationId);
}
