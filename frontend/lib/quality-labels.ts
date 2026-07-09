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
  AGENT_ROUTING_MISMATCH: "Agent 路由不匹配",
  KB_AGENT_ROUTING_MISMATCH: "KB Agent 路由不匹配",
  KB_AGENT_ANSWER_ROUTING_MISMATCH: "KB Agent 回答路由不匹配",
  KB_AGENT_UNSUPPORTED_INTENT: "KB Agent P0 意图边界异常",
  KB_AGENT_SCOPE_FAILURE: "KB Agent 权限失败透传异常",
  PERMISSION_REGRESSION: "权限隔离回归",
  FRONTEND_UX: "前端体验问题",
  ENV_BLOCKED: "环境/依赖阻塞",
  OTHER: "其他",
};

const METRIC_LABELS: Record<string, string> = {
  casePassRate: "用例通过率",
  distractorCitationFreeCount: "无干扰引用数",
  answerFaithfulnessPassCount: "回答忠实通过数",
  citationPhraseSupportPassCount: "引用短语支撑数",
  retrieveHits: "检索命中数",
  citations: "引用数",
  qaCitations: "回答引用数",
  answerCitations: "Agent 回答引用数",
  distractorCitationCount: "干扰引用数",
  evidenceCount: "证据数",
  memoryCount: "记忆数",
  documentHitCount: "命中文档数",
  promptTokens: "提示词 tokens",
  completionTokens: "回答 tokens",
  totalTokens: "总 tokens",
  estimatedCost: "估算成本",
  latencyMs: "延迟 ms",
  durationMs: "耗时 ms",
  answerDurationMs: "Agent 回答耗时 ms",
  retryCount: "重试次数",
  modelCallCount: "模型调用数",
  toolCallCount: "工具调用数",
  gateCount: "质量门禁数",
  failedGateCount: "失败门禁数",
  reviewGateCount: "复查门禁数",
  fileCount: "文件数",
  parsedFileCount: "解析成功文件数",
  parserFailureCount: "解析失败数",
  chunkCount: "切片数",
  retrieveHitCount: "检索命中文件数",
  directRetrieveHitCount: "直接检索命中文件数",
  qaRetrievalHitCount: "问答检索命中文件数",
  citationCount: "引用文件数",
  sourceLocatorCount: "来源定位数",
  negativeCaseCount: "负向用例数",
  negativeCasePassCount: "负向通过数",
  negativeCaseFailCount: "负向失败数",
  expectedDecisionMatched: "路由决策匹配",
  checkCount: "检查项数",
  queryVariantCount: "查询变体数",
  queryDedupeCount: "查询去重数",
};

const FLAG_LABELS: Record<string, string> = {
  success: "执行成功",
  targetCitationCovered: "目标引用已覆盖",
  coversBothDocuments: "覆盖两份文档",
  noEvidenceCorrect: "无证据判断正确",
  expectedEvidenceSupported: "预期证据已支撑",
  traceRagTriggered: "链路中 RAG 已触发",
  traceRagRequired: "链路要求 RAG",
  ragTriggered: "RAG 已触发",
  ragRequired: "需要 RAG",
  citationMarkerPresent: "引用标记存在",
  forbiddenMarkerHit: "命中禁止标记",
  expectedMarkersSatisfied: "预期标记满足",
  sourceLocatorPresent: "来源定位存在",
  retrieveHit: "检索已命中",
  citationPresent: "引用已生成",
  unsupportedUploadRejected: "不支持格式已拒绝",
  kbSearchDecisionPass: "KB 检索路由通过",
  answerDecisionPass: "KB 回答路由通过",
  answerSuccess: "KB 回答执行成功",
  answerCoversBothDocuments: "KB 回答覆盖两份文档",
  answerNoEvidenceHandled: "KB 无证据回答已处理",
  unsupportedIntentPass: "不支持意图处理通过",
  scopeFailurePropagated: "权限失败已透传",
  unsupportedIntentRejected: "不支持意图已拒绝",
  foreignKnowledgeBaseRejected: "跨用户知识库已拒绝",
  rerankApplied: "Rerank 已应用",
  multiQueryApplied: "多查询已应用",
};

const CASE_TYPE_LABELS: Record<string, string> = {
  agent_quality: "Agent 质量",
  agent_search: "Agent 检索路由",
  agent_search_route: "单文档 Agent 检索路由",
  agent_kb_search_route: "知识库 Agent 检索路由",
  rag_quality: "RAG 质量",
  memory_quality: "记忆质量",
  route_smoke: "路由冒烟",
  natural_corpus: "自然语料",
  conversation_trace: "会话链路",
};

