package com.docpilot.backend.ai.context.builder;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.conversation.constant.ConversationMessageStatus;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RecentTurnsContextBuilder {

    private final ConversationMessageMapper conversationMessageMapper;
    private final TokenEstimator tokenEstimator;

    public RecentTurnsContextBuilder(ConversationMessageMapper conversationMessageMapper,
                                     TokenEstimator tokenEstimator) {
        this.conversationMessageMapper = conversationMessageMapper;
        this.tokenEstimator = tokenEstimator;
    }

    public List<ContextItem> build(Long userId, Long conversationId, int maxRounds) {
        int messageLimit = Math.max(0, maxRounds) * 2;
        if (messageLimit <= 0) {
            return List.of();
        }
        List<ConversationMessage> recentDesc = conversationMessageMapper.selectRecentActive(userId, conversationId, messageLimit);
        List<ConversationMessage> recentAsc = new ArrayList<>(recentDesc);
        recentAsc.sort(Comparator.comparingInt(message -> message.getSequenceNo() == null ? 0 : message.getSequenceNo()));
        List<ContextItem> items = new ArrayList<>();
        for (ConversationMessage message : recentAsc) {
            String content = message.getRole() + ": " + message.getContent();
            items.add(new ContextItem(
                    ContextType.RECENT_TURN,
                    content,
                    700 + Math.max(0, message.getSequenceNo() == null ? 0 : message.getSequenceNo()),
                    tokenEstimator.estimate(content),
                    false,
                    message.getUserId(),
                    String.valueOf(message.getId()),
                    ConversationMessageStatus.ACTIVE,
                    Map.of("role", message.getRole(), "sequenceNo", message.getSequenceNo())
            ));
        }
        return List.copyOf(items);
    }
}
