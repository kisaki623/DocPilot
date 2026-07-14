import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";
import type { KnowledgeBaseCitationItem } from "@/lib/knowledge-base-api";

export type ConversationContextMode = "RECENT_TURNS" | "AGENT_MEMORY";
export type GroundingPolicy = "MODEL_ONLY" | "AUTO_RAG" | "STRICT_KB";

export interface ContextTraceTechnicalDetails {
  available?: boolean;
  traceId?: string;
  messageId?: number | null;
  route?: {
    groundingPolicy?: string | null;
    routeDecision?: string | null;
    routeReason?: string | null;
    ragTriggered?: boolean;
    ragRequired?: boolean;
    noEvidence?: boolean;
    llmCalled?: boolean | null;
    modelSkipped?: boolean;
  };
  timingsMs?: Record<string, number>;
  retrieval?: {
    retrievalMode?: string | null;
    provider?: string | null;
    topK?: number | null;
    evidenceCount?: number;
    documentHitCounts?: Record<string, number>;
    rerankApplied?: boolean;
    rerankModel?: string | null;
    rerankFailureReason?: string | null;
    multiQueryApplied?: boolean;
    queryVariantCount?: number;
    queryDedupeCount?: number;
    evidenceGate?: {
      status?: string;
      reason?: string | null;
    };
    scoreRows?: Array<{
      citationIndex?: number | null;
      documentId?: number | null;
      documentTitle?: string | null;
      chunkId?: number | null;
      chunkIndex?: number | null;
      locator?: string | null;
      vectorScore?: number | null;
      keywordScore?: number | null;
      fusedScore?: number | null;
      rerankScore?: number | null;
      finalScore?: number | null;
      selectedAsCitation?: boolean;
    }>;
  };
  tokenBudget?: {
    maxPromptTokens?: number;
    estimatedPromptTokens?: number;
    truncated?: boolean;
    byType?: Array<{
      type: string;
      usedCount: number;
      usedTokens: number;
      droppedCount: number;
      droppedTokens: number;
    }>;
    droppedReasons?: Array<{
      type: string;
      count: number;
      reason: string;
    }>;
  };
  contextUsage?: {
    summary?: { used?: boolean };
    memory?: { used?: boolean; count?: number; types?: string[] };
    recent?: { turnCount?: number; messageCount?: number };
  };
  fallback?: {
    used?: boolean;
    reason?: string | null;
    safeError?: string | null;
  };
}

export interface ContextTraceData {
  conversationId: number;
  messageId?: number | null;
  contextMode: string;
  groundingPolicy?: string | null;
  routeDecision?: string | null;
  llmCalled?: boolean | null;
  summaryUsed: boolean;
  recentTurnCount: number;
  recentMessageCount: number;
  memoryUsed: boolean;
  memoryCount: number;
  memoryTypes: string[];
  ragTriggered: boolean;
  ragRequired: boolean;
  knowledgeBaseId?: number | null;
  evidenceCount: number;
  noEvidence: boolean;
  documentHitCounts: Record<string, number>;
  contextSourceCounts?: Record<string, number>;
  contextSourceFlags?: Record<string, boolean>;
  maxPromptTokens: number;
  estimatedPromptTokens: number;
  truncated: boolean;
  truncatedTypes: string[];
  fallbackUsed: boolean;
  fallbackReason: string;
  modelCallSkipped: boolean;
  modelSkipped?: boolean;
  technicalDetails?: ContextTraceTechnicalDetails | null;
}

export interface ConversationItem {
  conversationId: number;
  title: string;
  contextMode: string;
  status: string;
  boundKnowledgeBaseId?: number | null;
  summaryEnabled: boolean;
  memoryEnabled: boolean;
  lastMessageTime?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ConversationMessageItem {
  messageId: number;
  conversationId: number;
  role: string;
  content: string;
  sequenceNo?: number;
  tokenCount?: number;
  createdAt?: string | null;
  citations?: KnowledgeBaseCitationItem[];
  contextTrace?: ContextTraceData | null;
}

export interface ConversationSummaryData {
  conversationId: number;
  summary?: string | null;
  coveredStartSeq?: number | null;
  coveredEndSeq?: number | null;
  summaryVersion?: number | null;
  status: string;
  tokenCount?: number | null;
  updatedAt?: string | null;
}

export function listConversations(limit = 30): Promise<ApiResponse<ConversationItem[]>> {
  return apiRequest<ConversationItem[]>(`/api/conversations?limit=${limit}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function createConversation(payload: {
  title?: string;
  contextMode?: ConversationContextMode;
  boundKnowledgeBaseId?: number;
}): Promise<ApiResponse<ConversationItem>> {
  return apiRequest<ConversationItem>("/api/conversations", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function getConversation(conversationId: number): Promise<ApiResponse<ConversationItem>> {
  return apiRequest<ConversationItem>(`/api/conversations/${conversationId}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function bindConversationKnowledgeBase(
  conversationId: number,
  knowledgeBaseId: number
): Promise<ApiResponse<ConversationItem>> {
  return apiRequest<ConversationItem>(`/api/conversations/${conversationId}/knowledge-base`, {
    method: "PATCH",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ knowledgeBaseId })
  });
}

export function unbindConversationKnowledgeBase(
  conversationId: number
): Promise<ApiResponse<ConversationItem>> {
  return apiRequest<ConversationItem>(`/api/conversations/${conversationId}/knowledge-base`, {
    method: "DELETE",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function listConversationMessages(
  conversationId: number,
  limit = 50
): Promise<ApiResponse<ConversationMessageItem[]>> {
  return apiRequest<ConversationMessageItem[]>(
    `/api/conversations/${conversationId}/messages?limit=${limit}`,
    {
      method: "GET",
      headers: {
        ...buildAuthorizationHeader()
      }
    }
  );
}

export function sendConversationMessage(
  conversationId: number,
  content: string,
  groundingPolicy?: GroundingPolicy
): Promise<ApiResponse<ConversationMessageItem>> {
  return apiRequest<ConversationMessageItem>(`/api/conversations/${conversationId}/messages`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(groundingPolicy ? { content, groundingPolicy } : { content })
  });
}

export function getConversationMessageTrace(
  conversationId: number,
  messageId: number
): Promise<ApiResponse<ContextTraceData>> {
  return apiRequest<ContextTraceData>(
    `/api/conversations/${conversationId}/messages/${messageId}/trace`,
    {
      method: "GET",
      headers: {
        ...buildAuthorizationHeader()
      }
    }
  );
}

export function getConversationSummary(
  conversationId: number
): Promise<ApiResponse<ConversationSummaryData>> {
  return apiRequest<ConversationSummaryData>(`/api/conversations/${conversationId}/summary`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function refreshConversationSummary(
  conversationId: number
): Promise<ApiResponse<ConversationSummaryData>> {
  return apiRequest<ConversationSummaryData>(`/api/conversations/${conversationId}/summary/refresh`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function deleteConversationSummary(
  conversationId: number
): Promise<ApiResponse<ConversationSummaryData>> {
  return apiRequest<ConversationSummaryData>(`/api/conversations/${conversationId}/summary`, {
    method: "DELETE",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
