import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface KnowledgeBaseItem {
  id: number;
  userId?: number;
  name: string;
  description?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeBaseDocumentItem {
  id?: number;
  knowledgeBaseId: number;
  documentId: number;
  documentTitle?: string;
  parseStatus?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeBaseDetailData extends KnowledgeBaseItem {
  documents?: KnowledgeBaseDocumentItem[];
}

export interface KnowledgeBaseDocumentMutationData {
  knowledgeBaseId?: number;
  documentIds?: number[];
  activeDocumentCount?: number;
}

export interface KnowledgeBaseCitationItem {
  index?: number;
  knowledgeBaseId?: number;
  documentId?: number;
  documentTitle?: string;
  indexVersion?: number;
  chunkId?: number;
  chunkIndex?: number;
  startOffset?: number;
  endOffset?: number;
  contentHash?: string;
  snippet?: string;
  quoteText?: string;
  quoteStartOffset?: number;
  quoteEndOffset?: number;
  score?: number;
  vectorScore?: number;
  keywordScore?: number;
  fusedScore?: number;
  rerankScore?: number;
}

export interface KnowledgeBaseRetrievalHitItem {
  citationIndex?: number;
  knowledgeBaseId?: number;
  vectorId?: string;
  score?: number;
  documentId?: number;
  documentTitle?: string;
  chunkId?: number;
  chunkIndex?: number;
  content?: string;
  contentHash?: string;
  startOffset?: number;
  endOffset?: number;
  quoteText?: string;
  quoteStartOffset?: number;
  quoteEndOffset?: number;
  tokenCount?: number;
  embeddingModel?: string;
  vectorScore?: number;
  keywordScore?: number;
  fusedScore?: number;
  rerankScore?: number;
}

export interface KnowledgeBaseRetrievalData {
  knowledgeBaseId: number;
  query: string;
  topK?: number;
  indexVersion?: number;
  documentIds?: number[];
  noEvidence?: boolean;
  provider?: string;
  collection?: string;
  embeddingModel?: string;
  documentHitCounts?: Record<string, number>;
  retrievalMode?: string;
  rerankApplied?: boolean;
  rerankModel?: string;
  hits?: KnowledgeBaseRetrievalHitItem[];
  citations?: KnowledgeBaseCitationItem[];
}

export interface KnowledgeBaseQaData {
  knowledgeBaseId: number;
  question: string;
  answer: string;
  sessionId?: string;
  noEvidence?: boolean;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  answerProvider?: string;
  answerModel?: string;
  modelCallCount?: number;
  retrieval?: KnowledgeBaseRetrievalData;
  citations?: KnowledgeBaseCitationItem[];
}

export function listKnowledgeBases(): Promise<ApiResponse<KnowledgeBaseItem[]>> {
  return apiRequest<KnowledgeBaseItem[]>("/api/knowledge-bases", {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function createKnowledgeBase(payload: {
  name: string;
  description?: string;
}): Promise<ApiResponse<KnowledgeBaseItem>> {
  return apiRequest<KnowledgeBaseItem>("/api/knowledge-bases", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function getKnowledgeBaseDetail(
  knowledgeBaseId: number
): Promise<ApiResponse<KnowledgeBaseDetailData>> {
  return apiRequest<KnowledgeBaseDetailData>(`/api/knowledge-bases/${knowledgeBaseId}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function addKnowledgeBaseDocuments(
  knowledgeBaseId: number,
  documentIds: number[]
): Promise<ApiResponse<KnowledgeBaseDocumentMutationData>> {
  return apiRequest<KnowledgeBaseDocumentMutationData>(`/api/knowledge-bases/${knowledgeBaseId}/documents`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ documentIds })
  });
}

export function removeKnowledgeBaseDocument(
  knowledgeBaseId: number,
  documentId: number
): Promise<ApiResponse<KnowledgeBaseDocumentMutationData>> {
  return apiRequest<KnowledgeBaseDocumentMutationData>(
    `/api/knowledge-bases/${knowledgeBaseId}/documents/${documentId}`,
    {
      method: "DELETE",
      headers: {
        ...buildAuthorizationHeader()
      }
    }
  );
}

export function retrieveKnowledgeBaseRag(
  knowledgeBaseId: number,
  payload: { query: string; topK?: number; indexVersion?: number }
): Promise<ApiResponse<KnowledgeBaseRetrievalData>> {
  return apiRequest<KnowledgeBaseRetrievalData>(`/api/knowledge-bases/${knowledgeBaseId}/rag/retrieve`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function askKnowledgeBaseRagQuestion(
  knowledgeBaseId: number,
  payload: { question: string; topK?: number; indexVersion?: number; sessionId?: string }
): Promise<ApiResponse<KnowledgeBaseQaData>> {
  return apiRequest<KnowledgeBaseQaData>(`/api/knowledge-bases/${knowledgeBaseId}/qa/rag`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}
