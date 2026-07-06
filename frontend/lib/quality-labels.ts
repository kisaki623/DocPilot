const STATUS_LABELS: Record<string, string> = {
  PASS: "通过",
  SUCCESS: "成功",
  REVIEW: "需复查",
  BLOCKED: "环境阻塞",
  FAILED: "失败",
  FAIL: "失败",
  FAILED_CORE_FLOW: "核心链路失败",
  FAILED_SECURITY_GATE: "安全门禁失败",
  NOT_RUN: "未运行",
};

const BUCKET_LABELS: Record<string, string> = {
  RAG_RETRIEVAL_MISS: "RAG 漏召回",
  CITATION_UNSUPPORTED: "引用不支撑",
  DISTRACTOR_CITATION: "干扰引用",
  NO_EVIDENCE_FALSE_POSITIVE: "无证据误判",
  MEMORY_CONFLICT: "记忆冲突",
  TOOL_FAILURE: "工具调用失败",
  PERMISSION_REGRESSION: "权限隔离回归",
  FRONTEND_UX: "前端体验问题",
  ENV_BLOCKED: "环境/依赖阻塞",
  OTHER: "其他",
};

const METRIC_LABELS: Record<string, string> = {
  casePassRate: "Case 通过率",
  distractorCitationFreeCount: "无干扰引用数",
  answerFaithfulnessPassCount: "回答忠实通过数",
  citationPhraseSupportPassCount: "引用短语支撑数",
  retrieveHits: "检索命中数",
  qaCitations: "回答引用数",
  distractorCitationCount: "干扰引用数",
  evidenceCount: "证据数",
  memoryCount: "记忆数",
  documentHitCount: "命中文档数",
  promptTokens: "Prompt tokens",
  completionTokens: "Completion tokens",
  totalTokens: "总 tokens",
  estimatedCost: "估算成本",
  latencyMs: "延迟 ms",
  durationMs: "耗时 ms",
  retryCount: "重试次数",
  modelCallCount: "模型调用数",
  toolCallCount: "工具调用数",
  gateCount: "质量门禁数",
  failedGateCount: "失败门禁数",
  reviewGateCount: "复查门禁数",
};

const FLAG_LABELS: Record<string, string> = {
  targetCitationCovered: "目标引用已覆盖",
  noEvidenceCorrect: "无证据判断正确",
  expectedEvidenceSupported: "预期证据已支撑",
  traceRagTriggered: "Trace 中 RAG 已触发",
  traceRagRequired: "Trace 要求 RAG",
  ragTriggered: "RAG 已触发",
  ragRequired: "需要 RAG",
  citationMarkerPresent: "引用标记存在",
  forbiddenMarkerHit: "命中禁止标记",
  expectedMarkersSatisfied: "预期标记满足",
};

const CASE_TYPE_LABELS: Record<string, string> = {
  agent_quality: "Agent 质量",
  rag_quality: "RAG 质量",
  memory_quality: "记忆质量",
  route_smoke: "路由 smoke",
};

const TRACE_STEP_LABELS: Record<string, string> = {
  eval_case: "Eval Case",
  agent_step: "Agent 步骤",
  rag_retrieve: "RAG 检索",
  tool_call: "工具调用",
  model_call: "模型调用",
  citation: "引用校验",
  failure_bucket: "失败桶",
};

function normalize(value?: string | null): string {
  return (value || "").trim();
}

function compactKey(value: string): string {
  return value.toLowerCase().replace(/[\s_-]+/g, "");
}

function labelWithRaw(label: string, raw: string): string {
  if (!raw || label === raw) {
    return label || "-";
  }
  return `${label} (${raw})`;
}

export function formatQualityNumber(value?: number | null): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  if (Math.abs(value) < 1 && value !== 0) {
    return value.toFixed(4);
  }
  return new Intl.NumberFormat("zh-CN").format(value);
}

export function formatQualityBoolean(value?: boolean | null): string {
  if (typeof value !== "boolean") {
    return "-";
  }
  return value ? "是" : "否";
}

export function labelStatus(status?: string | null): string {
  const raw = normalize(status);
  return STATUS_LABELS[raw] || raw || "-";
}

export function formatStatus(status?: string | null): string {
  const raw = normalize(status);
  return labelWithRaw(labelStatus(raw), raw);
}

