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