const TRACE_STEP_LABELS: Record<string, string> = {
  eval_case: "评测用例",
  agent_step: "Agent 步骤",
  rag_retrieve: "RAG 检索",
  tool_call: "工具调用",
  model_call: "模型调用",
  citation: "引用校验",
  failure_bucket: "失败桶",
};

const TRACE_ATTRIBUTE_LABELS: Record<string, string> = {
  decision: "路由决策",
  selectedTools: "检索工具",
  answerDecision: "回答决策",
  answerSelectedTools: "回答工具",
  retrievalMode: "检索模式",
};

const GATE_LABELS: Record<string, string> = {
  tunnel: "Tunnel 连通性",
  backendHealth: "后端健康检查",
  frontendRoutes: "前端路由",
  frontendInteraction: "前端交互",
  auth: "注册登录",
  uploadParseIndexing: "上传解析索引",
  chunkQuality: "切片质量",
  mysqlQdrantConsistency: "MySQL / Qdrant 一致性",
  singleDocumentRag: "单文档 RAG",
  knowledgeBaseRag: "知识库 RAG",
  knowledgeBaseAgent: "知识库 Agent",
  shortDocumentRag: "短文档 RAG",
  naturalCorpus: "自然语料",
  multiQueryRag: "多查询检索",
  answerGrounding: "回答依据性",
  noEvidenceThreshold: "无证据拒答",
  conversationTrace: "会话链路",
  memoryQuality: "记忆质量",
  permissionIsolation: "权限隔离",
  artifactRedaction: "Artifact 脱敏",
  parserRealChain: "文档解析真实链路",
  parserBoundary: "解析错误边界",
  cleanup: "清理检查",
  realProviderFaithfulness: "真实模型忠实性",
};

function normalize(value?: string | null): string {
  return (value || "").trim();
}

function compactKey(value: string): string {
  return value.toLowerCase().replace(/[\s_-]+/g, "");
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
  return labelStatus(raw);
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
  if (lower.includes("kbanswerdecision") || lower.includes("knowledgebaseanswerdecision")) {
    return BUCKET_LABELS.KB_AGENT_ANSWER_ROUTING_MISMATCH;
  }
  if (
    lower.includes("kbsearchdecision") ||
    lower.includes("knowledgebasesearchdecision") ||
    lower.includes("kbagentroute") ||
    lower.includes("kbrouting")
  ) {
    return BUCKET_LABELS.KB_AGENT_ROUTING_MISMATCH;
  }
  if (lower.includes("kbunsupportedintent") || lower.includes("unsupportedintent")) {
    return BUCKET_LABELS.KB_AGENT_UNSUPPORTED_INTENT;
  }
  if (lower.includes("kbscopefailure") || lower.includes("scopefailurenotpropagated")) {
    return BUCKET_LABELS.KB_AGENT_SCOPE_FAILURE;
  }
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
  if (
    lower.includes("expecteddecision") ||
    lower.includes("decisionmismatch") ||
    lower.includes("selector") ||
    lower.includes("searchoverrouting") ||
    lower.includes("answeroverrouting") ||
    lower.includes("routing")
  ) {
    return BUCKET_LABELS.AGENT_ROUTING_MISMATCH;
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
  return labelBucket(raw);
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
  return labelMetric(raw);
}

export function labelFlag(key?: string | null): string {
  const raw = normalize(key);
  return FLAG_LABELS[raw] || raw || "-";
}

export function formatFlagKey(key?: string | null): string {
  const raw = normalize(key);
  return labelFlag(raw);
}

export function formatMetricValue(key: string, value: number): string {
  const lower = compactKey(key);
  if (lower === "expecteddecisionmatched") {
    return value >= 1 ? "是" : "否";
  }
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
  return labelCaseType(raw);
}

export function labelGate(gateName?: string | null): string {
  const raw = normalize(gateName);
  if (!raw) {
    return "-";
  }
  const label = GATE_LABELS[raw] || raw;
  return label
    .replace(/_/g, " ")
    .replace(/\bqa\b/gi, "QA")
    .replace(/\brag\b/gi, "RAG")
    .replace(/\bkb\b/gi, "KB");
}

export function formatGate(gateName?: string | null): string {
  const raw = normalize(gateName);
  return labelGate(raw);
}

export function labelTraceStep(stepType?: string | null): string {
  const raw = normalize(stepType);
  return TRACE_STEP_LABELS[raw] || raw || "链路步骤";
}

export function labelTraceAttribute(attributeName?: string | null): string {
  const raw = normalize(attributeName);
  if (!raw) {
    return "-";
  }
  return TRACE_ATTRIBUTE_LABELS[raw] || raw;
}
