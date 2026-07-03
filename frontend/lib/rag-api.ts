import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface RagCitationItem {
  index?: number;
  documentId?: number;
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
}

export interface RagRetrievalHitItem {
  citationIndex?: number;
  vectorId?: string;
  score?: number;
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
}

export interface RagRetrievalData {
  documentId: number;
  query: string;
  topK?: number;
  indexVersion?: number;
  noEvidence?: boolean;
  hitCount?: number;
  provider?: string;
  collection?: string;
  embeddingModel?: string;
  hits?: RagRetrievalHitItem[];
  citations?: RagCitationItem[];
}

export interface RagQaData {
  documentId: number;
  question: string;
  answer: string;
  sessionId?: string;
  noEvidence?: boolean;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  retrieval?: RagRetrievalData;
  citations?: RagCitationItem[];
}

export interface RagRetrieveRequest {
  documentId: number;
  query: string;
  topK?: number;
  indexVersion?: number;
}

export interface RagQaRequest {
  question: string;
  topK?: number;
  indexVersion?: number;
  sessionId?: string;
}

export interface RagStreamPayload {
  documentId?: number;
  sessionId?: string;
  noEvidence?: boolean;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  retrieval?: RagRetrievalData;
  citations?: RagCitationItem[];
}

export interface RagStreamCallbacks {
  onMeta?: (payload: RagStreamPayload) => void;
  onRetrieval?: (payload: RagRetrievalData) => void;
  onCitation?: (payload: RagCitationItem) => void;
  onChunk?: (chunk: string) => void;
  onDone?: (payload?: RagStreamPayload) => void;
  onError?: (message: string) => void;
}

function resolveRagStreamEndpoint(documentId: number): string {
  const path = `/api/documents/${documentId}/qa/rag/stream`;
  if (typeof window === "undefined") {
    return `/backend${path}`;
  }

  const explicitBase = (process.env.NEXT_PUBLIC_BACKEND_BASE_URL || "").trim();
  if (explicitBase) {
    return `${explicitBase.replace(/\/+$/, "")}${path}`;
  }

  const { protocol, hostname, port } = window.location;
  const isLocalHost = hostname === "localhost" || hostname === "127.0.0.1";
  const isNextDevPort = port === "3000" || port === "3001" || port === "3002";
  if (isLocalHost && isNextDevPort) {
    return `${protocol}//${hostname}:8081${path}`;
  }

  return `/backend${path}`;
}

function getJsonErrorMessage(responseText: string): string | null {
  try {
    const payload = JSON.parse(responseText) as ApiResponse<unknown>;
    return payload?.message || null;
  } catch {
    return null;
  }
}

function parseEventStream(rawChunk: string): Array<{ event: string; data: string }> {
  const events: Array<{ event: string; data: string }> = [];
  const blocks = rawChunk.split("\n\n");
  for (const block of blocks) {
    if (!block || /^\s*$/.test(block)) {
      continue;
    }
    let event = "message";
    const dataLines: string[] = [];
    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        let value = line.slice(5);
        if (value.startsWith(" ")) {
          value = value.slice(1);
        }
        dataLines.push(value);
      }
    }
    events.push({ event, data: dataLines.join("\n") });
  }
  return events;
}

function parseStreamPayload(data: string): RagStreamPayload | undefined {
  const text = (data || "").trim();
  if (!text || text === "[DONE]") {
    return undefined;
  }
  try {
    return JSON.parse(text) as RagStreamPayload;
  } catch {
    return undefined;
  }
}

function parseStreamError(data: string): string {
  const text = (data || "").trim();
  if (!text) {
    return "RAG streaming request failed";
  }
  try {
    const parsed = JSON.parse(text) as { message?: string };
    if (parsed?.message) {
      return parsed.message;
    }
  } catch {
    // fallback to raw text
  }
  return text;
}

export function retrieveDocumentRag(
  payload: RagRetrieveRequest
): Promise<ApiResponse<RagRetrievalData>> {
  return apiRequest<RagRetrievalData>("/api/rag/retrieve", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function askDocumentRagQuestion(
  documentId: number,
  payload: RagQaRequest
): Promise<ApiResponse<RagQaData>> {
  return apiRequest<RagQaData>(`/api/documents/${documentId}/qa/rag`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export async function askDocumentRagQuestionStream(
  documentId: number,
  payload: RagQaRequest,
  callbacks: RagStreamCallbacks = {},
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(resolveRagStreamEndpoint(documentId), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload),
    cache: "no-store",
    signal
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(getJsonErrorMessage(errorText) || "RAG streaming request failed");
  }
  if (!response.body) {
    throw new Error("Streaming response body is unavailable");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true }).replace(/\r/g, "");
    const lastSeparator = buffer.lastIndexOf("\n\n");
    if (lastSeparator < 0) {
      continue;
    }
    const completeChunk = buffer.slice(0, lastSeparator);
    buffer = buffer.slice(lastSeparator + 2);
    for (const item of parseEventStream(completeChunk)) {
      if (item.event === "chunk") {
        callbacks.onChunk?.(item.data);
      } else if (item.event === "meta") {
        callbacks.onMeta?.(parseStreamPayload(item.data) || {});
      } else if (item.event === "retrieval") {
        callbacks.onRetrieval?.((parseStreamPayload(item.data) || {}) as RagRetrievalData);
      } else if (item.event === "citation") {
        callbacks.onCitation?.((parseStreamPayload(item.data) || {}) as RagCitationItem);
      } else if (item.event === "done") {
        callbacks.onDone?.(parseStreamPayload(item.data));
      } else if (item.event === "error") {
        const message = parseStreamError(item.data);
        callbacks.onError?.(message);
        throw new Error(message);
      }
    }
  }

  const remaining = decoder.decode();
  if (remaining) {
    buffer += remaining;
  }
  if (buffer.trim()) {
    for (const item of parseEventStream(buffer)) {
      if (item.event === "chunk") {
        callbacks.onChunk?.(item.data);
      } else if (item.event === "meta") {
        callbacks.onMeta?.(parseStreamPayload(item.data) || {});
      } else if (item.event === "retrieval") {
        callbacks.onRetrieval?.((parseStreamPayload(item.data) || {}) as RagRetrievalData);
      } else if (item.event === "citation") {
        callbacks.onCitation?.((parseStreamPayload(item.data) || {}) as RagCitationItem);
      } else if (item.event === "done") {
        callbacks.onDone?.(parseStreamPayload(item.data));
      } else if (item.event === "error") {
        const message = parseStreamError(item.data);
        callbacks.onError?.(message);
        throw new Error(message);
      }
    }
  }
}
