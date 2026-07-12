package com.docpilot.backend.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.conversation.entity.ConversationContextTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationContextTraceMapper extends BaseMapper<ConversationContextTrace> {

    @Select("""
            SELECT *
              FROM tb_context_trace
             WHERE user_id = #{userId}
               AND conversation_id = #{conversationId}
               AND message_id = #{messageId}
             LIMIT 1
            """)
    ConversationContextTrace selectByMessage(@Param("userId") Long userId,
                                             @Param("conversationId") Long conversationId,
                                             @Param("messageId") Long messageId);

    @Select("""
            <script>
            SELECT *
              FROM tb_context_trace
             WHERE user_id = #{userId}
               AND conversation_id = #{conversationId}
               AND message_id IN
               <foreach collection="messageIds" item="messageId" open="(" separator="," close=")">
                   #{messageId}
               </foreach>
            </script>
            """)
    List<ConversationContextTrace> selectByMessages(@Param("userId") Long userId,
                                                    @Param("conversationId") Long conversationId,
                                                    @Param("messageIds") List<Long> messageIds);
}
