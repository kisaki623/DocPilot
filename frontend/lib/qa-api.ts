import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface DocumentQaRequest {
  documentId: number;
  question: string;
  sessionId?: string;
}

export interface DocumentQaData {
  documentId: number;
  question: string;
  answer: string;
  sessionId?: string;
  citations?: DocumentQaCitationItem[];
}

export interface DocumentQaCitationItem {
  chunkIndex: number;
  charStart: number;
  charEnd: number;
  snippet: string;
  score: number;
}

export interface DocumentQaHistoryItem {
  id: number;
  documentId: number;
  question: string;
  answer: string;
  createTime: string;
}

export interface DocumentQaStreamCallbacks {
  onChunk?: (chunk: string) => void;
  onDone?: () => void;
  onError?: (message: string) => void;
}

function resolveStreamEndpoint(): string {
  if (typeof window === "undefined") {
    return "/backend/api/ai/qa/stream";
  }

  const explicitBase = (process.env.NEXT_PUBLIC_BACKEND_BASE_URL || "").trim();
  if (explicitBase) {
    return `${explicitBase.replace(/\/+$/, "")}/api/ai/qa/stream`;
  }

  // Avoid local dev proxy buffering for SSE by connecting to backend directly.
  const { protocol, hostname, port } = window.location;
  const isLocalHost = hostname === "localhost" || hostname === "127.0.0.1";
  const isNextDevPort = port === "3000" || port === "3001" || port === "3002";
  if (isLocalHost && isNextDevPort) {
    return `${protocol}//${hostname}:8081/api/ai/qa/stream`;
  }

  return "/backend/api/ai/qa/stream";
}

function getEventStreamMessage(responseText: string): string | null {
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
    const lines = block.split("\n");
    for (const line of lines) {
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        let dataValue = line.slice(5);
        if (dataValue.startsWith(" ")) {
          dataValue = dataValue.slice(1);
        }
        dataLines.push(dataValue);
      }
    }
    events.push({
      event,
      data: dataLines.join("\n")
    });
  }
  return events;
}

export async function askDocumentQuestionStream(
  payload: DocumentQaRequest,
  callbacks: DocumentQaStreamCallbacks = {},
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(resolveStreamEndpoint(), {
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
    throw new Error(getEventStreamMessage(errorText) || "Streaming QA request failed");
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
    const events = parseEventStream(completeChunk);
    for (const item of events) {
      if (item.event === "chunk") {
        callbacks.onChunk?.(item.data);
      } else if (item.event === "done") {
        callbacks.onDone?.();
      } else if (item.event === "error") {
        const message = item.data || "Streaming QA failed";
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
    const events = parseEventStream(buffer);
    for (const item of events) {
      if (item.event === "chunk") {
        callbacks.onChunk?.(item.data);
      } else if (item.event === "done") {
        callbacks.onDone?.();
      } else if (item.event === "error") {
        const message = item.data || "Streaming QA failed";
        callbacks.onError?.(message);
        throw new Error(message);
      }
    }
  }
}

export function askDocumentQuestion(
  payload: DocumentQaRequest
): Promise<ApiResponse<DocumentQaData>> {
  return apiRequest<DocumentQaData>("/api/ai/qa", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function getDocumentQaHistory(
  documentId: number,
  limit = 10
): Promise<ApiResponse<DocumentQaHistoryItem[]>> {
  const query = new URLSearchParams({
    documentId: String(documentId),
    limit: String(limit)
  });

  return apiRequest<DocumentQaHistoryItem[]>(`/api/ai/qa/history?${query.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
