import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface QualityTokenUsageSummary {
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  estimatedCost?: number | null;
}

export interface QualityGateSummary {
  name: string;
  status?: string | null;
  passed?: boolean | null;
  metrics: Record<string, number>;
  flags: Record<string, boolean>;
  failureBuckets: string[];
  reviewBuckets: string[];
}

export interface QualityEvalCaseResultDetail {
  caseId: string;
  caseType: string;
  status: string;
  passed?: boolean | null;
  traceId: string;
  agentRunId: string;
  failureBuckets: string[];
  reviewBuckets: string[];
  metrics: Record<string, number>;
  flags: Record<string, boolean>;
}

export interface QualityTraceReference {
  caseId: string;
  caseType: string;
  status: string;
  gateName: string;
  traceId: string;
  agentRunId: string;
  conversationId: string;
  failureBuckets: string[];
  reviewBuckets: string[];
}

export interface QualityEvalCaseCatalogItem {
  caseId: string;
  caseType: string;
  tags: string[];
  expectedEvidence: string[];
  expectedTools: string[];
  scoringRules: string[];
  latestStatus: string;
  latestRunMarker: string;
  latestTraceId: string;
  latestAgentRunId: string;
  latestFailureBuckets: string[];
  latestReviewBuckets: string[];
}

export interface QualityRunSummary {
  marker: string;
  source: string;
  artifactName: string;
  status: string;
  updatedAt: string;
  gateCount: number;
  failedGateCount: number;
  reviewGateCount: number;
  failureBuckets: string[];
  reviewBuckets: string[];
  tokenUsage: QualityTokenUsageSummary;
  artifactMissing: boolean;
  artifactParseFailed: boolean;
}

export interface QualityRunDetail {
  summary: QualityRunSummary;
  gates: QualityGateSummary[];
  evalCases: QualityEvalCaseResultDetail[];
  traceReferences: QualityTraceReference[];
}

export function listQualityRuns(
  limit = 20
): Promise<ApiResponse<QualityRunSummary[]>> {
  const params = new URLSearchParams({ limit: String(limit) });
  return apiRequest<QualityRunSummary[]>(`/api/quality/runs?${params.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function getQualityRunDetail(
  marker: string
): Promise<ApiResponse<QualityRunDetail>> {
  return apiRequest<QualityRunDetail>(
    `/api/quality/runs/${encodeURIComponent(marker)}`,
    {
      method: "GET",
      headers: {
        ...buildAuthorizationHeader()
      }
    }
  );
}

export function listQualityEvalCases(): Promise<ApiResponse<QualityEvalCaseCatalogItem[]>> {
  return apiRequest<QualityEvalCaseCatalogItem[]>("/api/quality/eval-cases", {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
