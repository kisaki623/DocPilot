package com.docpilot.backend.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.ContextTraceTechnicalDetails;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.conversation.entity.ConversationContextTrace;
import com.docpilot.backend.conversation.mapper.ConversationContextTraceMapper;
import com.docpilot.backend.conversation.service.ConversationContextTraceService;
import com.docpilot.backend.conversation.service.ConversationService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationContextTraceServiceImpl implements ConversationContextTraceService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<Long, Integer>> DOCUMENT_HIT_COUNTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<KnowledgeBaseRagEvidenceCitation>> CITATIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ContextTraceTechnicalDetails> TECHNICAL_DETAILS_TYPE = new TypeReference<>() {
    };

    private final ConversationContextTraceMapper traceMapper;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public ConversationContextTraceServiceImpl(ConversationContextTraceMapper traceMapper,
                                               ConversationService conversationService,
                                               ObjectMapper objectMapper) {
        this.traceMapper = traceMapper;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Long userId, ContextTrace trace) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(trace, "trace");
        ValidationUtils.requireNonNull(trace.conversationId(), "conversationId");
        ValidationUtils.requireNonNull(trace.messageId(), "messageId");

        ConversationContextTrace entity = toEntity(userId, trace);
        if (traceMapper.insert(entity) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to save context trace");
        }
    }

    @Override
    public ContextTrace getByMessage(Long userId, Long conversationId, Long messageId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        ValidationUtils.requireNonNull(messageId, "messageId");
        conversationService.requireOwnedActive(userId, conversationId);

        ConversationContextTrace entity = traceMapper.selectByMessage(userId, conversationId, messageId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.CONTEXT_TRACE_NOT_FOUND);
        }
        return toTrace(entity);
    }

    @Override
    public Map<Long, ContextTrace> listByMessages(Long userId, Long conversationId, List<Long> messageIds) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(conversationId, "conversationId");
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        conversationService.requireOwnedActive(userId, conversationId);

        List<Long> distinctMessageIds = messageIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (distinctMessageIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ContextTrace> traces = new LinkedHashMap<>();
        for (ConversationContextTrace entity : traceMapper.selectByMessages(userId, conversationId, distinctMessageIds)) {
            traces.put(entity.getMessageId(), toTrace(entity));
        }
        return traces;
    }

    private ConversationContextTrace toEntity(Long userId, ContextTrace trace) {
        ConversationContextTrace entity = new ConversationContextTrace();
        entity.setConversationId(trace.conversationId());
        entity.setMessageId(trace.messageId());
        entity.setUserId(userId);
        entity.setContextMode(trace.contextMode());
        entity.setGroundingPolicy(trace.groundingPolicy());
        entity.setRouteDecision(trace.routeDecision());
        entity.setLlmCalled(trace.llmCalled());
        entity.setSummaryUsed(trace.summaryUsed());
        entity.setRecentTurnCount(trace.recentTurnCount());
        entity.setRecentMessageCount(trace.recentMessageCount());
        entity.setMemoryUsed(trace.memoryUsed());
        entity.setMemoryCount(trace.memoryCount());
        entity.setMemoryTypesJson(writeJson(trace.memoryTypes()));
        entity.setRagTriggered(trace.ragTriggered());
        entity.setRagRequired(trace.ragRequired());
        entity.setKnowledgeBaseId(trace.knowledgeBaseId());
        entity.setEvidenceCount(trace.evidenceCount());
        entity.setNoEvidence(trace.noEvidence());
        entity.setDocumentHitCountsJson(writeJson(trace.documentHitCounts()));
        entity.setCitationsJson(writeJson(trace.citations()));
        entity.setTechnicalDetailsJson(writeJson(trace.technicalDetails()));
        entity.setMaxPromptTokens(trace.maxPromptTokens());
        entity.setEstimatedPromptTokens(trace.estimatedPromptTokens());
        entity.setTruncated(trace.truncated());
        entity.setTruncatedTypesJson(writeJson(trace.truncatedTypes()));
        entity.setFallbackUsed(trace.fallbackUsed());
        entity.setFallbackReason(trace.fallbackReason());
        entity.setModelCallSkipped(trace.modelCallSkipped());
        return entity;
    }

    private ContextTrace toTrace(ConversationContextTrace entity) {
        return new ContextTrace(
                entity.getConversationId(),
                entity.getMessageId(),
                entity.getContextMode(),
                entity.getGroundingPolicy(),
                entity.getRouteDecision(),
                entity.getLlmCalled(),
                Boolean.TRUE.equals(entity.getSummaryUsed()),
                defaultInt(entity.getRecentTurnCount()),
                defaultInt(entity.getRecentMessageCount()),
                Boolean.TRUE.equals(entity.getMemoryUsed()),
                defaultInt(entity.getMemoryCount()),
                readJson(entity.getMemoryTypesJson(), STRING_LIST_TYPE, List.of()),
                Boolean.TRUE.equals(entity.getRagTriggered()),
                Boolean.TRUE.equals(entity.getRagRequired()),
                entity.getKnowledgeBaseId(),
                defaultInt(entity.getEvidenceCount()),
                Boolean.TRUE.equals(entity.getNoEvidence()),
                readJson(entity.getDocumentHitCountsJson(), DOCUMENT_HIT_COUNTS_TYPE, Map.of()),
                defaultInt(entity.getMaxPromptTokens()),
                defaultInt(entity.getEstimatedPromptTokens()),
                Boolean.TRUE.equals(entity.getTruncated()),
                readJson(entity.getTruncatedTypesJson(), STRING_LIST_TYPE, List.of()),
                Boolean.TRUE.equals(entity.getFallbackUsed()),
                entity.getFallbackReason(),
                Boolean.TRUE.equals(entity.getModelCallSkipped()),
                readJson(entity.getTechnicalDetailsJson(), TECHNICAL_DETAILS_TYPE, null),
                readJson(entity.getCitationsJson(), CITATIONS_TYPE, List.of())
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.CONTEXT_ASSEMBLY_FAILED, "context trace serialization failed");
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
