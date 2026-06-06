import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface ToolSpecItem {
  name: string;
  displayName?: string;
  description?: string;
  parameterSchema?: unknown;
  requiredFields?: string[];
  resultSchema?: unknown;
  riskLevel?: string;
  safeForLlmSelection?: boolean;
  callableByToolCallApi?: boolean;
}

export interface ToolCallData {
  toolName: string;
  status: string;
  result?: unknown;
  outputSummary?: string;
  errorType?: string;
  errorMessage?: string;
  durationMs?: number;
  citations?: unknown[];
  retrievalHits?: unknown[];
}

export function listAgentTools(): Promise<ApiResponse<ToolSpecItem[]>> {
  return apiRequest<ToolSpecItem[]>("/api/agent/tools", {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function callAgentTool(payload: {
  toolName: string;
  arguments: Record<string, unknown>;
}): Promise<ApiResponse<ToolCallData>> {
  return apiRequest<ToolCallData>("/api/agent/tools/call", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}
