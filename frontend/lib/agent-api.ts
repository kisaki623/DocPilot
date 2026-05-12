import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";
import type { DocumentQaCitationItem } from "@/lib/qa-api";

export interface DocumentAgentRunRequest {
  documentId: number;
  task: string;
  sessionId?: string;
}

export interface DocumentAgentStep {
  stepIndex: number;
  toolName: string;
  status: string;
  inputSummary: string;
  outputSummary: string;
  durationMs: number;
}

export interface DocumentAgentRunData {
  traceId?: string;
  startedAt?: string;
  finishedAt?: string;
  totalDurationMs?: number;
  success?: boolean;
  documentId: number;
  task: string;
  sessionId?: string;
  decision: string;
  finalAnswer: string;
  citations?: DocumentQaCitationItem[];
  steps?: DocumentAgentStep[];
}

export function runDocumentAgent(
  payload: DocumentAgentRunRequest
): Promise<ApiResponse<DocumentAgentRunData>> {
  return apiRequest<DocumentAgentRunData>("/api/ai/agent/run", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}
