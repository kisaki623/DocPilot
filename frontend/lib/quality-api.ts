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

export interface QualityTraceStepDetail {
  stepType: string;
  status: string;
  label: string;
  metrics: Record<string, number>;
  flags: Record<string, boolean>;
  attributes: Record<string, string>;
  buckets: string[];
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
  steps: QualityTraceStepDetail[];
}

export interface QualityDocumentCoverageSummary {
  documentCount?: number | null;
  coveredDocumentCount?: number | null;
  zeroHitDocumentCount?: number | null;
  maxHitsPerDocument?: number | null;
  minHitsPerDocument?: number | null;
}

export interface QualityToolQualitySummary {
  toolCallCount?: number | null;
  toolFailureCount?: number | null;
  toolArgsReviewCount?: number | null;
}

export interface QualityMemoryQualitySummary {
  memoryTriggerCount?: number | null;
  memoryHitCount?: number | null;
  memoryReviewCount?: number | null;
  ragEvidenceCount?: number | null;
}

export interface QualityParserQualitySummary {
  expectedFileTypeCount?: number | null;
  coveredFileTypeCount?: number | null;
  missingFileTypeCount?: number | null;
  allFileTypesCovered?: boolean | null;
  expectedStructureSignalCount?: number | null;
  coveredStructureSignalCount?: number | null;
  missingStructureSignalCount?: number | null;
  allStructureSignalsCovered?: boolean | null;
  fileCount?: number | null;
  parsedFileCount?: number | null;
  parserFailureCount?: number | null;
  parsePassRate?: number | null;
  sourceLocatorCount?: number | null;
  sourceLocatorCoverageRate?: number | null;
  chunkCountKnown?: number | null;
  chunkCount?: number | null;
  retrieveHitCount?: number | null;
  directRetrieveHitCount?: number | null;
  qaRetrievalHitCount?: number | null;
  citationCount?: number | null;
  directRetrieveOkCount?: number | null;
  qaRetrieveOkCount?: number | null;
  directRetrieveNoEvidenceCount?: number | null;
  qaRetrieveNoEvidenceCount?: number | null;
  directRetrieveMaxAttempts?: number | null;
  qaRetrieveMaxAttempts?: number | null;
  environmentUnstable?: boolean | null;
  retrieveCoverageRate?: number | null;
  citationCoverageRate?: number | null;
  negativeCaseCount?: number | null;
  negativeCasePassCount?: number | null;
  negativeCaseFailCount?: number | null;
  boundaryPassRate?: number | null;
  unsupportedUploadRejected?: boolean | null;
  warningCountKnown?: number | null;
  totalWarningCount?: number | null;
  filesWithWarnings?: number | null;
  reviewReasons: string[];
  unavailableMetrics: string[];
}

export interface QualityRunDiagnostics {
  documentCoverage?: QualityDocumentCoverageSummary | null;
  toolQuality?: QualityToolQualitySummary | null;
  memoryQuality?: QualityMemoryQualitySummary | null;
  parserQuality?: QualityParserQualitySummary | null;
}

export interface QualityEvalCaseCatalogItem {
  caseId: string;
  caseVersion: number;
  owner: string;
  lastUpdated: string;
  riskLevel: string;
  caseLayer: string;
  riskGate: string;
  scoringSummary: string[];
  regressionPolicy: string[];
  failureHistoryMarkers: string[];
  sourceIssueIds: string[];
  lastVerifiedMarker: string;
  remediationHints: string[];
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
  diagnostics?: QualityRunDiagnostics | null;
}

export interface QualityTrendPoint {
  marker: string;
  status: string;
  updatedAt: string;
  failedGateCount: number;
  reviewGateCount: number;
  casePassRate?: number | null;
  totalTokens?: number | null;
  estimatedCost?: number | null;
  latencyMs?: number | null;
  durationMs?: number | null;
  failureBuckets: string[];
  reviewBuckets: string[];
}

export interface QualityRepeatedCaseSummary {
  caseId: string;
  failedCount: number;
  reviewCount: number;
  latestStatus: string;
  latestRunMarker: string;
}

export interface QualityTrendSummary {
  limit: number;
  runCount: number;
  statusCounts: Record<string, number>;
  failureBucketCounts: Record<string, number>;
  reviewBucketCounts: Record<string, number>;
  averageCasePassRate?: number | null;
  totalTokens?: number | null;
  estimatedCost?: number | null;
  averageLatencyMs?: number | null;
  averageDurationMs?: number | null;
  repeatedCases: QualityRepeatedCaseSummary[];
  points: QualityTrendPoint[];
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

export function getQualityTrendSummary(
  limit = 20
): Promise<ApiResponse<QualityTrendSummary>> {
  const params = new URLSearchParams({ limit: String(limit) });
  return apiRequest<QualityTrendSummary>(`/api/quality/trends?${params.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
