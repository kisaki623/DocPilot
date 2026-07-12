import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface ParseTaskCreateData {
  taskId: number;
  userId: number;
  documentId: number;
  fileRecordId: number;
  status: string;
  statusLabel?: string;
  statusDescription?: string;
  reused?: boolean;
  retryCount?: number;
  errorMsg?: string;
}

export interface ParseTaskStatusData {
  taskId: number;
  userId: number;
  documentId: number;
  fileRecordId: number;
  status: string;
  statusLabel?: string;
  statusDescription?: string;
  documentParseStatus?: string;
  terminal?: boolean;
  processing?: boolean;
  retryAllowed?: boolean;
  reparseAllowed?: boolean;
  safeReindexAllowed?: boolean;
  contentOnlyReindexAllowed?: boolean;
  parsedContentPresent?: boolean;
  stale?: boolean;
  staleReason?: string;
  consumeStatus?: string;
  outboxStatus?: string;
  outboxRetryCount?: number;
  outboxNextRetryTime?: string;
  errorCode?: string;
  failedStage?: string;
  recoveryAction?: string;
  recoveryDescription?: string;
  retryCount?: number;
  startTime?: string;
  finishTime?: string;
  updateTime?: string;
}

export function createParseTask(
  documentId: number
): Promise<ApiResponse<ParseTaskCreateData>> {
  return apiRequest<ParseTaskCreateData>("/api/task/parse/create", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ documentId })
  });
}

export function retryParseTask(
  documentId: number
): Promise<ApiResponse<ParseTaskCreateData>> {
  return apiRequest<ParseTaskCreateData>("/api/task/parse/retry", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ documentId })
  });
}

export function reparseTask(
  documentId: number
): Promise<ApiResponse<ParseTaskCreateData>> {
  return apiRequest<ParseTaskCreateData>("/api/task/parse/reparse", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ documentId })
  });
}

export function getParseTaskStatus(
  documentId: number
): Promise<ApiResponse<ParseTaskStatusData>> {
  const params = new URLSearchParams({
    documentId: String(documentId)
  });

  return apiRequest<ParseTaskStatusData>(`/api/task/parse/status?${params.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