export function labelBucket(bucket?: string | null): string {
  const raw = normalize(bucket);
  if (!raw) {
    return "-";
  }
  const exact = BUCKET_LABELS[raw];
  if (exact) {
    return exact;
  }
  const lower = compactKey(raw);
  if (lower.includes("distractor")) {
    return BUCKET_LABELS.DISTRACTOR_CITATION;
  }
  if (lower.includes("noevidence") || lower.includes("evidencefalse")) {
    return BUCKET_LABELS.NO_EVIDENCE_FALSE_POSITIVE;
  }
  if (
    lower.includes("citation") ||
    lower.includes("unsupported") ||
    lower.includes("grounding") ||
    lower.includes("support")
  ) {
    return BUCKET_LABELS.CITATION_UNSUPPORTED;
  }
  if (
    lower.includes("retrieval") ||
    lower.includes("retrieve") ||
    lower.includes("miss") ||
    lower.includes("recall")
  ) {
    return BUCKET_LABELS.RAG_RETRIEVAL_MISS;
  }
  if (lower.includes("memory")) {
    return BUCKET_LABELS.MEMORY_CONFLICT;
  }
  if (lower.includes("tool")) {
    return BUCKET_LABELS.TOOL_FAILURE;
  }
  if (
    lower.includes("permission") ||
    lower.includes("forbidden") ||
    lower.includes("unauthorized") ||
    lower.includes("scope")
  ) {
    return BUCKET_LABELS.PERMISSION_REGRESSION;
  }
  if (
    lower.includes("frontend") ||
    lower.includes("consoleerror") ||
    lower.includes("route") ||
    lower.includes("overflow") ||
    lower.includes("ui") ||
    lower.includes("ux")
  ) {
    return BUCKET_LABELS.FRONTEND_UX;
  }
  if (
    lower.includes("blocked") ||
    lower.includes("timeout") ||
    lower.includes("health") ||
    lower.includes("tunnel") ||
    lower.includes("env") ||
    lower.includes("parsefailed") ||
    lower.includes("artifactparsefailed")
  ) {
    return BUCKET_LABELS.ENV_BLOCKED;
  }
  return raw;
}

export function formatBucket(bucket?: string | null): string {
  const raw = normalize(bucket);
  return labelWithRaw(labelBucket(raw), raw);
}

export function formatBucketList(values?: string[], limit = 5): string {
  if (!values || values.length === 0) {
    return "-";
  }
  return values.slice(0, limit).map((value) => formatBucket(value)).join(" / ");
}

export function labelMetric(key?: string | null): string {
  const raw = normalize(key);
  return METRIC_LABELS[raw] || raw || "-";
}

export function formatMetricKey(key?: string | null): string {
  const raw = normalize(key);
  return labelWithRaw(labelMetric(raw), raw);
}

export function labelFlag(key?: string | null): string {
  const raw = normalize(key);
  return FLAG_LABELS[raw] || raw || "-";
}

export function formatFlagKey(key?: string | null): string {
  const raw = normalize(key);
  return labelWithRaw(labelFlag(raw), raw);
}

export function formatMetricValue(key: string, value: number): string {
  const lower = compactKey(key);
  if (lower.includes("rate") && value >= 0 && value <= 1) {
    return `${(value * 100).toFixed(1)}%`;
  }
  return formatQualityNumber(value);
}

export function formatMetricList(
  metrics?: Record<string, number>,
  limit = 8
): string {
  const entries = Object.entries(metrics || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .slice(0, limit)
    .map(([key, value]) => `${formatMetricKey(key)}: ${formatMetricValue(key, value)}`)
    .join(" / ");
}

export function formatFlagList(flags?: Record<string, boolean>, limit = 8): string {
  const entries = Object.entries(flags || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .slice(0, limit)
    .map(([key, value]) => `${formatFlagKey(key)}: ${formatQualityBoolean(value)}`)
    .join(" / ");
}

export function labelCaseType(caseType?: string | null): string {
  const raw = normalize(caseType);
  return CASE_TYPE_LABELS[raw] || raw || "-";
}

export function formatCaseType(caseType?: string | null): string {
  const raw = normalize(caseType);
  return labelWithRaw(labelCaseType(raw), raw);
}

export function labelGate(gateName?: string | null): string {
  const raw = normalize(gateName);
  if (!raw) {
    return "-";
  }
  return raw
    .replace(/_/g, " ")
    .replace(/\bqa\b/gi, "QA")
    .replace(/\brag\b/gi, "RAG")
    .replace(/\bkb\b/gi, "KB");
}

export function formatGate(gateName?: string | null): string {
  const raw = normalize(gateName);
  return labelWithRaw(labelGate(raw), raw);
}

export function labelTraceStep(stepType?: string | null): string {
  const raw = normalize(stepType);
  return TRACE_STEP_LABELS[raw] || raw || "Trace Step";
}
