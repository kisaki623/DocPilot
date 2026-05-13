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
  taskId?: number;
  documentId: number;
  task: string;
  sessionId?: string;
  decision: string;
  routingReason?: string;
  matchedKeywords?: string[];
  finalAnswer: string;
  citations?: DocumentQaCitationItem[];
  steps?: DocumentAgentStep[];
}

export interface AgentTask {
  id: number;
  userId?: number;
  documentId: number;
  sessionId?: string;
  taskInput?: string;
  decision?: string;
  finalAnswer?: string;
  status?: string;
  errorMsg?: string;
  totalDurationMs?: number;
  startTime?: string;
  finishTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentPersistedStep {
  id?: number;
  taskId: number;
  stepIndex: number;
  toolName: string;
  inputSummary?: string;
  outputSummary?: string;
  status?: string;
  durationMs?: number;
  errorMsg?: string;
  startTime?: string;
  finishTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentTaskTraceData {
  task: AgentTask;
  steps: AgentPersistedStep[];
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

export function getAgentTask(taskId: number): Promise<ApiResponse<AgentTaskTraceData>> {
  return apiRequest<AgentTaskTraceData>(`/api/ai/agent/task/${taskId}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function getAgentTaskSteps(taskId: number): Promise<ApiResponse<AgentPersistedStep[]>> {
  return apiRequest<AgentPersistedStep[]>(`/api/ai/agent/task/${taskId}/steps`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
