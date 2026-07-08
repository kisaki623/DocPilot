"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "@/lib/auth";
import {
  getQualityRunDetail,
  getQualityTrendSummary,
  listQualityEvalCases,
  listQualityRuns,
  type QualityEvalCaseCatalogItem,
  type QualityEvalCaseResultDetail,
  type QualityGateSummary,
  type QualityParserQualitySummary,
  type QualityRunDetail,
  type QualityRunSummary,
  type QualityTokenUsageSummary,
  type QualityTraceReference,
  type QualityTrendSummary,
} from "@/lib/quality-api";
import {
  formatBucketList,
  formatCaseType,
  formatFlagKey,
  formatFlagList,
  formatGate,
  formatMetricKey,
  formatMetricList,
  formatMetricValue,
  formatQualityBoolean,
  formatStatus,
  labelBucket,
} from "@/lib/quality-labels";

const RUN_STATUS_FILTERS = [
  { value: "ALL", label: "全部" },
  { value: "PASS", label: "通过" },
  { value: "REVIEW", label: "需复查 / 阻塞" },
  { value: "FAILED", label: "失败" },
];
const DETAIL_SECTIONS = [
  { id: "summary", label: "摘要" },
  { id: "gates", label: "门禁" },
  { id: "failures", label: "待处理" },
  { id: "trace", label: "链路" },
  { id: "eval", label: "评测" },
  { id: "rag", label: "RAG" },
  { id: "memory", label: "记忆" },
  { id: "tools", label: "工具调用" },
  { id: "artifacts", label: "Artifact" },
] as const;
const SIGNAL_PRIORITY = [
  "casePassRate",
  "distractorCitationFreeCount",
  "answerFaithfulnessPassCount",
  "citationPhraseSupportPassCount",
  "retrieveHits",
  "qaCitations",
  "distractorCitationCount",
  "targetCitationCovered",
  "noEvidenceCorrect",
  "expectedEvidenceSupported",
  "traceRagTriggered",
  "traceRagRequired",
  "expectedDecisionMatched",
];
const TRIAGE_STATUS_OPTIONS = ["ALL", "FAILED", "REVIEW", "BLOCKED", "PASS"];
const TRIAGE_BUCKET_CATEGORIES = [
  "RAG_RETRIEVAL_MISS",
  "CITATION_UNSUPPORTED",
  "DISTRACTOR_CITATION",
  "NO_EVIDENCE_FALSE_POSITIVE",
  "MEMORY_CONFLICT",
  "TOOL_FAILURE",
  "AGENT_ROUTING_MISMATCH",
  "PERMISSION_REGRESSION",
  "FRONTEND_UX",
  "ENV_BLOCKED",
  "OTHER",
];

interface TriageFilters {
  status: string;
  bucketCategory: string;
  gateName: string;
  caseType: string;
}

interface TriageBucketSummary {
  category: string;
  count: number;
  failedCount: number;
  reviewCount: number;
  examples: string[];
}

interface RunComparisonSummary {
  statusChange: string;
  gateDelta: number;
  failedGateDelta: number;
  reviewGateDelta: number;
  tokenDelta: number | null;
  casePassRateDelta: number | null;
  newFailureBuckets: string[];
  resolvedFailureBuckets: string[];
  changedGateStatuses: string[];
  changedCaseStatuses: string[];
}

interface OperationalMetricSummary {
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  estimatedCost: number | null;
  modelCallCount: number | null;
  toolCallCount: number | null;
  latencyMs: number | null;
  durationMs: number | null;
  retryCount: number | null;
}

type DetailSectionId = (typeof DETAIL_SECTIONS)[number]["id"];
type DiagnosticTone = "success" | "warning" | "danger" | "neutral";

interface DiagnosticItem {
  label: string;
  value: string;
  helper: string;
  tone?: DiagnosticTone;
  action?: string;
  priority?: string;
}

interface BucketDiagnostic {
  bucket: string;
  label: string;
  count: number;
  module: "RAG" | "Citation" | "Agent" | "Tool" | "Memory" | "Security" | "Env" | "Unknown";
  description: string;
  action: string;
  tone: DiagnosticTone;
}

interface OverviewDiagnostics {
  totalRuns: number;
  passRuns: number;
  reviewRuns: number;
  failedRuns: number;
  passRate: number | null;
  reviewRate: number | null;
  failRate: number | null;
  coreFlowFailRate: number | null;
  securityGateFailRate: number | null;
  avgLatencyMs: number | null;
  p95LatencyMs: number | null;
  totalTokens: number | null;
  avgTokens: number | null;
  avgEstimatedCost: number | null;
  costPerSuccessfulRun: number | null;
  topFailureBuckets: BucketDiagnostic[];
  topReviewBuckets: BucketDiagnostic[];
}

interface RunDiagnostics {
  rag: DiagnosticItem[];
  memory: DiagnosticItem[];
  tools: DiagnosticItem[];
  eval: DiagnosticItem[];
  topFailureBuckets: BucketDiagnostic[];
  topReviewBuckets: BucketDiagnostic[];
  newFailureBuckets: BucketDiagnostic[];
  recoveredFailureBuckets: BucketDiagnostic[];
}

const DEFAULT_TRIAGE_FILTERS: TriageFilters = {
  status: "ALL",
  bucketCategory: "ALL",
  gateName: "ALL",
  caseType: "ALL",
};

function statusBadge(status?: string): string {
  if (status === "PASS" || status === "SUCCESS") {
    return "dp-badge dp-badge-success";
  }
  if (status === "REVIEW" || status === "BLOCKED") {
    return "dp-badge dp-badge-warning";
  }
  if (status?.startsWith("FAILED")) {
    return "dp-badge dp-badge-danger";
  }
  return "dp-badge dp-badge-info";
}

function normalizeTriageBucket(value?: string): string {
  const lower = (value || "").toLowerCase().replace(/[\s_-]+/g, "");
  if (!lower) {
    return "OTHER";
  }
  if (lower.includes("distractor")) {
    return "DISTRACTOR_CITATION";
  }
  if (lower.includes("noevidence") || lower.includes("evidencefalse")) {
    return "NO_EVIDENCE_FALSE_POSITIVE";
  }
  if (
    lower.includes("citation") ||
    lower.includes("quote") ||
    lower.includes("unsupported") ||
    lower.includes("grounding") ||
    lower.includes("support")
  ) {
    return "CITATION_UNSUPPORTED";
  }
  if (
    lower.includes("chunk") ||
    lower.includes("embedding") ||
    lower.includes("vector") ||
    lower.includes("qdrant") ||
    lower.includes("index") ||
    lower.includes("retrieval") ||
    lower.includes("retrieve") ||
    lower.includes("miss") ||
    lower.includes("recall")
  ) {
    return "RAG_RETRIEVAL_MISS";
  }
  if (lower.includes("memory")) {
    return "MEMORY_CONFLICT";
  }
  if (
    lower.includes("expecteddecision") ||
    lower.includes("decisionmismatch") ||
    lower.includes("selector") ||
    lower.includes("searchoverrouting") ||
    lower.includes("answeroverrouting") ||
    lower.includes("routing")
  ) {
    return "AGENT_ROUTING_MISMATCH";
  }
  if (lower.includes("tool")) {
    return "TOOL_FAILURE";
  }
  if (
    lower.includes("permission") ||
    lower.includes("forbidden") ||
    lower.includes("unauthorized") ||
    lower.includes("scope") ||
    lower.includes("security") ||
    lower.includes("redaction") ||
    lower.includes("auth")
  ) {
    return "PERMISSION_REGRESSION";
  }
  if (
    lower.includes("frontend") ||
    lower.includes("consoleerror") ||
    lower.includes("route") ||
    lower.includes("overflow") ||
    lower.includes("ui") ||
    lower.includes("ux")
  ) {
    return "FRONTEND_UX";
  }
  if (
    lower.includes("blocked") ||
    lower.includes("timeout") ||
    lower.includes("health") ||
    lower.includes("tunnel") ||
    lower.includes("mysql") ||
    lower.includes("redis") ||
    lower.includes("minio") ||
    lower.includes("rocketmq") ||
    lower.includes("env") ||
    lower.includes("parsefailed") ||
    lower.includes("artifactparsefailed")
  ) {
    return "ENV_BLOCKED";
  }
  return "OTHER";
}

function getGateStatus(gate: QualityGateSummary): string {
  return gate.status || (gate.passed === false ? "FAILED" : "PASS");
}

function statusMatches(status: string | undefined, filter: string): boolean {
  if (filter === "ALL") {
    return true;
  }
  const normalized = status || "";
  if (filter === "FAILED") {
    return normalized.startsWith("FAILED") || normalized === "FAIL";
  }
  if (filter === "PASS") {
    return normalized === "PASS" || normalized === "SUCCESS";
  }
  return normalized === filter;
}

function isFailedStatus(status?: string | null): boolean {
  const normalized = status || "";
  return normalized.startsWith("FAILED") || normalized === "FAIL";
}

function isReviewStatus(status?: string | null): boolean {
  return status === "REVIEW" || status === "BLOCKED";
}

function isPassStatus(status?: string | null): boolean {
  return status === "PASS" || status === "SUCCESS";
}

function runStatusMatches(status: string | undefined, filter: string): boolean {
  if (filter === "ALL") {
    return true;
  }
  if (filter === "FAILED") {
    return isFailedStatus(status);
  }
  if (filter === "REVIEW") {
    return isReviewStatus(status);
  }
  if (filter === "PASS") {
    return isPassStatus(status);
  }
  return status === filter;
}

function bucketCategoryMatches(
  buckets: string[] | undefined,
  filter: string
): boolean {
  if (filter === "ALL") {
    return true;
  }
  return (buckets || []).some((bucket) => normalizeTriageBucket(bucket) === filter);
}

function uniqueSorted(values: Array<string | undefined | null>): string[] {
  return Array.from(
    new Set(values.filter((value): value is string => Boolean(value)))
  ).sort((left, right) => left.localeCompare(right));
}

function formatDateTime(input?: string): string {
  if (!input) {
    return "-";
  }
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function formatNumber(value?: number | null): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  if (Math.abs(value) < 1 && value !== 0) {
    return value.toFixed(4);
  }
  return new Intl.NumberFormat("zh-CN").format(value);
}

function summarizeBuckets(values?: string[]): string {
  return formatBucketList(values, 4);
}

function summarizeTextList(values?: string[]): string {
  if (!values || values.length === 0) {
    return "-";
  }
  return values.slice(0, 4).join(" / ");
}

function tokenUsageTotalOrNull(runs: QualityRunSummary[]): number | null {
  const values = runs
    .map((item) => item.tokenUsage?.totalTokens)
    .filter((value): value is number => typeof value === "number" && !Number.isNaN(value));
  if (values.length === 0) {
    return null;
  }
  return values.reduce((sum, value) => sum + value, 0);
}

function tokenUsageValue(tokenUsage?: QualityTokenUsageSummary): number | null {
  return typeof tokenUsage?.totalTokens === "number" ? tokenUsage.totalTokens : null;
}

function formatTokenUsage(tokenUsage?: QualityTokenUsageSummary): string {
  if (!tokenUsage) {
    return "暂无统计";
  }
  const parts = [
    ["提示词 token 数", tokenUsage.promptTokens],
    ["回答 token 数", tokenUsage.completionTokens],
    ["总 token 数", tokenUsage.totalTokens],
  ]
    .filter(([, value]) => typeof value === "number")
    .map(([label, value]) => `${label}: ${formatNumber(value as number)}`);
  if (typeof tokenUsage.estimatedCost === "number") {
    parts.push(`估算成本: ${formatNumber(tokenUsage.estimatedCost)}`);
  }
  return parts.length === 0 ? "暂无统计" : parts.join(" / ");
}

function formatRate(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return "暂无样本";
  }
  return `${(value * 100).toFixed(1)}%`;
}

function formatRateWithSample(value: number | null, numerator: number, denominator: number): string {
  if (value === null || denominator <= 0) {
    return "暂无样本";
  }
  return `${formatRate(value)} (${numerator} / ${denominator})`;
}

function formatNullableStat(value: number | null | undefined, emptyText = "暂无统计"): string {
  return typeof value === "number" && !Number.isNaN(value) ? formatNumber(value) : emptyText;
}

function formatCost(value: number | null): string {
  if (value === null || Number.isNaN(value)) {
    return "暂无样本";
  }
  return value < 1 ? value.toFixed(4) : formatNumber(value);
}

function ratio(numerator: number, denominator: number): number | null {
  return denominator <= 0 ? null : numerator / denominator;
}

function average(values: Array<number | null | undefined>): number | null {
  const resolved = values.filter((value): value is number =>
    typeof value === "number" && !Number.isNaN(value)
  );
  if (resolved.length === 0) {
    return null;
  }
  return resolved.reduce((sum, value) => sum + value, 0) / resolved.length;
}

function percentile(values: Array<number | null | undefined>, p: number): number | null {
  const resolved = values
    .filter((value): value is number => typeof value === "number" && !Number.isNaN(value))
    .sort((left, right) => left - right);
  if (resolved.length === 0) {
    return null;
  }
  const index = Math.min(
    resolved.length - 1,
    Math.max(0, Math.ceil((p / 100) * resolved.length) - 1)
  );
  return resolved[index];
}

function diagnosticToneForRate(
  value: number | null,
  goodAtLeast: number,
  warnAtLeast: number
): DiagnosticTone {
  if (value === null) {
    return "neutral";
  }
  if (value >= goodAtLeast) {
    return "success";
  }
  if (value >= warnAtLeast) {
    return "warning";
  }
  return "danger";
}

function diagnosticToneForBadRate(
  value: number | null,
  warningAtLeast: number,
  dangerAtLeast: number
): DiagnosticTone {
  if (value === null) {
    return "neutral";
  }
  if (value >= dangerAtLeast) {
    return "danger";
  }
  if (value >= warningAtLeast) {
    return "warning";
  }
  return "success";
}

function runHealthMessage(run?: QualityRunSummary): string {
  if (!run) {
    return "还没有加载质量运行记录。";
  }
  if (isFailedStatus(run.status)) {
    return "最近一次运行失败，优先处理失败门禁和待处理项。";
  }
  if (isReviewStatus(run.status)) {
    return "最近一次运行需要复查，请先看待处理项和链路定位。";
  }
  if (isPassStatus(run.status)) {
    return "最近一次运行通过，可以抽查门禁和 Trace 摘要。";
  }
  return "最近一次运行状态不明确，请查看详情。";
}

function bucketAction(bucket: string): string {
  const category = normalizeTriageBucket(bucket);
  switch (category) {
    case "RAG_RETRIEVAL_MISS":
      return "优先检查 chunk 质量、embedding 配置、query rewrite 和检索阈值。";
    case "CITATION_UNSUPPORTED":
      return "检查 citation selector、grounding gate 和回答引用组装。";
    case "DISTRACTOR_CITATION":
      return "检查 rerank、干扰文档裁剪和多文档 coverage 策略。";
    case "NO_EVIDENCE_FALSE_POSITIVE":
      return "检查 no-evidence threshold、near-miss case 和 support gate。";
    case "MEMORY_CONFLICT":
      return "检查长期记忆去重、冲突治理和 RAG evidence 分层。";
    case "AGENT_ROUTING_MISMATCH":
      return "检查 DocumentToolSelector、LLM selector prompt 和 search / answer 意图评测用例。";
    case "TOOL_FAILURE":
      return "检查工具参数校验、超时、重试和 fallback 路径。";
    case "PERMISSION_REGRESSION":
      return "立即复查 scope guard、用户归属校验和前端权限提示。";
    case "FRONTEND_UX":
      return "用 Playwright 复现页面路径，检查 console error 和移动端溢出。";
    case "ENV_BLOCKED":
      return "先确认 tunnel、backend health、artifact parse 和本地依赖。";
    default:
      return "查看关联 gate、eval case 和 Trace，再补充更细失败桶。";
  }
}

function bucketDescription(bucket: string): string {
  const category = normalizeTriageBucket(bucket);
  switch (category) {
    case "RAG_RETRIEVAL_MISS":
      return "检索、切片、索引或召回覆盖不足，可能导致有答案却无证据。";
    case "CITATION_UNSUPPORTED":
      return "回答引用与证据支撑关系需要复核，属于可信引用风险。";
    case "DISTRACTOR_CITATION":
      return "答案挂载了干扰文档或低相关引用，影响 grounded QA 可信度。";
    case "NO_EVIDENCE_FALSE_POSITIVE":
      return "无证据判断边界异常，可能误答或误拒答。";
    case "MEMORY_CONFLICT":
      return "长期记忆命中、去重、冲突或治理链路需要复查。";
    case "AGENT_ROUTING_MISMATCH":
      return "Agent 工具选择或意图路由与预期不一致，可能导致检索请求被当成问答，或问答请求被降级成只检索。";
    case "TOOL_FAILURE":
      return "工具选择、参数、超时、重试或 fallback 存在风险。";
    case "PERMISSION_REGRESSION":
      return "权限隔离、脱敏或访问控制相关风险，应优先确认。";
    case "FRONTEND_UX":
      return "前端路径、控制台错误或移动端展示影响真实排查体验。";
    case "ENV_BLOCKED":
      return "本地依赖、tunnel、健康检查或 artifact 解析阻塞了质量判断。";
    default:
      return "当前只能归为 Unknown，需要补充 bucket 映射规则或上游更稳定的失败类型。";
  }
}

function bucketModule(bucket: string): BucketDiagnostic["module"] {
  const category = normalizeTriageBucket(bucket);
  switch (category) {
    case "RAG_RETRIEVAL_MISS":
    case "NO_EVIDENCE_FALSE_POSITIVE":
      return "RAG";
    case "CITATION_UNSUPPORTED":
    case "DISTRACTOR_CITATION":
      return "Citation";
    case "AGENT_ROUTING_MISMATCH":
      return "Agent";
    case "TOOL_FAILURE":
      return "Tool";
    case "MEMORY_CONFLICT":
      return "Memory";
    case "PERMISSION_REGRESSION":
      return "Security";
    case "FRONTEND_UX":
    case "ENV_BLOCKED":
      return "Env";
    default:
      return "Unknown";
  }
}

function bucketTone(bucket: string): DiagnosticTone {
  const category = normalizeTriageBucket(bucket);
  if (category === "PERMISSION_REGRESSION") {
    return "danger";
  }
  if (category === "ENV_BLOCKED" || category === "FRONTEND_UX") {
    return "warning";
  }
  if (category === "OTHER") {
    return "neutral";
  }
  return "danger";
}

function topBucketDiagnostics(values: string[], limit = 5): BucketDiagnostic[] {
  const counts = new Map<string, number>();
  values.forEach((bucket) => {
    const normalized = normalizeTriageBucket(bucket);
    counts.set(normalized, (counts.get(normalized) || 0) + 1);
  });
  return Array.from(counts.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, limit)
    .map(([bucket, count]) => ({
      bucket,
      label: labelBucket(bucket),
      count,
      module: bucketModule(bucket),
      description: bucketDescription(bucket),
      action: bucketAction(bucket),
      tone: bucketTone(bucket),
    }));
}

function compactMetrics(metrics: Record<string, number>): string {
  return formatMetricList(metrics, 4);
}

function signalEntries(
  metrics: Record<string, number>,
  flags: Record<string, boolean>,
  limit = 8
): Array<{ key: string; label: string; value: string; tone?: "success" | "warning" }> {
  const metricEntries = Object.entries(metrics || {}).map(([key, value]) => ({
    key,
    label: formatMetricKey(key),
    value: formatMetricValue(key, value),
  }));
  const flagEntries = Object.entries(flags || {}).map(([key, value]) => ({
    key,
    label: formatFlagKey(key),
    value: formatQualityBoolean(value),
    tone: value ? "success" as const : "warning" as const,
  }));
  return [...metricEntries, ...flagEntries]
    .sort((left, right) => signalPriority(left.key) - signalPriority(right.key))
    .slice(0, limit);
}

function signalPriority(key: string): number {
  const index = SIGNAL_PRIORITY.indexOf(key);
  return index >= 0 ? index : SIGNAL_PRIORITY.length;
}

function compactFlags(flags: Record<string, boolean>): string {
  return formatFlagList(flags, 4);
}

function formatDelta(value: number | null, fractionDigits = 0): string {
  if (value === null || Number.isNaN(value)) {
    return "-";
  }
  const prefix = value > 0 ? "+" : "";
  return `${prefix}${value.toFixed(fractionDigits)}`;
}

function uniqueDiff(left: string[], right: string[]): string[] {
  const rightSet = new Set(right);
  return uniqueSorted(left.filter((item) => !rightSet.has(item)));
}

function sumMetric(detail: QualityRunDetail, names: string[]): number | null {
  let sum = 0;
  let found = false;
  const normalizedNames = new Set(names.map((name) => name.toLowerCase()));
  [...detail.gates, ...detail.evalCases].forEach((item) => {
    Object.entries(item.metrics || {}).forEach(([key, value]) => {
      if (normalizedNames.has(key.toLowerCase()) && typeof value === "number") {
        sum += value;
        found = true;
      }
    });
  });
  return found ? sum : null;
}

function operationalSummary(detail: QualityRunDetail): OperationalMetricSummary {
  const tokenUsage = detail.summary.tokenUsage;
  return {
    promptTokens:
      typeof tokenUsage?.promptTokens === "number" ? tokenUsage.promptTokens : null,
    completionTokens:
      typeof tokenUsage?.completionTokens === "number" ? tokenUsage.completionTokens : null,
    totalTokens:
      typeof tokenUsage?.totalTokens === "number" ? tokenUsage.totalTokens : null,
    estimatedCost:
      typeof tokenUsage?.estimatedCost === "number" ? tokenUsage.estimatedCost : null,
    modelCallCount: sumMetric(detail, ["modelCallCount"]),
    toolCallCount: sumMetric(detail, ["toolCallCount"]),
    latencyMs: sumMetric(detail, ["latencyMs"]),
    durationMs: sumMetric(detail, ["durationMs"]),
    retryCount: sumMetric(detail, ["retryCount"]),
  };
}

function metricValues(detail: QualityRunDetail, names: string[]): number[] {
  const normalizedNames = new Set(names.map((name) => name.toLowerCase()));
  const values: number[] = [];
  [...detail.gates, ...detail.evalCases].forEach((item) => {
    Object.entries(item.metrics || {}).forEach(([key, value]) => {
      if (normalizedNames.has(key.toLowerCase()) && typeof value === "number") {
        values.push(value);
      }
    });
  });
  return values;
}

function flagValues(detail: QualityRunDetail, names: string[]): boolean[] {
  const normalizedNames = new Set(names.map((name) => name.toLowerCase()));
  const values: boolean[] = [];
  [...detail.gates, ...detail.evalCases].forEach((item) => {
    Object.entries(item.flags || {}).forEach(([key, value]) => {
      if (normalizedNames.has(key.toLowerCase()) && typeof value === "boolean") {
        values.push(value);
      }
    });
  });
  return values;
}

function allBuckets(detail: QualityRunDetail, type: "failure" | "review" | "all" = "all"): string[] {
  const buckets: string[] = [];
  if (type !== "review") {
    buckets.push(...detail.summary.failureBuckets);
    detail.gates.forEach((gate) => buckets.push(...gate.failureBuckets));
    detail.evalCases.forEach((item) => buckets.push(...item.failureBuckets));
    detail.traceReferences.forEach((reference) => buckets.push(...reference.failureBuckets));
  }
  if (type !== "failure") {
    buckets.push(...detail.summary.reviewBuckets);
    detail.gates.forEach((gate) => buckets.push(...gate.reviewBuckets));
    detail.evalCases.forEach((item) => buckets.push(...item.reviewBuckets));
    detail.traceReferences.forEach((reference) => buckets.push(...reference.reviewBuckets));
  }
  return buckets;
}

function buildOverviewDiagnostics(
  runs: QualityRunSummary[],
  trend: QualityTrendSummary | null
): OverviewDiagnostics {
  const totalRuns = runs.length;
  const passRuns = runs.filter((run) => isPassStatus(run.status)).length;
  const reviewRuns = runs.filter((run) => isReviewStatus(run.status)).length;
  const failedRuns = runs.filter((run) => isFailedStatus(run.status)).length;
  const coreFlowFailedRuns = runs.filter((run) => run.status === "FAILED_CORE_FLOW").length;
  const securityFailedRuns = runs.filter((run) => run.status === "FAILED_SECURITY_GATE").length;
  const tokenValues = runs
    .map((run) => run.tokenUsage?.totalTokens)
    .filter((value): value is number => typeof value === "number");
  const costValues = runs
    .map((run) => run.tokenUsage?.estimatedCost)
    .filter((value): value is number => typeof value === "number");
  const latencyValues = trend?.points?.map((point) => point.latencyMs) || [];
  const failureBuckets = [
    ...runs.flatMap((run) => run.failureBuckets),
    ...Object.entries(trend?.failureBucketCounts || {}).flatMap(([bucket, count]) =>
      Array.from({ length: count }, () => bucket)
    ),
  ];
  const reviewBuckets = [
    ...runs.flatMap((run) => run.reviewBuckets),
    ...Object.entries(trend?.reviewBucketCounts || {}).flatMap(([bucket, count]) =>
      Array.from({ length: count }, () => bucket)
    ),
  ];
  const totalCost = costValues.reduce((sum, value) => sum + value, 0);

  return {
    totalRuns,
    passRuns,
    reviewRuns,
    failedRuns,
    passRate: ratio(passRuns, totalRuns),
    reviewRate: ratio(reviewRuns, totalRuns),
    failRate: ratio(failedRuns, totalRuns),
    coreFlowFailRate: ratio(coreFlowFailedRuns, totalRuns),
    securityGateFailRate: ratio(securityFailedRuns, totalRuns),
    avgLatencyMs: trend?.averageLatencyMs ?? average(latencyValues),
    p95LatencyMs: percentile(latencyValues, 95),
    totalTokens:
      tokenValues.length === 0
        ? trend?.totalTokens ?? null
        : tokenValues.reduce((sum, value) => sum + value, 0),
    avgTokens: average(tokenValues),
    avgEstimatedCost: average(costValues),
    costPerSuccessfulRun: passRuns > 0 && costValues.length > 0 ? totalCost / passRuns : null,
    topFailureBuckets: topBucketDiagnostics(failureBuckets),
    topReviewBuckets: topBucketDiagnostics(reviewBuckets),
  };
}

function buildRunDiagnostics(
  detail: QualityRunDetail,
  compareDetail: QualityRunDetail | null,
  evalCatalog: QualityEvalCaseCatalogItem[]
): RunDiagnostics {
  const retrieveHits = metricValues(detail, ["retrieveHits", "retrievalHitCount"]);
  const qaCitations = metricValues(detail, ["qaCitations", "citationCount"]);
  const evidenceCounts = metricValues(detail, ["evidenceCount"]);
  const noEvidenceFlags = flagValues(detail, ["noEvidenceCorrect"]);
  const citationSupportFlags = flagValues(detail, [
    "targetCitationCovered",
    "expectedEvidenceSupported",
    "citationMarkerPresent",
    "citationPhraseSupport",
  ]);
  const distractorCounts = metricValues(detail, ["distractorCitationCount"]);
  const toolCallCounts = metricValues(detail, ["toolCallCount"]);
  const toolFailureBuckets = allBuckets(detail).filter(
    (bucket) => normalizeTriageBucket(bucket) === "TOOL_FAILURE"
  );
  const memoryCounts = metricValues(detail, ["memoryCount"]);
  const memoryConflictBuckets = allBuckets(detail).filter(
    (bucket) => normalizeTriageBucket(bucket) === "MEMORY_CONFLICT"
  );
  const traceSteps = detail.traceReferences.flatMap((reference) => reference.steps || []);
  const failedTraceSteps = traceSteps.filter((step) => isFailedStatus(step.status));
  const failedOrReviewCases = detail.evalCases.filter(
    (item) => isFailedStatus(item.status) || isReviewStatus(item.status)
  );
  const linkedFailedOrReviewCases = failedOrReviewCases.filter(
    (item) => Boolean(item.traceId || item.agentRunId)
  );
  const passByTag = passRateByTag(detail, evalCatalog);
  const previousFailures = compareDetail ? allBuckets(compareDetail, "failure") : [];
  const currentFailures = allBuckets(detail, "failure");
  const documentCoverage = detail.diagnostics?.documentCoverage;
  const toolQuality = detail.diagnostics?.toolQuality;
  const memoryQuality = detail.diagnostics?.memoryQuality;
  const documentCoverageText =
    typeof documentCoverage?.documentCount === "number" && documentCoverage.documentCount > 0
      ? `${formatNumber(documentCoverage.coveredDocumentCount)}/${formatNumber(documentCoverage.documentCount)}，未命中 ${formatNumber(documentCoverage.zeroHitDocumentCount)}`
      : "暂无安全摘要";
  const memoryQualityText =
    typeof memoryQuality?.memoryHitCount === "number" || typeof memoryQuality?.ragEvidenceCount === "number"
      ? `记忆 ${formatNumber(memoryQuality?.memoryHitCount ?? null)} / RAG 证据 ${formatNumber(memoryQuality?.ragEvidenceCount ?? null)}`
      : "暂无安全摘要";
  const toolArgsReviewText =
    typeof toolQuality?.toolArgsReviewCount === "number"
      ? formatNumber(toolQuality.toolArgsReviewCount)
      : "暂无安全摘要";

  return {
    rag: [
      {
        label: "检索命中率",
        value: formatRate(ratio(retrieveHits.filter((value) => value > 0).length, retrieveHits.length)),
        helper: "有 retrieveHits 样本时，命中数大于 0 的比例。",
        tone: diagnosticToneForRate(
          ratio(retrieveHits.filter((value) => value > 0).length, retrieveHits.length),
          0.9,
          0.75
        ),
        action: "偏低时优先检查 chunk 质量、embedding、query rewrite 和检索阈值。",
      },
      {
        label: "引用覆盖率",
        value: formatRate(ratio(qaCitations.filter((value) => value > 0).length, qaCitations.length)),
        helper: "有 qaCitations 样本时，引用数大于 0 的比例。",
        tone: diagnosticToneForRate(
          ratio(qaCitations.filter((value) => value > 0).length, qaCitations.length),
          0.9,
          0.75
        ),
        action: "偏低时检查 QA citation builder 和 quote-first citation 输出。",
      },
      {
        label: "证据覆盖率",
        value: formatRate(ratio(evidenceCounts.filter((value) => value > 0).length, evidenceCounts.length)),
        helper: "有 evidenceCount 样本时，证据数大于 0 的比例。",
        tone: diagnosticToneForRate(
          ratio(evidenceCounts.filter((value) => value > 0).length, evidenceCounts.length),
          0.9,
          0.75
        ),
        action: "偏低时检查 Conversation Trace 是否写入 RAG evidence。",
      },
      {
        label: "引用支撑率",
        value: formatRate(ratio(citationSupportFlags.filter(Boolean).length, citationSupportFlags.length)),
        helper: "基于 citation / evidence 支撑类布尔门禁的通过比例。",
        tone: diagnosticToneForRate(
          ratio(citationSupportFlags.filter(Boolean).length, citationSupportFlags.length),
          0.9,
          0.75
        ),
        action: "偏低时检查 answer grounding 和 citation selector。",
      },
      {
        label: "无证据拒答正确率",
        value: formatRate(ratio(noEvidenceFlags.filter(Boolean).length, noEvidenceFlags.length)),
        helper: "基于 noEvidenceCorrect 布尔门禁。",
        tone: diagnosticToneForRate(
          ratio(noEvidenceFlags.filter(Boolean).length, noEvidenceFlags.length),
          0.95,
          0.8
        ),
        action: "偏低时检查 no-evidence threshold 和 hard negative case。",
      },
      {
        label: "平均检索 chunk",
        value: formatNumber(average(retrieveHits)),
        helper: "基于 retrieveHits 的平均值。",
        tone: "neutral",
        action: "过低可能漏召回，过高可能带来干扰 citation。",
      },
      {
        label: "干扰引用通过率",
        value: formatRate(ratio(distractorCounts.filter((value) => value === 0).length, distractorCounts.length)),
        helper: "有 distractorCitationCount 样本时，干扰引用为 0 的比例。",
        tone: diagnosticToneForRate(
          ratio(distractorCounts.filter((value) => value === 0).length, distractorCounts.length),
          0.95,
          0.8
        ),
        action: "偏低时检查 rerank、summary citation pruning 和多文档 coverage。",
      },
      {
        label: "命中文档分布",
        value: documentCoverageText,
        helper: "后端只返回 documentHitCounts 的覆盖数量摘要，不透传文档 ID 或原始 map。",
        tone: "neutral",
        action: "未命中文档偏多时，检查多文档 coverage、query rewrite 和 summary backfill。",
      },
    ],
    memory: [
      {
        label: "记忆触发率",
        value: formatRate(ratio(memoryCounts.filter((value) => value > 0).length, memoryCounts.length)),
        helper: "有 memoryCount 样本时，记忆数大于 0 的比例。",
        tone: diagnosticToneForRate(
          ratio(memoryCounts.filter((value) => value > 0).length, memoryCounts.length),
          0.8,
          0.5
        ),
        action: "偏低时检查 ACTIVE memory、ContextAssembly 和会话模式。",
      },
      {
        label: "记忆证据覆盖",
        value: formatRate(ratio(
          memoryCounts.filter((value, index) => value > 0 && (evidenceCounts[index] || 0) > 0).length,
          Math.max(memoryCounts.length, evidenceCounts.length)
        )),
        helper: "近似衡量 memory 与 evidence 同时进入 trace 的比例。",
        tone: "neutral",
        action: "偏低时检查 memory 与 RAG evidence 是否同时进入 Context Trace。",
      },
      {
        label: "记忆治理复查率",
        value: formatRate(ratio(memoryConflictBuckets.length, Math.max(1, allBuckets(detail).length))),
        helper: "memory conflict bucket 在全部失败/复查 bucket 中的占比。",
        tone: diagnosticToneForBadRate(
          ratio(memoryConflictBuckets.length, Math.max(1, allBuckets(detail).length)),
          0.1,
          0.25
        ),
        action: "偏高时检查记忆去重、冲突合并和 suggestion accept 门禁。",
      },
      {
        label: "记忆命中摘要",
        value: memoryQualityText,
        helper: "基于 memoryCount 与 contextSourceCounts 的安全数值摘要，不展示记忆内容。",
        tone: "neutral",
        action: "记忆命中低时检查 ACTIVE memory、ContextAssembly 和会话绑定模式。",
      },
    ],
    tools: [
      {
        label: "工具触发率",
        value: formatRate(ratio(toolCallCounts.filter((value) => value > 0).length, toolCallCounts.length)),
        helper: "有 toolCallCount 样本时，工具调用数大于 0 的比例。",
        tone: "neutral",
        action: "过低可能没触发工具，过高可能存在工具循环或选择过度。",
      },
      {
        label: "工具失败率",
        value: formatRate(ratio(toolFailureBuckets.length, Math.max(1, allBuckets(detail).length))),
        helper: "tool failure bucket 在全部失败/复查 bucket 中的占比。",
        tone: diagnosticToneForBadRate(
          ratio(toolFailureBuckets.length, Math.max(1, allBuckets(detail).length)),
          0.1,
          0.25
        ),
        action: "偏高时检查工具参数校验、超时、重试和 fallback。",
      },
      {
        label: "本次工具调用数",
        value: formatNumber(sumMetric(detail, ["toolCallCount"])),
        helper: "当前 run 内 toolCallCount 的聚合值。",
        tone: "neutral",
      },
      {
        label: "Agent 步骤失败率",
        value: formatRate(ratio(failedTraceSteps.length, traceSteps.length)),
        helper: "基于脱敏 trace step status。",
        tone: diagnosticToneForBadRate(ratio(failedTraceSteps.length, traceSteps.length), 0.05, 0.2),
        action: "偏高时从 Trace tab 查看失败步骤和 failure bucket。",
      },
      {
        label: "最大链路步数",
        value: formatNumber(Math.max(0, ...detail.traceReferences.map((reference) => reference.steps.length))),
        helper: "用于观察是否出现异常长链路。",
        tone: "neutral",
      },
      {
        label: "工具参数复查",
        value: toolArgsReviewText,
        helper: `工具调用数 ${formatNumber(toolQuality?.toolCallCount ?? null)}，只统计参数相关复查 bucket。`,
        tone: "neutral",
        action: "参数复查偏高时检查 tool schema、参数校验和 fallback 文案。",
      },
    ],
    eval: [
      {
        label: "评测用例数",
        value: formatNumber(detail.evalCases.length),
        helper: "当前 run 解析到的 eval case 数。",
        tone: "neutral",
      },
      {
        label: "评测通过率",
        value: formatRate(ratio(detail.evalCases.filter((item) => isPassStatus(item.status)).length, detail.evalCases.length)),
        helper: "PASS / SUCCESS eval case 占比。",
        tone: diagnosticToneForRate(
          ratio(detail.evalCases.filter((item) => isPassStatus(item.status)).length, detail.evalCases.length),
          0.9,
          0.75
        ),
        action: "偏低时先看失败 case 的 bucket 和 Trace 链接覆盖。",
      },
      {
        label: "失败用例数",
        value: formatNumber(detail.evalCases.filter((item) => isFailedStatus(item.status)).length),
        helper: "FAILED eval case 数。",
        tone: detail.evalCases.some((item) => isFailedStatus(item.status)) ? "danger" : "success",
      },
      {
        label: "失败用例 Trace 覆盖",
        value: formatRate(ratio(linkedFailedOrReviewCases.length, failedOrReviewCases.length)),
        helper: "失败/需复查 eval case 中带 traceId 或 agentRunId 的比例。",
        tone: diagnosticToneForRate(
          ratio(linkedFailedOrReviewCases.length, failedOrReviewCases.length),
          0.95,
          0.75
        ),
        action: "偏低时先修 artifact 中 traceId / agentRunId 写入。",
      },
      {
        label: "按标签通过率",
        value: passByTag.length === 0 ? "暂无标签样本" : passByTag.slice(0, 3).join(" / "),
        helper: "基于 eval catalog tags 与当前 caseId 关联。",
        tone: "neutral",
      },
    ],
    topFailureBuckets: topBucketDiagnostics(currentFailures),
    topReviewBuckets: topBucketDiagnostics(allBuckets(detail, "review")),
    newFailureBuckets: topBucketDiagnostics(uniqueDiff(currentFailures, previousFailures)),
    recoveredFailureBuckets: topBucketDiagnostics(uniqueDiff(previousFailures, currentFailures)).map((bucket) => ({
      ...bucket,
      tone: "success",
      action: "该失败类型较对比运行已恢复，可作为修复回归证据。",
    })),
  };
}

function passRateByTag(
  detail: QualityRunDetail,
  evalCatalog: QualityEvalCaseCatalogItem[]
): string[] {
  const tagsByCase = new Map(evalCatalog.map((item) => [item.caseId, item.tags || []]));
  const stats = new Map<string, { pass: number; total: number }>();
  detail.evalCases.forEach((item) => {
    const tags = tagsByCase.get(item.caseId) || [];
    const resolvedTags = tags.length === 0 ? ["未分组"] : tags;
    resolvedTags.forEach((tag) => {
      const current = stats.get(tag) || { pass: 0, total: 0 };
      current.total += 1;
      if (isPassStatus(item.status)) {
        current.pass += 1;
      }
      stats.set(tag, current);
    });
  });
  return Array.from(stats.entries())
    .sort((left, right) => right[1].total - left[1].total)
    .map(([tag, value]) => `${tag}: ${formatRate(ratio(value.pass, value.total))}`);
}

async function copyToClipboard(value: string): Promise<void> {
  if (!value || !navigator.clipboard) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
  } catch {
    // Clipboard permission is best-effort for the internal console locator.
  }
}

export default function QualityPage() {
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [runs, setRuns] = useState<QualityRunSummary[]>([]);
  const [evalCatalog, setEvalCatalog] = useState<QualityEvalCaseCatalogItem[]>([]);
  const [trend, setTrend] = useState<QualityTrendSummary | null>(null);
  const [selectedMarker, setSelectedMarker] = useState("");
  const [compareMarker, setCompareMarker] = useState("");
  const [runStatusFilter, setRunStatusFilter] = useState("ALL");
  const [runSearch, setRunSearch] = useState("");
  const [detail, setDetail] = useState<QualityRunDetail | null>(null);
  const [compareDetail, setCompareDetail] = useState<QualityRunDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [compareLoading, setCompareLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const loadRuns = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setRuns([]);
      setDetail(null);
      setLoading(false);
      setErrorMessage("未检测到登录状态。");
      return;
    }

    setHasToken(true);
    setLoading(true);
    setErrorMessage("");
    try {
      const response = await listQualityRuns(20);
      const nextRuns = response.data || [];
      setRuns(nextRuns);
      setSelectedMarker((current) => current || nextRuns[0]?.marker || "");
      setCompareMarker((current) => current || nextRuns[1]?.marker || "");
      try {
        const trendResponse = await getQualityTrendSummary(20);
        setTrend(trendResponse.data || null);
      } catch {
        setTrend(null);
      }
      try {
        const catalogResponse = await listQualityEvalCases();
        setEvalCatalog(catalogResponse.data || []);
      } catch {
        setEvalCatalog([]);
      }
    } catch (error) {
      setRuns([]);
      setEvalCatalog([]);
      setTrend(null);
      setDetail(null);
      setCompareDetail(null);
      setErrorMessage(
        error instanceof Error ? error.message : "加载质量运行记录失败"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDetail = useCallback(async (marker: string) => {
    if (!marker) {
      setDetail(null);
      return;
    }
    setDetailLoading(true);
    setErrorMessage("");
    try {
      const response = await getQualityRunDetail(marker);
      setDetail(response.data);
    } catch (error) {
      setDetail(null);
      setErrorMessage(
        error instanceof Error ? error.message : "加载质量运行详情失败"
      );
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const loadCompareDetail = useCallback(async (marker: string) => {
    if (!marker) {
      setCompareDetail(null);
      return;
    }
    setCompareLoading(true);
    try {
      const response = await getQualityRunDetail(marker);
      setCompareDetail(response.data);
    } catch {
      setCompareDetail(null);
    } finally {
      setCompareLoading(false);
    }
  }, []);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setRuns([]);
      setDetail(null);
      setLoading(false);
      setErrorMessage("未检测到登录状态。");
      return;
    }

    setHasToken(true);
    const shouldAutoload = new URLSearchParams(window.location.search).get("autoload") === "1";
    if (shouldAutoload) {
      loadRuns();
    } else {
      setLoading(false);
    }
  }, [loadRuns]);

  useEffect(() => {
    if (selectedMarker) {
      loadDetail(selectedMarker);
    }
  }, [loadDetail, selectedMarker]);

  useEffect(() => {
    if (!selectedMarker || compareMarker !== selectedMarker) {
      return;
    }
    const fallback = runs.find((run) => run.marker !== selectedMarker)?.marker || "";
    setCompareMarker(fallback);
  }, [compareMarker, runs, selectedMarker]);

  useEffect(() => {
    if (compareMarker) {
      loadCompareDetail(compareMarker);
    } else {
      setCompareDetail(null);
    }
  }, [compareMarker, loadCompareDetail]);

  const stats = useMemo(() => {
    const pass = runs.filter((item) => item.status === "PASS").length;
    const review = runs.filter(
      (item) => item.status === "REVIEW" || item.status === "BLOCKED"
    ).length;
    const failed = runs.filter((item) => item.status.startsWith("FAILED"))
      .length;
    return {
      total: runs.length,
      pass,
      review,
      failed,
      tokens: tokenUsageTotalOrNull(runs),
    };
  }, [runs]);

  const filteredRuns = useMemo(() => {
    const query = runSearch.trim().toLowerCase();
    return runs.filter((run) => {
      const searchable = [
        run.marker,
        run.source,
        run.artifactName,
        formatStatus(run.status),
      ]
        .join(" ")
        .toLowerCase();
      return (
        runStatusMatches(run.status, runStatusFilter) &&
        (!query || searchable.includes(query))
      );
    });
  }, [runSearch, runStatusFilter, runs]);

  const latestRun = runs[0];
  const overviewDiagnostics = useMemo(
    () => buildOverviewDiagnostics(runs, trend),
    [runs, trend]
  );

  if (hasToken === false) {
    return (
      <main className="dp-page max-w-5xl mx-auto py-8 px-4">
        <section className="dp-hero">
          <p className="dp-eyebrow">Agent Quality Console</p>
          <h1 className="dp-title">内部质量控制台</h1>
          <p className="dp-subtitle">登录后查看质量运行摘要。</p>
          <div className="mt-5">
            <Link href="/login" className="dp-btn dp-btn-primary">
              登录
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <QualityOverviewHeader
        stats={stats}
        diagnostics={overviewDiagnostics}
        latestRun={latestRun}
        loading={loading}
        onRefresh={() => loadRuns()}
      />

      {errorMessage ? (
        <section className="dp-card border-red-200 bg-red-50 text-sm text-red-700">
          {errorMessage}
        </section>
      ) : null}

      <section className="grid gap-4 lg:grid-cols-[minmax(280px,0.85fr)_minmax(0,1.75fr)]">
        <RunListSidebar
          runs={filteredRuns}
          allRunCount={runs.length}
          loading={loading}
          selectedMarker={selectedMarker}
          statusFilter={runStatusFilter}
          search={runSearch}
          onStatusFilterChange={setRunStatusFilter}
          onSearchChange={setRunSearch}
          onSelect={setSelectedMarker}
        />

        <RunDetailPanel
          detail={detail}
          loading={detailLoading}
          runs={runs}
          evalCatalog={evalCatalog}
          trend={trend}
          selectedMarker={selectedMarker}
          compareMarker={compareMarker}
          compareDetail={compareDetail}
          compareLoading={compareLoading}
          onCompareMarkerChange={setCompareMarker}
        />
      </section>
    </main>
  );
}

function QualityOverviewHeader({
  stats,
  diagnostics,
  latestRun,
  loading,
  onRefresh,
}: {
  stats: {
    total: number;
    pass: number;
    review: number;
    failed: number;
    tokens: number | null;
  };
  diagnostics: OverviewDiagnostics;
  latestRun?: QualityRunSummary;
  loading: boolean;
  onRefresh: () => void;
}) {
  const overviewCards: DiagnosticItem[] = [
    {
      label: "通过率",
      value: formatRateWithSample(
        diagnostics.passRate,
        diagnostics.passRuns,
        diagnostics.totalRuns
      ),
      helper: "最近加载运行中 PASS / SUCCESS 的比例，分母为 totalRuns。",
      tone: diagnosticToneForRate(diagnostics.passRate, 0.9, 0.75),
      action: "偏低时先看失败桶 TopN 和最新 run 的待处理项。",
      priority: "Failures / Gates / Trace",
    },
    {
      label: "复查率",
      value: formatRateWithSample(
        diagnostics.reviewRate,
        diagnostics.reviewRuns,
        diagnostics.totalRuns
      ),
      helper: "REVIEW / BLOCKED 运行占比，分母为 totalRuns；复查表示质量风险，不一定阻断核心链路。",
      tone: diagnosticToneForBadRate(diagnostics.reviewRate, 0.15, 0.3),
      action: "偏高时优先补齐 evidence、Trace 和环境稳定性。",
      priority: "Citation / RAG / Eval Scorer",
    },
    {
      label: "失败率",
      value: formatRateWithSample(
        diagnostics.failRate,
        diagnostics.failedRuns,
        diagnostics.totalRuns
      ),
      helper: "FAILED_* 运行占比，分母为 totalRuns。",
      tone: diagnosticToneForBadRate(diagnostics.failRate, 0.05, 0.15),
      action: "偏高时直接进入 Failures，先处理核心链路失败。",
      priority: "Failures / Gates / Trace",
    },
    {
      label: "P95 延迟",
      value: formatNullableStat(diagnostics.p95LatencyMs),
      helper: "基于最近 trend points 的 latencyMs；没有 point 样本时显示暂无统计。",
      tone: diagnostics.p95LatencyMs === null ? "neutral" : diagnostics.p95LatencyMs > 5000 ? "warning" : "success",
      action: "明显升高时检查模型调用、工具调用和重试次数。",
      priority: "LLM / RAG / Tool latency",
    },
    {
      label: "平均 token 数",
      value: formatNullableStat(diagnostics.avgTokens),
      helper: "最近运行中存在 totalTokens 样本时才计算平均值；字段缺失不按 0 处理。",
      tone: "neutral",
      action: "持续升高时检查 prompt 上下文、RAG evidence 和历史消息裁剪。",
      priority: "Context / Prompt / RAG chunks",
    },
    {
      label: "成功运行成本",
      value: formatCost(diagnostics.costPerSuccessfulRun),
      helper: "存在 estimatedCost 样本且有成功运行时才计算；字段缺失显示暂无样本。",
      tone: "neutral",
      action: "升高时优先排查高 token 或重复模型调用路径。",
      priority: "模型调用 / token 用量 / 重试",
    },
  ];

  return (
    <>
      <section className="dp-hero">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <p className="dp-eyebrow">Agent Quality Console</p>
            <h1 className="dp-title">内部质量排查控制台</h1>
            <p className="dp-subtitle max-w-3xl">
              先判断最近运行是否健康，再沿着门禁、失败项和链路定位排查问题。
            </p>
          </div>
          <button
            type="button"
            onClick={onRefresh}
            className="dp-btn dp-btn-secondary px-4 py-2"
            disabled={loading}
          >
            {loading ? "刷新中..." : "刷新"}
          </button>
        </div>
        <div className="mt-5 rounded-lg border border-slate-200 bg-white/70 p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-slate-900">
                {runHealthMessage(latestRun)}
              </p>
              <p className="mt-1 break-words text-xs text-slate-500">
                最近运行: {latestRun?.marker || "-"} /{" "}
                {latestRun ? formatDateTime(latestRun.updatedAt) : "-"}
              </p>
            </div>
            <span className={statusBadge(latestRun?.status)}>
              {latestRun ? formatStatus(latestRun.status) : "未加载"}
            </span>
          </div>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <MetricCard label="运行次数" value={stats.total} />
        <MetricCard label="通过" value={stats.pass} tone="success" />
        <MetricCard label="需复查 / 阻塞" value={stats.review} tone="warning" />
        <MetricCard label="失败" value={stats.failed} tone="danger" />
        <MetricCard label="总 token 数" value={formatNullableStat(stats.tokens)} />
      </section>

      <section className="grid gap-3 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]">
        <div className="dp-card min-w-0">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h2 className="dp-section-title">质量诊断</h2>
              <p className="mt-1 text-xs text-slate-500">
                只展示能指导排查的脱敏比率和成本数值。
              </p>
            </div>
            <span className="dp-badge dp-badge-neutral">P0 派生指标</span>
          </div>
          <DiagnosticGrid items={overviewCards} />
        </div>
        <div className="grid min-w-0 gap-3">
          <BucketActionPanel
            title="失败类型 TopN"
            items={diagnostics.topFailureBuckets}
            emptyText="暂无失败类型。"
          />
          <BucketActionPanel
            title="复查类型 TopN"
            items={diagnostics.topReviewBuckets}
            emptyText="暂无复查类型。"
          />
        </div>
      </section>
    </>
  );
}

function RunListSidebar({
  runs,
  allRunCount,
  loading,
  selectedMarker,
  statusFilter,
  search,
  onStatusFilterChange,
  onSearchChange,
  onSelect,
}: {
  runs: QualityRunSummary[];
  allRunCount: number;
  loading: boolean;
  selectedMarker: string;
  statusFilter: string;
  search: string;
  onStatusFilterChange: (value: string) => void;
  onSearchChange: (value: string) => void;
  onSelect: (marker: string) => void;
}) {
  return (
    <aside className="dp-card min-w-0 self-start lg:sticky lg:top-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="dp-section-title">运行记录</h2>
          <p className="mt-1 text-xs text-slate-500">
            已加载 {allRunCount} 条，当前显示 {runs.length} 条
          </p>
        </div>
      </div>

      <div className="mt-4 grid gap-3">
        <label className="min-w-0">
          <span className="text-xs font-semibold uppercase text-slate-500">
            状态筛选
          </span>
          <select
            value={statusFilter}
            onChange={(event) => onStatusFilterChange(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          >
            {RUN_STATUS_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-0">
          <span className="text-xs font-semibold uppercase text-slate-500">
            搜索 marker / 来源
          </span>
          <input
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="输入 marker、来源或状态"
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          />
        </label>
      </div>

      <div className="mt-4 grid max-h-[620px] gap-2 overflow-y-auto pr-1">
        {loading ? (
          <p className="dp-meta">正在加载运行记录...</p>
        ) : allRunCount === 0 ? (
          <p className="dp-meta">暂无质量运行记录。点击刷新后仍为空时，说明还没有生成脱敏 artifact。</p>
        ) : runs.length === 0 ? (
          <p className="dp-meta">没有匹配的运行记录，请调整筛选或搜索。</p>
        ) : (
          runs.map((run) => (
            <button
              key={`${run.source}-${run.marker}`}
              type="button"
              onClick={() => onSelect(run.marker)}
              className={`w-full min-w-0 rounded-lg border p-3 text-left transition ${
                selectedMarker === run.marker
                  ? "border-blue-300 bg-blue-50"
                  : "border-slate-200 bg-white hover:border-blue-200"
              }`}
            >
              <div className="flex min-w-0 items-start justify-between gap-2">
                <span className="min-w-0 break-words text-sm font-semibold text-slate-900">
                  {run.marker}
                </span>
                <span className={`${statusBadge(run.status)} shrink-0`}>
                  {formatStatus(run.status)}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
                <span>{run.source}</span>
                <span>{formatDateTime(run.updatedAt)}</span>
              </div>
              <div className="mt-2 text-xs text-slate-600">
                门禁 {run.gateCount} / 失败 {run.failedGateCount} / 复查{" "}
                {run.reviewGateCount}
              </div>
            </button>
          ))
        )}
      </div>
    </aside>
  );
}

function MetricCard({
  label,
  value,
  tone,
}: {
  label: string;
  value: string | number;
  tone?: "success" | "warning" | "danger";
}) {
  const toneClass =
    tone === "success"
      ? "text-emerald-700"
      : tone === "warning"
        ? "text-amber-700"
        : tone === "danger"
          ? "text-red-700"
          : "text-slate-900";
  return (
    <div className="dp-card">
      <p className="dp-meta">{label}</p>
      <p className={`mt-2 text-2xl font-bold ${toneClass}`}>{value}</p>
    </div>
  );
}

function EvalCatalogPanel({ items }: { items: QualityEvalCaseCatalogItem[] }) {
  const [riskFilter, setRiskFilter] = useState("ALL");
  const [ownerFilter, setOwnerFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const riskOptions = useMemo(() => uniqueCatalogValues(items.map((item) => item.riskLevel)), [items]);
  const ownerOptions = useMemo(() => uniqueCatalogValues(items.map((item) => item.owner)), [items]);
  const statusOptions = useMemo(
    () => uniqueCatalogValues(items.map((item) => item.latestStatus || "NOT_RUN")),
    [items]
  );
  const filteredItems = useMemo(
    () =>
      items.filter((item) => {
        const status = item.latestStatus || "NOT_RUN";
        return (
          (riskFilter === "ALL" || item.riskLevel === riskFilter) &&
          (ownerFilter === "ALL" || item.owner === ownerFilter) &&
          (statusFilter === "ALL" || status === statusFilter)
        );
      }),
    [items, ownerFilter, riskFilter, statusFilter]
  );

  return (
    <div className="dp-card min-w-0">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="dp-section-title">评测用例库</h2>
          <p className="mt-1 text-xs text-slate-500">
            {filteredItems.length} / {items.length} 个用例
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">P0</span>
      </div>

      <div className="mt-4 grid gap-2 sm:grid-cols-3">
        <CatalogFilterSelect
          label="风险"
          value={riskFilter}
          options={riskOptions}
          onChange={setRiskFilter}
        />
        <CatalogFilterSelect
          label="负责人"
          value={ownerFilter}
          options={ownerOptions}
          onChange={setOwnerFilter}
        />
        <CatalogFilterSelect
          label="状态"
          value={statusFilter}
          options={statusOptions}
          onChange={setStatusFilter}
          formatOption={formatStatus}
        />
      </div>

      <div className="mt-4 grid gap-2">
        {items.length === 0 ? (
          <p className="dp-meta">暂无 eval case 目录。</p>
        ) : filteredItems.length === 0 ? (
          <p className="dp-meta">当前筛选条件下暂无 case。</p>
        ) : (
          filteredItems.map((item) => <EvalCatalogRow key={item.caseId} item={item} />)
        )}
      </div>
    </div>
  );
}

function CatalogFilterSelect({
  label,
  value,
  options,
  onChange,
  formatOption,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  formatOption?: (value: string) => string;
}) {
  return (
    <label className="min-w-0 text-xs font-medium text-slate-600">
      <span className="mb-1 block uppercase text-slate-400">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full min-w-0 rounded-md border border-slate-200 bg-white px-2 py-2 text-xs text-slate-700 outline-none focus:border-blue-300"
      >
        <option value="ALL">全部</option>
        {options.map((option) => (
          <option key={`${label}-${option}`} value={option}>
            {formatOption ? formatOption(option) : option}
          </option>
        ))}
      </select>
    </label>
  );
}

function uniqueCatalogValues(values: string[]): string[] {
  return Array.from(
    new Set(values.map((value) => value || "").filter((value) => value.trim().length > 0))
  ).sort((left, right) => left.localeCompare(right));
}

function EvalCatalogRow({ item }: { item: QualityEvalCaseCatalogItem }) {
  const bucketText = summarizeBuckets([
    ...item.latestFailureBuckets,
    ...item.latestReviewBuckets,
  ]);
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex min-w-0 items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {item.caseId}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            {formatCaseType(item.caseType || "agent_quality")}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            v{item.caseVersion || 0} / {item.owner || "-"} /{" "}
            {item.lastUpdated || "-"}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            来源问题: {summarizeTextList(item.sourceIssueIds || [])}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            最近验证: {item.lastVerifiedMarker || "-"}
          </p>
        </div>
        <span className={statusBadge(item.latestStatus)}>
          {formatStatus(item.latestStatus || "NOT_RUN")}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {item.riskLevel ? (
          <span className="dp-badge dp-badge-warning">{item.riskLevel}</span>
        ) : null}
        {item.caseLayer ? (
          <span className="dp-badge dp-badge-info">{item.caseLayer}</span>
        ) : null}
        {item.riskGate ? (
          <span className="dp-badge dp-badge-danger">{item.riskGate}</span>
        ) : null}
        {item.tags.slice(0, 4).map((tag) => (
          <span key={`${item.caseId}-${tag}`} className="dp-badge dp-badge-neutral">
            {tag}
          </span>
        ))}
      </div>
      <p className="mt-3 break-words text-xs text-slate-600">
        预期证据: {summarizeTextList(item.expectedEvidence)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        预期工具: {summarizeTextList(item.expectedTools)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        评分规则: {summarizeTextList(item.scoringRules)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        评分摘要: {summarizeTextList(item.scoringSummary || [])}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        回归策略: {summarizeTextList(item.regressionPolicy || [])}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        失败历史: {summarizeTextList(item.failureHistoryMarkers || [])}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        修复提示: {summarizeTextList(item.remediationHints || [])}
      </p>
      <p className="mt-2 break-words text-xs text-slate-500">
        最近运行: {item.latestRunMarker || "-"}
      </p>
      <p className="mt-1 break-words text-xs text-amber-700">
        失败/复查类型: {bucketText}
      </p>
    </div>
  );
}

function TrendPanel({ trend }: { trend: QualityTrendSummary | null }) {
  const statusText = summarizeCountMap(trend?.statusCounts);
  const failureText = summarizeCountMap(trend?.failureBucketCounts, labelBucket);
  const reviewText = summarizeCountMap(trend?.reviewBucketCounts, labelBucket);
  return (
    <div className="dp-card min-w-0">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="dp-section-title">质量趋势</h2>
          <p className="mt-1 text-xs text-slate-500">
            最近 {trend?.runCount || 0} / {trend?.limit || 20} 次运行
          </p>
        </div>
        <span className="dp-badge dp-badge-info">脱敏摘要</span>
      </div>

      {!trend || trend.runCount === 0 ? (
        <p className="mt-4 dp-meta">暂无可聚合的趋势摘要。</p>
      ) : (
        <div className="mt-4 space-y-3">
          <div className="grid gap-2 sm:grid-cols-2">
            <SmallMetric label="平均通过率" value={formatPercent(trend.averageCasePassRate)} />
            <SmallMetric label="总 token 数" value={formatNullableStat(trend.totalTokens)} />
            <SmallMetric label="估算成本" value={formatCost(trend.estimatedCost ?? null)} />
            <SmallMetric label="平均延迟" value={formatNullableStat(trend.averageLatencyMs)} />
          </div>
          <TrendTextRow label="状态分布" value={statusText} />
          <TrendTextRow label="失败类型" value={failureText} tone="danger" />
          <TrendTextRow label="复查类型" value={reviewText} tone="warning" />
          <div>
            <p className="text-xs font-semibold uppercase text-slate-400">
              反复失败用例
            </p>
            <div className="mt-2 grid gap-2">
              {trend.repeatedCases.length === 0 ? (
                <p className="dp-meta">暂无反复失败或需复查的用例。</p>
              ) : (
                trend.repeatedCases.slice(0, 5).map((item) => (
                  <div
                    key={item.caseId}
                    className="rounded-lg border border-slate-200 bg-white p-2 text-xs text-slate-600"
                  >
                    <p className="break-words font-semibold text-slate-900">
                      {item.caseId}
                    </p>
                    <p className="mt-1 break-words">
                      失败 {item.failedCount} / 复查 {item.reviewCount} / 最近{" "}
                      {formatStatus(item.latestStatus || "-")}
                    </p>
                    <p className="mt-1 break-words text-slate-500">
                      {item.latestRunMarker || "-"}
                    </p>
                  </div>
                ))
              )}
            </div>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase text-slate-400">
              最近运行点
            </p>
            <div className="mt-2 grid gap-2">
              {trend.points.slice(0, 5).map((point) => (
                <div
                  key={point.marker}
                  className="rounded-lg border border-slate-200 bg-white p-2"
                >
                  <div className="flex min-w-0 items-start justify-between gap-2">
                    <p className="min-w-0 break-words text-xs font-semibold text-slate-900">
                      {point.marker}
                    </p>
                    <span className={statusBadge(point.status)}>
                      {formatStatus(point.status)}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    通过率 {formatPercent(point.casePassRate)} / 失败{" "}
                    {point.failedGateCount} / 复查 {point.reviewGateCount}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function SmallMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-white p-2">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 break-words text-sm font-semibold text-slate-900">
        {value}
      </p>
    </div>
  );
}

function TrendTextRow({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: "warning" | "danger";
}) {
  const toneClass =
    tone === "danger"
      ? "text-red-700"
      : tone === "warning"
        ? "text-amber-700"
        : "text-slate-600";
  return (
    <p className={`break-words text-xs ${toneClass}`}>
      <span className="font-semibold uppercase text-slate-400">{label}: </span>
      {value}
    </p>
  );
}

function summarizeCountMap(
  values?: Record<string, number>,
  labeler: (key: string) => string = (key) => formatStatus(key)
): string {
  const entries = Object.entries(values || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .sort((left, right) => right[1] - left[1])
    .slice(0, 4)
    .map(([key, value]) => `${labeler(key)}: ${value}`)
    .join(" / ");
}

function formatPercent(value?: number | null): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return `${(value * 100).toFixed(1)}%`;
}

function RunDetailPanel({
  detail,
  loading,
  runs,
  evalCatalog,
  trend,
  selectedMarker,
  compareMarker,
  compareDetail,
  compareLoading,
  onCompareMarkerChange,
}: {
  detail: QualityRunDetail | null;
  loading: boolean;
  runs: QualityRunSummary[];
  evalCatalog: QualityEvalCaseCatalogItem[];
  trend: QualityTrendSummary | null;
  selectedMarker: string;
  compareMarker: string;
  compareDetail: QualityRunDetail | null;
  compareLoading: boolean;
  onCompareMarkerChange: (marker: string) => void;
}) {
  if (loading) {
    return (
      <div className="dp-card">
        <p className="dp-meta">加载运行详情...</p>
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="dp-card">
        <p className="dp-meta">选择一条运行记录查看详情。</p>
      </div>
    );
  }

  return (
    <RunDetailContent
      detail={detail}
      runs={runs}
      evalCatalog={evalCatalog}
      trend={trend}
      selectedMarker={selectedMarker}
      compareMarker={compareMarker}
      compareDetail={compareDetail}
      compareLoading={compareLoading}
      onCompareMarkerChange={onCompareMarkerChange}
    />
  );
}

function RunDetailContent({
  detail,
  runs,
  evalCatalog,
  trend,
  selectedMarker,
  compareMarker,
  compareDetail,
  compareLoading,
  onCompareMarkerChange,
}: {
  detail: QualityRunDetail;
  runs: QualityRunSummary[];
  evalCatalog: QualityEvalCaseCatalogItem[];
  trend: QualityTrendSummary | null;
  selectedMarker: string;
  compareMarker: string;
  compareDetail: QualityRunDetail | null;
  compareLoading: boolean;
  onCompareMarkerChange: (marker: string) => void;
}) {
  const { summary } = detail;
  const [filters, setFilters] = useState<TriageFilters>(DEFAULT_TRIAGE_FILTERS);
  const [activeSection, setActiveSection] = useState<DetailSectionId>("summary");

  useEffect(() => {
    setFilters(DEFAULT_TRIAGE_FILTERS);
    setActiveSection("summary");
  }, [summary.marker]);

  const traceReferences = useMemo(
    () => detail.traceReferences || [],
    [detail.traceReferences]
  );
  const traceByCaseId = useMemo(() => {
    const map = new Map<string, QualityTraceReference[]>();
    traceReferences.forEach((reference) => {
      if (!reference.caseId) {
        return;
      }
      const next = map.get(reference.caseId) || [];
      next.push(reference);
      map.set(reference.caseId, next);
    });
    return map;
  }, [traceReferences]);

  const gateNames = useMemo(
    () =>
      uniqueSorted([
        ...detail.gates.map((gate) => gate.name),
        ...traceReferences.map((reference) => reference.gateName),
      ]),
    [detail.gates, traceReferences]
  );

  const caseTypes = useMemo(
    () =>
      uniqueSorted([
        ...detail.evalCases.map((item) => item.caseType || "agent_quality"),
        ...traceReferences.map((reference) => reference.caseType || "agent_quality"),
      ]),
    [detail.evalCases, traceReferences]
  );

  const bucketSummaries = useMemo(
    () => buildBucketSummaries(detail, traceReferences),
    [detail, traceReferences]
  );

  const filteredGates = useMemo(
    () =>
      detail.gates.filter((gate) => {
        const buckets = [...gate.failureBuckets, ...gate.reviewBuckets];
        if (!statusMatches(getGateStatus(gate), filters.status)) {
          return false;
        }
        if (!bucketCategoryMatches(buckets, filters.bucketCategory)) {
          return false;
        }
        if (filters.gateName !== "ALL" && gate.name !== filters.gateName) {
          return false;
        }
        if (filters.caseType === "ALL") {
          return true;
        }
        return traceReferences.some(
          (reference) =>
            reference.gateName === gate.name &&
            (reference.caseType || "agent_quality") === filters.caseType
        );
      }),
    [detail.gates, filters, traceReferences]
  );

  const filteredEvalCases = useMemo(
    () =>
      detail.evalCases.filter((item) => {
        const references = traceByCaseId.get(item.caseId) || [];
        const buckets = [
          ...item.failureBuckets,
          ...item.reviewBuckets,
          ...references.flatMap((reference) => [
            ...reference.failureBuckets,
            ...reference.reviewBuckets,
          ]),
        ];
        if (!statusMatches(item.status, filters.status)) {
          return false;
        }
        if (!bucketCategoryMatches(buckets, filters.bucketCategory)) {
          return false;
        }
        if (
          filters.gateName !== "ALL" &&
          !references.some((reference) => reference.gateName === filters.gateName)
        ) {
          return false;
        }
        if (
          filters.caseType !== "ALL" &&
          (item.caseType || "agent_quality") !== filters.caseType
        ) {
          return false;
        }
        return true;
      }),
    [detail.evalCases, filters, traceByCaseId]
  );

  const filteredTraceReferences = useMemo(
    () =>
      traceReferences.filter((reference) => {
        const buckets = [
          ...reference.failureBuckets,
          ...reference.reviewBuckets,
        ];
        if (!statusMatches(reference.status, filters.status)) {
          return false;
        }
        if (!bucketCategoryMatches(buckets, filters.bucketCategory)) {
          return false;
        }
        if (
          filters.gateName !== "ALL" &&
          reference.gateName !== filters.gateName
        ) {
          return false;
        }
        if (
          filters.caseType !== "ALL" &&
          (reference.caseType || "agent_quality") !== filters.caseType
        ) {
          return false;
        }
        return true;
      }),
    [filters, traceReferences]
  );

  const failedGateCount = detail.gates.filter((gate) =>
    isFailedStatus(getGateStatus(gate))
  ).length;
  const reviewGateCount = detail.gates.filter((gate) =>
    isReviewStatus(getGateStatus(gate))
  ).length;
  const failedEvalCount = detail.evalCases.filter((item) =>
    isFailedStatus(item.status)
  ).length;
  const reviewEvalCount = detail.evalCases.filter((item) =>
    isReviewStatus(item.status)
  ).length;
  const attentionCount =
    failedGateCount + reviewGateCount + failedEvalCount + reviewEvalCount;
  const runDiagnostics = useMemo(
    () => buildRunDiagnostics(detail, compareDetail, evalCatalog),
    [compareDetail, detail, evalCatalog]
  );

  return (
    <div className="grid min-w-0 gap-4">
      <section className="dp-card min-w-0 border-slate-200">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0">
            <p className="dp-eyebrow">运行详情</p>
            <h2 className="mt-2 break-words text-xl font-bold text-slate-950">
              {summary.marker}
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              {summary.source} / {summary.artifactName}
            </p>
          </div>
          <span className={statusBadge(summary.status)}>
            {formatStatus(summary.status)}
          </span>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SmallFact label="更新时间" value={formatDateTime(summary.updatedAt)} />
          <SmallFact label="质量门禁" value={`${summary.gateCount}`} />
          <SmallFact
            label="失败 / 复查"
            value={`${summary.failedGateCount} / ${summary.reviewGateCount}`}
          />
          <SmallFact label="token 用量" value={formatTokenUsage(summary.tokenUsage)} />
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <BucketBox title="失败类型" values={summary.failureBuckets} />
          <BucketBox title="复查类型" values={summary.reviewBuckets} />
        </div>

        {attentionCount > 0 ? (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            当前运行有 {attentionCount} 个失败或需复查项，建议先查看“待处理”和“链路”。
          </div>
        ) : (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            当前运行未发现失败或需复查项，可以按需抽查门禁和链路摘要。
          </div>
        )}
      </section>

      <section className="dp-card min-w-0">
        <div className="flex flex-wrap gap-2">
          {DETAIL_SECTIONS.map((section) => (
            <button
              key={section.id}
              type="button"
              onClick={() => setActiveSection(section.id)}
              className={`rounded-lg border px-3 py-2 text-sm font-semibold transition ${
                activeSection === section.id
                  ? "border-blue-300 bg-blue-50 text-blue-700"
                  : "border-slate-200 bg-white text-slate-600 hover:border-blue-200 hover:text-blue-700"
              }`}
            >
              {section.label}
            </button>
          ))}
        </div>
      </section>

      {activeSection === "summary" ? (
        <>
          <OperationalSummaryPanel summary={operationalSummary(detail)} />
          <RunComparisonPanel
            current={detail}
            previous={compareDetail}
            runs={runs}
            selectedMarker={selectedMarker}
            compareMarker={compareMarker}
            loading={compareLoading}
            onCompareMarkerChange={onCompareMarkerChange}
          />
        </>
      ) : null}

      {activeSection === "gates" ? (
        <GateStatusGroups gates={detail.gates} />
      ) : null}

      {activeSection === "failures" ? (
        <>
          <FailureActionSummary diagnostics={runDiagnostics} />
          <FailureTriagePanel
            filters={filters}
            gateNames={gateNames}
            caseTypes={caseTypes}
            bucketSummaries={bucketSummaries}
            resultCounts={{
              gates: filteredGates.length,
              evalCases: filteredEvalCases.length,
              traces: filteredTraceReferences.length,
            }}
            onChange={setFilters}
          />
        </>
      ) : null}

      {activeSection === "trace" ? (
        <TraceReferencePanel
          references={filteredTraceReferences}
          runMarker={summary.marker}
        />
      ) : null}

      {activeSection === "eval" ? (
        <>
          <DiagnosticsSection
            title="评测诊断"
            description="评测通过率、失败用例和 Trace 关联覆盖。"
            items={runDiagnostics.eval}
          />
          <EvalCasesPanel
            items={filteredEvalCases}
            runMarker={summary.marker}
          />
        </>
      ) : null}

      {activeSection === "rag" ? (
        <DomainSummaryPanel detail={detail} domain="rag" diagnostics={runDiagnostics.rag} />
      ) : null}

      {activeSection === "memory" ? (
        <DomainSummaryPanel detail={detail} domain="memory" diagnostics={runDiagnostics.memory} />
      ) : null}

      {activeSection === "tools" ? (
        <DomainSummaryPanel detail={detail} domain="tools" diagnostics={runDiagnostics.tools} />
      ) : null}

      {activeSection === "artifacts" ? (
        <>
          <ParserArtifactPanel detail={detail} />
          <ArtifactSummaryPanel summary={summary} />
          <EvalCatalogPanel items={evalCatalog} />
          <TrendPanel trend={trend} />
        </>
      ) : null}
    </div>
  );
}

function DiagnosticsSection({
  title,
  description,
  items,
}: {
  title: string;
  description: string;
  items: DiagnosticItem[];
}) {
  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">{title}</h3>
          <p className="mt-1 text-xs text-slate-500">{description}</p>
        </div>
        <span className="dp-badge dp-badge-neutral">诊断比率</span>
      </div>
      <DiagnosticGrid items={items} />
    </section>
  );
}

function DiagnosticGrid({ items }: { items: DiagnosticItem[] }) {
  return (
    <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      {items.map((item) => (
        <DiagnosticCard key={`${item.label}-${item.value}`} item={item} />
      ))}
    </div>
  );
}

function DiagnosticCard({ item }: { item: DiagnosticItem }) {
  const toneClass =
    item.tone === "success"
      ? "border-emerald-200 bg-emerald-50 text-emerald-800"
      : item.tone === "warning"
        ? "border-amber-200 bg-amber-50 text-amber-800"
        : item.tone === "danger"
          ? "border-red-200 bg-red-50 text-red-800"
          : "border-slate-200 bg-slate-50 text-slate-700";
  return (
    <div className={`min-w-0 rounded-lg border p-3 ${toneClass}`}>
      <p className="text-xs font-semibold uppercase text-slate-500">{item.label}</p>
      <p className="mt-2 break-words text-xl font-bold text-slate-950">
        {item.value}
      </p>
      <p className="mt-2 break-words text-xs text-slate-600">{item.helper}</p>
      {item.priority ? (
        <p className="mt-2 break-words text-xs font-semibold text-slate-800">
          优先排查：{item.priority}
        </p>
      ) : null}
      {item.action ? (
        <p className="mt-2 break-words text-xs font-semibold text-slate-800">
          建议：{item.action}
        </p>
      ) : null}
    </div>
  );
}

function BucketActionPanel({
  title,
  items,
  emptyText,
}: {
  title: string;
  items: BucketDiagnostic[];
  emptyText: string;
}) {
  return (
    <section className="dp-card min-w-0">
      <div className="flex items-start justify-between gap-3">
        <h3 className="dp-section-title">{title}</h3>
        <span className="dp-badge dp-badge-neutral">{items.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {items.length === 0 ? (
          <p className="dp-meta">{emptyText}</p>
        ) : (
          items.map((item) => <BucketActionRow key={item.bucket} item={item} />)
        )}
      </div>
    </section>
  );
}

function BucketActionRow({ item }: { item: BucketDiagnostic }) {
  const toneClass =
    item.tone === "success"
      ? "border-emerald-200 bg-emerald-50"
      : item.tone === "warning"
        ? "border-amber-200 bg-amber-50"
        : item.tone === "danger"
          ? "border-red-200 bg-red-50"
          : "border-slate-200 bg-slate-50";
  return (
    <div className={`min-w-0 rounded-lg border p-3 ${toneClass}`}>
      <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {item.label}
          </p>
          <p className="mt-1 break-words text-xs text-slate-600">
            {item.description}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <span className="dp-badge dp-badge-neutral">{item.module}</span>
          <span className="dp-badge dp-badge-neutral">次数 {item.count}</span>
        </div>
      </div>
      <p className="mt-2 break-words text-xs font-semibold text-slate-800">
        建议动作：{item.action}
      </p>
    </div>
  );
}

function FailureActionSummary({ diagnostics }: { diagnostics: RunDiagnostics }) {
  return (
    <section className="grid gap-3 lg:grid-cols-2">
      <BucketActionPanel
        title="当前失败类型"
        items={diagnostics.topFailureBuckets}
        emptyText="当前 run 暂无失败类型。"
      />
      <BucketActionPanel
        title="当前复查类型"
        items={diagnostics.topReviewBuckets}
        emptyText="当前 run 暂无复查类型。"
      />
      <BucketActionPanel
        title="新增失败类型"
        items={diagnostics.newFailureBuckets}
        emptyText="没有相对对比运行新增的失败类型。"
      />
      <BucketActionPanel
        title="已恢复失败类型"
        items={diagnostics.recoveredFailureBuckets}
        emptyText="没有相对对比运行恢复的失败类型。"
      />
    </section>
  );
}

function GateStatusGroups({ gates }: { gates: QualityGateSummary[] }) {
  const failed = gates.filter((gate) => isFailedStatus(getGateStatus(gate)));
  const review = gates.filter((gate) => isReviewStatus(getGateStatus(gate)));
  const pass = gates.filter((gate) => isPassStatus(getGateStatus(gate)));
  const other = gates.filter((gate) => {
    const status = getGateStatus(gate);
    return !isFailedStatus(status) && !isReviewStatus(status) && !isPassStatus(status);
  });

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">门禁排查</h3>
          <p className="mt-1 text-xs text-slate-500">
            失败和需复查默认展开，通过项默认压缩。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">{gates.length}</span>
      </div>

      <GateGroup title="失败门禁" gates={failed} tone="danger" emptyText="暂无失败门禁。" />
      <GateGroup title="需复查门禁" gates={review} tone="warning" emptyText="暂无需复查门禁。" />
      {other.length > 0 ? (
        <GateGroup title="其他状态" gates={other} tone="neutral" emptyText="暂无其他状态门禁。" />
      ) : null}

      <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <summary className="cursor-pointer text-sm font-semibold text-slate-700">
          已通过门禁 {pass.length} 项
        </summary>
        <div className="mt-3 grid gap-2">
          {pass.length === 0 ? (
            <p className="dp-meta">暂无通过门禁。</p>
          ) : (
            pass.map((gate) => <GateRow key={gate.name} gate={gate} compact />)
          )}
        </div>
      </details>
    </section>
  );
}

function GateGroup({
  title,
  gates,
  tone,
  emptyText,
}: {
  title: string;
  gates: QualityGateSummary[];
  tone: "danger" | "warning" | "neutral";
  emptyText: string;
}) {
  const toneClass =
    tone === "danger"
      ? "border-red-200 bg-red-50"
      : tone === "warning"
        ? "border-amber-200 bg-amber-50"
        : "border-slate-200 bg-white";
  return (
    <div className={`mt-4 rounded-lg border p-3 ${toneClass}`}>
      <div className="flex items-center justify-between gap-3">
        <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
        <span className="dp-badge dp-badge-neutral">{gates.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {gates.length === 0 ? (
          <p className="dp-meta">{emptyText}</p>
        ) : (
          gates.map((gate) => <GateRow key={gate.name} gate={gate} />)
        )}
      </div>
    </div>
  );
}

function EvalCasesPanel({
  items,
  runMarker,
}: {
  items: QualityEvalCaseResultDetail[];
  runMarker: string;
}) {
  const failedOrReview = items.filter(
    (item) => isFailedStatus(item.status) || isReviewStatus(item.status)
  );
  const pass = items.filter((item) => isPassStatus(item.status));
  const other = items.filter(
    (item) =>
      !isFailedStatus(item.status) &&
      !isReviewStatus(item.status) &&
      !isPassStatus(item.status)
  );

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">评测用例定位</h3>
          <p className="mt-1 text-xs text-slate-500">
            失败或需复查用例会显示 Trace 入口。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">{items.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {failedOrReview.length === 0 ? (
          <p className="dp-meta">暂无失败或需复查评测用例。</p>
        ) : (
          failedOrReview.map((item) => (
            <EvalCaseRow key={item.caseId} item={item} runMarker={runMarker} />
          ))
        )}
      </div>
      {other.length > 0 ? (
        <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
          <summary className="cursor-pointer text-sm font-semibold text-slate-700">
            其他状态用例 {other.length} 项
          </summary>
          <div className="mt-3 grid gap-2">
            {other.map((item) => (
              <EvalCaseRow key={item.caseId} item={item} runMarker={runMarker} />
            ))}
          </div>
        </details>
      ) : null}
      <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <summary className="cursor-pointer text-sm font-semibold text-slate-700">
          已通过用例 {pass.length} 项
        </summary>
        <div className="mt-3 grid gap-2">
          {pass.length === 0 ? (
            <p className="dp-meta">暂无通过用例。</p>
          ) : (
            pass.map((item) => (
              <EvalCaseRow key={item.caseId} item={item} runMarker={runMarker} compact />
            ))
          )}
        </div>
      </details>
    </section>
  );
}

function DomainSummaryPanel({
  detail,
  domain,
  diagnostics,
}: {
  detail: QualityRunDetail;
  domain: "rag" | "memory" | "tools";
  diagnostics: DiagnosticItem[];
}) {
  const config =
    domain === "rag"
      ? {
          title: "RAG 摘要",
          description: "关注检索命中、证据、引用和回答依据性。",
          facts: [
            ["检索命中数", sumMetric(detail, ["retrieveHits"])],
            ["回答引用数", sumMetric(detail, ["qaCitations"])],
            ["证据数", sumMetric(detail, ["evidenceCount"])],
            ["干扰引用数", sumMetric(detail, ["distractorCitationCount"])],
          ],
          buckets: ["RAG_RETRIEVAL_MISS", "CITATION_UNSUPPORTED", "DISTRACTOR_CITATION", "NO_EVIDENCE_FALSE_POSITIVE"],
        }
      : domain === "memory"
        ? {
            title: "记忆摘要",
            description: "关注记忆数量、会话链路和长期记忆相关复查项。",
            facts: [
              ["记忆数", sumMetric(detail, ["memoryCount"])],
              ["证据数", sumMetric(detail, ["evidenceCount"])],
              ["RAG 已触发", countTrueFlags(detail, ["ragTriggered", "traceRagTriggered"])],
              ["需要 RAG", countTrueFlags(detail, ["ragRequired", "traceRagRequired"])],
            ],
            buckets: ["MEMORY_CONFLICT"],
          }
        : {
            title: "工具调用摘要",
            description: "关注工具调用、模型调用、重试和耗时等数值。",
            facts: [
              ["工具调用数", sumMetric(detail, ["toolCallCount"])],
              ["模型调用数", sumMetric(detail, ["modelCallCount"])],
              ["重试次数", sumMetric(detail, ["retryCount"])],
              ["耗时 ms", sumMetric(detail, ["durationMs"])],
            ],
            buckets: ["TOOL_FAILURE", "ENV_BLOCKED"],
          };
  const relatedBuckets = relatedBucketLabels(detail, config.buckets);

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">{config.title}</h3>
          <p className="mt-1 text-xs text-slate-500">{config.description}</p>
        </div>
        <span className="dp-badge dp-badge-neutral">脱敏摘要</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {config.facts.map(([label, value]) => (
          <SmallFact key={label as string} label={label as string} value={formatNumber(value as number | null)} />
        ))}
      </div>
      <DiagnosticGrid items={diagnostics} />
      <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <p className="text-xs font-semibold uppercase text-slate-500">
          相关失败 / 复查类型
        </p>
        <p className="mt-2 break-words text-sm text-slate-700">
          {relatedBuckets.length === 0 ? "暂无相关失败或复查类型。" : relatedBuckets.join(" / ")}
        </p>
      </div>
    </section>
  );
}

function ArtifactSummaryPanel({ summary }: { summary: QualityRunSummary }) {
  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">Artifact 摘要</h3>
          <p className="mt-1 text-xs text-slate-500">
            只展示脱敏元信息，不展示原始 JSON 内容。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">默认折叠原文</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SmallFact label="来源" value={summary.source || "-"} />
        <SmallFact label="Artifact" value={summary.artifactName || "-"} />
        <SmallFact label="缺失" value={summary.artifactMissing ? "是" : "否"} />
        <SmallFact label="解析失败" value={summary.artifactParseFailed ? "是" : "否"} />
      </div>
      <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <summary className="cursor-pointer text-sm font-semibold text-slate-700">
          查看脱敏定位字段
        </summary>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <SmallFact label="marker" value={summary.marker || "-"} />
          <SmallFact label="更新时间" value={formatDateTime(summary.updatedAt)} />
        </div>
      </details>
    </section>
  );
}

function ParserArtifactPanel({ detail }: { detail: QualityRunDetail }) {
  const realChain = findGate(detail, "parserRealChain");
  const boundary = findGate(detail, "parserBoundary");
  const parserQuality = detail.diagnostics?.parserQuality || null;
  const hasParserQuality = hasParserQualitySummary(parserQuality);
  if (!realChain && !boundary && !hasParserQuality) {
    return (
      <section className="dp-card min-w-0">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h3 className="dp-section-title">文档解析质量摘要</h3>
            <p className="mt-1 text-xs text-slate-500">
              当前 run 没有 parser smoke 摘要，可能不是文档解析质量运行。
            </p>
          </div>
          <span className="dp-badge dp-badge-neutral">暂无样本</span>
        </div>
      </section>
    );
  }

  const fileCount = parserQuality?.fileCount ?? gateMetric(realChain, "fileCount");
  const parsedFileCount = parserQuality?.parsedFileCount ?? gateMetric(realChain, "parsedFileCount");
  const negativeCaseCount = parserQuality?.negativeCaseCount ?? gateMetric(boundary, "negativeCaseCount");
  const negativeCasePassCount = parserQuality?.negativeCasePassCount ?? gateMetric(boundary, "negativeCasePassCount");
  const parserFailureCount = parserQuality?.parserFailureCount ?? gateMetric(realChain, "parserFailureCount");
  const retrieveHitCount = parserQuality?.retrieveHitCount ?? gateMetric(realChain, "retrieveHitCount");
  const directRetrieveHitCount = parserQuality?.directRetrieveHitCount ?? gateMetric(realChain, "directRetrieveHitCount");
  const qaRetrievalHitCount = parserQuality?.qaRetrievalHitCount ?? gateMetric(realChain, "qaRetrievalHitCount");
  const citationCount = parserQuality?.citationCount ?? gateMetric(realChain, "citationCount");
  const sourceLocatorCount = parserQuality?.sourceLocatorCount ?? gateMetric(realChain, "sourceLocatorCount");
  const parserDiagnostics = buildParserDiagnostics(parserQuality, realChain, boundary);

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">文档解析质量摘要</h3>
          <p className="mt-1 text-xs text-slate-500">
            只展示 parser smoke 的脱敏数值，用来判断 PDF / HTML / DOCX 解析链路和错误边界是否健康。
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {realChain ? (
            <span className={statusBadge(getGateStatus(realChain))}>
              {formatStatus(getGateStatus(realChain))}
            </span>
          ) : null}
          {boundary ? (
            <span className={statusBadge(getGateStatus(boundary))}>
              边界 {formatStatus(getGateStatus(boundary))}
            </span>
          ) : null}
        </div>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <SmallFact
          label="解析成功文件"
          value={formatRateWithOptionalSample(parserQuality?.parsePassRate, parsedFileCount, fileCount)}
        />
        <SmallFact
          label="切片总数"
          value={formatNullableStat(parserQuality?.chunkCount ?? gateMetric(realChain, "chunkCount"))}
        />
        <SmallFact
          label="检索 / 引用"
          value={`${formatNullableStat(retrieveHitCount)} / ${formatNullableStat(citationCount)}`}
        />
        <SmallFact
          label="检索来源"
          value={`直接 ${formatNullableStat(directRetrieveHitCount)} / 问答 ${formatNullableStat(qaRetrievalHitCount)}`}
        />
        <SmallFact
          label="来源定位"
          value={formatRateWithOptionalSample(parserQuality?.sourceLocatorCoverageRate, sourceLocatorCount, fileCount)}
        />
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SmallFact
          label="解析失败数"
          value={formatNullableStat(parserFailureCount)}
        />
        <SmallFact
          label="运行耗时 ms"
          value={formatNullableStat(gateMetric(realChain, "durationMs"))}
        />
        <SmallFact
          label="负向边界通过"
          value={formatMetricPair(negativeCasePassCount, negativeCaseCount)}
        />
        <SmallFact
          label="不支持格式拒绝"
          value={formatQualityBoolean(gateFlag(boundary, "unsupportedUploadRejected"))}
        />
      </div>

      <DiagnosticGrid items={parserDiagnostics} />

      <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <p className="text-xs font-semibold uppercase text-slate-500">
          待关注原因
        </p>
        <p className="mt-2 break-words text-sm text-slate-700">
          {parserQuality?.reviewReasons?.length
            ? parserQuality.reviewReasons.map(formatParserReviewReason).join(" / ")
            : "暂无需要优先处理的解析质量风险。"}
        </p>
        {parserQuality?.unavailableMetrics?.length ? (
          <p className="mt-2 break-words text-xs text-slate-500">
            暂无统计字段：{parserQuality.unavailableMetrics.map(formatParserMetricName).join(" / ")}
          </p>
        ) : null}
      </div>

      <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <summary className="cursor-pointer text-sm font-semibold text-slate-700">
          查看 parser gate 脱敏信号
        </summary>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          {realChain ? <GateRow gate={realChain} /> : <p className="dp-meta">暂无真实链路 gate。</p>}
          {boundary ? <GateRow gate={boundary} /> : <p className="dp-meta">暂无错误边界 gate。</p>}
        </div>
      </details>
    </section>
  );
}

function hasParserQualitySummary(summary: QualityParserQualitySummary | null): boolean {
  if (!summary) {
    return false;
  }
  return [
    summary.fileCount,
    summary.parsedFileCount,
    summary.sourceLocatorCount,
    summary.retrieveHitCount,
    summary.directRetrieveHitCount,
    summary.qaRetrievalHitCount,
    summary.citationCount,
    summary.negativeCaseCount,
  ].some((value) => typeof value === "number");
}

function buildParserDiagnostics(
  summary: QualityParserQualitySummary | null,
  realChain: QualityGateSummary | null,
  boundary: QualityGateSummary | null
): DiagnosticItem[] {
  const fileCount = summary?.fileCount ?? gateMetric(realChain, "fileCount");
  const parsedFileCount = summary?.parsedFileCount ?? gateMetric(realChain, "parsedFileCount");
  const retrieveHitCount = summary?.retrieveHitCount ?? gateMetric(realChain, "retrieveHitCount");
  const citationCount = summary?.citationCount ?? gateMetric(realChain, "citationCount");
  const negativeCaseCount = summary?.negativeCaseCount ?? gateMetric(boundary, "negativeCaseCount");
  const negativeCasePassCount = summary?.negativeCasePassCount ?? gateMetric(boundary, "negativeCasePassCount");
  const coveredFileTypeCount = summary?.coveredFileTypeCount ?? null;
  const expectedFileTypeCount = summary?.expectedFileTypeCount ?? null;
  const missingFileTypeCount = summary?.missingFileTypeCount ?? null;
  return [
    {
      label: "格式覆盖",
      value: formatMetricPair(coveredFileTypeCount, expectedFileTypeCount),
      helper: "确认 PDF / HTML / DOCX 是否都进入真实解析链路。",
      tone: missingFileTypeCount === 0 ? "success" : missingFileTypeCount === null ? "neutral" : "danger",
      priority: "上传 fixture / parser registry / allowlist",
      action: "缺类型时先看 smoke fixture 是否生成，再看上传白名单和 parser 选择。"
    },
    {
      label: "解析成功率",
      value: formatRateWithOptionalSample(summary?.parsePassRate, parsedFileCount, fileCount),
      helper: "分母是本次 parser smoke 中的文件数。",
      tone: rateTone(summary?.parsePassRate),
      priority: "Parser / ParseTask / storage",
      action: "失败时优先查看脱敏 failureReason 和 parse status 流转。"
    },
    {
      label: "检索与引用覆盖",
      value: `${formatRateWithOptionalSample(summary?.retrieveCoverageRate, retrieveHitCount, fileCount)} / ${formatRateWithOptionalSample(summary?.citationCoverageRate, citationCount, fileCount)}`,
      helper: "前者是 retrieve 覆盖，后者是 QA citation 覆盖。",
      tone: rateTone(minKnownRate(
        summary?.retrieveCoverageRate ?? deriveRate(retrieveHitCount, fileCount),
        summary?.citationCoverageRate ?? deriveRate(citationCount, fileCount)
      )),
      priority: "Chunk / embedding / vector index / citation",
      action: "检索或引用缺失时先看 chunkCount、source locator 和 RAG retrieve gate。"
    },
    {
      label: "错误边界",
      value: formatRateWithOptionalSample(summary?.boundaryPassRate, negativeCasePassCount, negativeCaseCount),
      helper: "覆盖不支持格式、空内容和损坏文件的可控失败。",
      tone: rateTone(summary?.boundaryPassRate),
      priority: "Upload validation / parser error code",
      action: "失败时先确认是否被限流或上传层提前拒绝。"
    }
  ];
}

function findGate(detail: QualityRunDetail, name: string): QualityGateSummary | null {
  const expected = name.toLowerCase();
  return detail.gates.find((gate) => gate.name.toLowerCase() === expected) || null;
}

function gateMetric(gate: QualityGateSummary | null | undefined, name: string): number | null {
  const value = gate?.metrics?.[name];
  return typeof value === "number" && !Number.isNaN(value) ? value : null;
}

function gateFlag(gate: QualityGateSummary | null | undefined, name: string): boolean | null {
  const value = gate?.flags?.[name];
  return typeof value === "boolean" ? value : null;
}

function formatMetricPair(numerator: number | null, denominator: number | null): string {
  if (numerator === null || denominator === null) {
    return "暂无统计";
  }
  return `${formatNumber(numerator)} / ${formatNumber(denominator)}`;
}

function formatRateWithOptionalSample(
  value: number | null | undefined,
  numerator: number | null | undefined,
  denominator: number | null | undefined
): string {
  const resolvedRate =
    typeof value === "number" && !Number.isNaN(value)
      ? value
      : typeof numerator === "number" && typeof denominator === "number"
        ? ratio(numerator, denominator)
        : null;
  if (resolvedRate === null) {
    return "暂无统计";
  }
  if (typeof numerator === "number" && typeof denominator === "number" && denominator > 0) {
    return `${formatRate(resolvedRate)} (${formatNumber(numerator)} / ${formatNumber(denominator)})`;
  }
  return formatRate(resolvedRate);
}

function rateTone(value: number | null | undefined): DiagnosticTone {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "neutral";
  }
  if (value >= 0.99) {
    return "success";
  }
  if (value >= 0.8) {
    return "warning";
  }
  return "danger";
}

function deriveRate(
  numerator: number | null | undefined,
  denominator: number | null | undefined
): number | null {
  if (typeof numerator !== "number" || typeof denominator !== "number") {
    return null;
  }
  return ratio(numerator, denominator);
}

function minKnownRate(...values: Array<number | null | undefined>): number | null {
  const resolved = values.filter((value): value is number =>
    typeof value === "number" && !Number.isNaN(value)
  );
  return resolved.length === 0 ? null : Math.min(...resolved);
}

function formatParserReviewReason(value: string): string {
  const labels: Record<string, string> = {
    parser_file_type_missing: "文件类型覆盖不完整",
    parse_status_failed: "存在解析失败",
    missing_source_locator: "来源定位缺失",
    retrieval_or_citation_missing: "检索或引用缺失",
    parser_boundary_failed: "错误边界未通过",
    unsupported_upload_not_rejected: "不支持格式未被拒绝"
  };
  return labels[value] || "未知解析风险";
}

function formatParserMetricName(value: string): string {
  const labels: Record<string, string> = {
    chunkCount: "切片数量",
    warningCount: "warning 数量"
  };
  return labels[value] || value;
}

function countTrueFlags(detail: QualityRunDetail, names: string[]): number | null {
  let count = 0;
  let found = false;
  const normalizedNames = new Set(names.map((name) => name.toLowerCase()));
  [...detail.gates, ...detail.evalCases].forEach((item) => {
    Object.entries(item.flags || {}).forEach(([key, value]) => {
      if (normalizedNames.has(key.toLowerCase())) {
        found = true;
        if (value) {
          count += 1;
        }
      }
    });
  });
  return found ? count : null;
}

function relatedBucketLabels(detail: QualityRunDetail, categories: string[]): string[] {
  const categorySet = new Set(categories);
  const buckets = [
    ...detail.summary.failureBuckets,
    ...detail.summary.reviewBuckets,
    ...detail.gates.flatMap((gate) => [...gate.failureBuckets, ...gate.reviewBuckets]),
    ...detail.evalCases.flatMap((item) => [...item.failureBuckets, ...item.reviewBuckets]),
  ];
  return uniqueSorted(
    buckets
      .map((bucket) => normalizeTriageBucket(bucket))
      .filter((bucket) => categorySet.has(bucket))
      .map((bucket) => labelBucket(bucket))
  );
}

function buildBucketSummaries(
  detail: QualityRunDetail,
  traceReferences: QualityTraceReference[]
): TriageBucketSummary[] {
  const summaryMap = new Map<string, TriageBucketSummary>();

  function addBuckets(
    buckets: string[],
    status: string,
    label: string,
    bucketType: "failed" | "review"
  ) {
    buckets.forEach((bucket) => {
      const category = normalizeTriageBucket(bucket);
      const current =
        summaryMap.get(category) || {
          category,
          count: 0,
          failedCount: 0,
          reviewCount: 0,
          examples: [],
        };
      current.count += 1;
      if (bucketType === "failed" || status.startsWith("FAILED")) {
        current.failedCount += 1;
      } else {
        current.reviewCount += 1;
      }
      if (bucket && current.examples.length < 3) {
        current.examples.push(`${label}: ${labelBucket(bucket)}`);
      }
      summaryMap.set(category, current);
    });
  }

  addBuckets(detail.summary.failureBuckets, detail.summary.status, "summary", "failed");
  addBuckets(detail.summary.reviewBuckets, detail.summary.status, "summary", "review");
  detail.gates.forEach((gate) => {
    addBuckets(gate.failureBuckets, getGateStatus(gate), gate.name, "failed");
    addBuckets(gate.reviewBuckets, getGateStatus(gate), gate.name, "review");
  });
  detail.evalCases.forEach((item) => {
    addBuckets(item.failureBuckets, item.status, item.caseId, "failed");
    addBuckets(item.reviewBuckets, item.status, item.caseId, "review");
  });
  traceReferences.forEach((reference) => {
    addBuckets(reference.failureBuckets, reference.status, reference.caseId, "failed");
    addBuckets(reference.reviewBuckets, reference.status, reference.caseId, "review");
  });

  return TRIAGE_BUCKET_CATEGORIES
    .map((category) => summaryMap.get(category))
    .filter((item): item is TriageBucketSummary => Boolean(item));
}

function FailureTriagePanel({
  filters,
  gateNames,
  caseTypes,
  bucketSummaries,
  resultCounts,
  onChange,
}: {
  filters: TriageFilters;
  gateNames: string[];
  caseTypes: string[];
  bucketSummaries: TriageBucketSummary[];
  resultCounts: { gates: number; evalCases: number; traces: number };
  onChange: (filters: TriageFilters) => void;
}) {
  const updateFilter = (key: keyof TriageFilters, value: string) => {
    onChange({ ...filters, [key]: value });
  };

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div>
          <h3 className="dp-section-title">失败分桶</h3>
          <p className="mt-1 text-xs text-slate-500">
            门禁 {resultCounts.gates} / 评测 {resultCounts.evalCases} / 链路{" "}
            {resultCounts.traces}
          </p>
        </div>
        <button
          type="button"
          className="dp-btn dp-btn-secondary px-3 py-2 text-xs"
          onClick={() => onChange(DEFAULT_TRIAGE_FILTERS)}
        >
          清除筛选
        </button>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <TriageSelect
          label="状态"
          value={filters.status}
          options={TRIAGE_STATUS_OPTIONS}
          onChange={(value) => updateFilter("status", value)}
          formatOption={(value) => (value === "ALL" ? "全部" : formatStatus(value))}
        />
        <TriageSelect
          label="失败类型"
          value={filters.bucketCategory}
          options={["ALL", ...TRIAGE_BUCKET_CATEGORIES]}
          onChange={(value) => updateFilter("bucketCategory", value)}
          formatOption={(value) => (value === "ALL" ? "全部" : labelBucket(value))}
        />
        <TriageSelect
          label="门禁"
          value={filters.gateName}
          options={["ALL", ...gateNames]}
          onChange={(value) => updateFilter("gateName", value)}
          formatOption={(value) => (value === "ALL" ? "全部" : formatGate(value))}
        />
        <TriageSelect
          label="用例类型"
          value={filters.caseType}
          options={["ALL", ...caseTypes]}
          onChange={(value) => updateFilter("caseType", value)}
          formatOption={(value) => (value === "ALL" ? "全部" : formatCaseType(value))}
        />
      </div>

      <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        {bucketSummaries.length === 0 ? (
          <p className="dp-meta">暂无失败项或需复查项。</p>
        ) : (
          bucketSummaries.map((item) => (
            <button
              key={item.category}
              type="button"
              title={item.category}
              onClick={() => updateFilter("bucketCategory", item.category)}
              className={`min-w-0 rounded-lg border p-3 text-left transition ${
                filters.bucketCategory === item.category
                  ? "border-blue-300 bg-blue-50"
                  : "border-slate-200 bg-white hover:border-blue-200"
              }`}
            >
              <div className="flex min-w-0 items-start justify-between gap-2">
                <p className="break-words text-xs font-semibold text-slate-900">
                  {labelBucket(item.category)}
                </p>
                <span className="dp-badge dp-badge-neutral">{item.count}</span>
              </div>
              <p className="mt-2 text-xs text-slate-600">
                失败 {item.failedCount} / 复查 {item.reviewCount}
              </p>
              <p className="mt-2 break-words text-xs text-slate-500">
                {summarizeBuckets(item.examples)}
              </p>
            </button>
          ))
        )}
      </div>
    </section>
  );
}

function TriageSelect({
  label,
  value,
  options,
  onChange,
  formatOption,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  formatOption?: (value: string) => string;
}) {
  return (
    <label className="min-w-0">
      <span className="text-xs font-semibold uppercase text-slate-500">
        {label}
      </span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
      >
        {options.map((option) => (
          <option key={`${label}-${option}`} value={option}>
            {formatOption ? formatOption(option) : option}
          </option>
        ))}
      </select>
    </label>
  );
}

function TraceReferencePanel({
  references,
  runMarker,
}: {
  references: QualityTraceReference[];
  runMarker: string;
}) {
  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">链路定位</h3>
          <p className="mt-1 text-xs text-slate-500">
            失败和需复查用例的脱敏定位入口。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">{references.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {references.length === 0 ? (
          <p className="dp-meta">暂无链路定位项。</p>
        ) : (
          references.map((reference) => (
            <TraceReferenceRow
              key={`${reference.caseId}-${reference.traceId}-${reference.agentRunId}`}
              reference={reference}
              runMarker={runMarker}
            />
          ))
        )}
      </div>
    </section>
  );
}

function TraceReferenceRow({
  reference,
  runMarker,
}: {
  reference: QualityTraceReference;
  runMarker: string;
}) {
  const status = reference.status || "REVIEW";
  const traceHref = buildTraceHref(runMarker, reference);
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {reference.caseId}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            {formatGate(reference.gateName)} / {formatCaseType(reference.caseType || "agent_quality")}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <Link
            href={traceHref}
            className="rounded border border-blue-200 bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700 hover:border-blue-300"
          >
            打开
          </Link>
          <span className={statusBadge(status)}>{formatStatus(status)}</span>
        </div>
      </div>
      <div className="mt-3 grid gap-2 lg:grid-cols-3">
        <TraceIdBox label="traceId" value={reference.traceId} />
        <TraceIdBox label="agentRunId" value={reference.agentRunId} />
        <TraceIdBox label="conversationId" value={reference.conversationId} />
      </div>
      <div className="mt-2 grid gap-2 md:grid-cols-2">
        <p className="break-words text-xs text-red-700">
          失败: {summarizeBuckets(reference.failureBuckets)}
        </p>
        <p className="break-words text-xs text-amber-700">
          复查: {summarizeBuckets(reference.reviewBuckets)}
        </p>
      </div>
    </div>
  );
}

function buildTraceHref(runMarker: string, reference: QualityTraceReference): string {
  const params = new URLSearchParams();
  params.set("marker", runMarker);
  if (reference.caseId) {
    params.set("caseId", reference.caseId);
  }
  if (reference.traceId) {
    params.set("traceId", reference.traceId);
  }
  if (reference.agentRunId) {
    params.set("agentRunId", reference.agentRunId);
  }
  if (reference.conversationId) {
    params.set("conversationId", reference.conversationId);
  }
  return `/quality/trace?${params.toString()}`;
}

function TraceIdBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-md border border-slate-100 bg-slate-50 px-2 py-2">
      <p className="truncate text-[11px] uppercase text-slate-500">{label}</p>
      <div className="mt-1 flex min-w-0 items-center gap-2">
        <p className="min-w-0 flex-1 break-words text-xs font-semibold text-slate-900">
          {value || "-"}
        </p>
        {value ? (
          <button
            type="button"
            className="shrink-0 rounded border border-slate-200 bg-white px-2 py-1 text-[11px] font-semibold text-slate-600 hover:border-blue-200 hover:text-blue-700"
            onClick={() => void copyToClipboard(value)}
          >
            复制
          </button>
        ) : null}
      </div>
    </div>
  );
}

function SmallFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 break-words text-sm font-semibold text-slate-900">
        {value}
      </p>
    </div>
  );
}

function BucketBox({
  title,
  values,
  format = "bucket",
}: {
  title: string;
  values: string[];
  format?: "bucket" | "text";
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-xs font-semibold uppercase text-slate-500">{title}</p>
      <p className="mt-2 break-words text-sm text-slate-700">
        {format === "text" ? summarizeTextList(values) : summarizeBuckets(values)}
      </p>
    </div>
  );
}

function GateRow({
  gate,
  compact = false,
}: {
  gate: QualityGateSummary;
  compact?: boolean;
}) {
  const status = gate.status || (gate.passed === false ? "FAILED" : "PASS");
  const signals = signalEntries(gate.metrics, gate.flags);
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            <span title={gate.name}>{formatGate(gate.name)}</span>
          </p>
          {!compact ? (
            <>
              <p className="mt-1 break-words text-xs text-slate-500">
                指标: {compactMetrics(gate.metrics)}
              </p>
              <p className="mt-1 break-words text-xs text-slate-500">
                布尔门禁: {compactFlags(gate.flags)}
              </p>
            </>
          ) : null}
        </div>
        <span className={statusBadge(status)}>{formatStatus(status)}</span>
      </div>
      {!compact && signals.length > 0 ? <SignalGrid signals={signals} /> : null}
      {!compact || gate.failureBuckets.length > 0 || gate.reviewBuckets.length > 0 ? (
        <div className="mt-2 grid gap-2 md:grid-cols-2">
          <p className="break-words text-xs text-red-700">
            失败: {summarizeBuckets(gate.failureBuckets)}
          </p>
          <p className="break-words text-xs text-amber-700">
            复查: {summarizeBuckets(gate.reviewBuckets)}
          </p>
        </div>
      ) : null}
    </div>
  );
}

function EvalCaseRow({
  item,
  runMarker,
  compact = false,
}: {
  item: QualityEvalCaseResultDetail;
  runMarker?: string;
  compact?: boolean;
}) {
  const signals = signalEntries(item.metrics, item.flags);
  const traceHref = buildEvalCaseTraceHref(runMarker, item);
  const shouldShowTraceLink =
    Boolean(traceHref) && (isFailedStatus(item.status) || isReviewStatus(item.status));
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {item.caseId}
          </p>
          <p className="mt-1 text-xs text-slate-500">
            {formatCaseType(item.caseType || "agent_quality")}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {shouldShowTraceLink ? (
            <Link
              href={traceHref || "#"}
              className="rounded border border-blue-200 bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700 hover:border-blue-300"
            >
              查看 Trace
            </Link>
          ) : null}
          <span className={statusBadge(item.status)}>{formatStatus(item.status)}</span>
        </div>
      </div>
      {!compact ? (
        <>
          <div className="mt-2 grid gap-2 text-xs text-slate-600 md:grid-cols-2">
            <p className="break-words">traceId: {item.traceId || "-"}</p>
            <p className="break-words">agentRunId: {item.agentRunId || "-"}</p>
          </div>
          {signals.length > 0 ? <SignalGrid signals={signals} /> : null}
          <p className="mt-2 break-words text-xs text-red-700">
            失败: {summarizeBuckets(item.failureBuckets)}
          </p>
          <p className="mt-1 break-words text-xs text-amber-700">
            复查: {summarizeBuckets(item.reviewBuckets)}
          </p>
        </>
      ) : null}
    </div>
  );
}

function buildEvalCaseTraceHref(
  runMarker: string | undefined,
  item: QualityEvalCaseResultDetail
): string | null {
  if (!runMarker || (!item.traceId && !item.agentRunId)) {
    return null;
  }
  const params = new URLSearchParams();
  params.set("marker", runMarker);
  if (item.caseId) {
    params.set("caseId", item.caseId);
  }
  if (item.traceId) {
    params.set("traceId", item.traceId);
  }
  if (item.agentRunId) {
    params.set("agentRunId", item.agentRunId);
  }
  return `/quality/trace?${params.toString()}`;
}

function OperationalSummaryPanel({
  summary,
}: {
  summary: OperationalMetricSummary;
}) {
  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 className="dp-section-title">模型与成本摘要</h3>
          <p className="mt-1 text-xs text-slate-500">
            仅展示 token、成本和运行计数，不展示 prompt 或回答原文。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">仅数值</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SmallFact label="提示词 token 数" value={formatNullableStat(summary.promptTokens)} />
        <SmallFact
          label="回答 token 数"
          value={formatNullableStat(summary.completionTokens)}
        />
        <SmallFact label="总 token 数" value={formatNullableStat(summary.totalTokens)} />
        <SmallFact label="估算成本" value={formatCost(summary.estimatedCost)} />
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <SmallFact label="模型调用数" value={formatNumber(summary.modelCallCount)} />
        <SmallFact label="工具调用数" value={formatNumber(summary.toolCallCount)} />
        <SmallFact label="延迟 ms" value={formatNumber(summary.latencyMs)} />
        <SmallFact label="耗时 ms" value={formatNumber(summary.durationMs)} />
        <SmallFact label="重试次数" value={formatNumber(summary.retryCount)} />
      </div>
    </section>
  );
}

function RunComparisonPanel({
  current,
  previous,
  runs,
  selectedMarker,
  compareMarker,
  loading,
  onCompareMarkerChange,
}: {
  current: QualityRunDetail;
  previous: QualityRunDetail | null;
  runs: QualityRunSummary[];
  selectedMarker: string;
  compareMarker: string;
  loading: boolean;
  onCompareMarkerChange: (marker: string) => void;
}) {
  const comparison = useMemo(
    () => (previous ? buildRunComparison(current, previous) : null),
    [current, previous]
  );
  const options = runs.filter((run) => run.marker !== selectedMarker);

  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h3 className="dp-section-title">运行对比</h3>
          <p className="mt-1 text-xs text-slate-500">
            当前运行与历史运行的质量变化。
          </p>
        </div>
        <label className="min-w-0 lg:min-w-72">
          <span className="text-xs font-semibold uppercase text-slate-500">
            对比运行
          </span>
          <select
            value={compareMarker}
            onChange={(event) => onCompareMarkerChange(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          >
            <option value="">不对比</option>
            {options.map((run) => (
              <option key={run.marker} value={run.marker}>
                {run.marker}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading ? (
        <p className="mt-4 dp-meta">加载对比运行...</p>
      ) : !comparison ? (
        <p className="mt-4 dp-meta">选择历史运行后查看差异。</p>
      ) : (
        <>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SmallFact label="状态变化" value={comparison.statusChange} />
            <SmallFact label="门禁增量" value={formatDelta(comparison.gateDelta)} />
            <SmallFact
              label="失败增量"
              value={formatDelta(comparison.failedGateDelta)}
            />
            <SmallFact
              label="token 增量"
              value={formatDelta(comparison.tokenDelta)}
            />
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SmallFact
              label="复查增量"
              value={formatDelta(comparison.reviewGateDelta)}
            />
            <SmallFact
              label="用例通过率变化"
              value={formatDelta(comparison.casePassRateDelta, 4)}
            />
            <SmallFact
              label="新增失败类型"
              value={`${comparison.newFailureBuckets.length}`}
            />
            <SmallFact
              label="已恢复失败类型"
              value={`${comparison.resolvedFailureBuckets.length}`}
            />
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <BucketBox title="新增失败类型" values={comparison.newFailureBuckets} />
            <BucketBox title="已恢复失败类型" values={comparison.resolvedFailureBuckets} />
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <BucketBox
              title="门禁状态变化"
              values={comparison.changedGateStatuses}
              format="text"
            />
            <BucketBox
              title="评测用例状态变化"
              values={comparison.changedCaseStatuses}
              format="text"
            />
          </div>
        </>
      )}
    </section>
  );
}

function buildRunComparison(
  current: QualityRunDetail,
  previous: QualityRunDetail
): RunComparisonSummary {
  const currentSummary = current.summary;
  const previousSummary = previous.summary;
  const currentTokens = tokenUsageValue(currentSummary.tokenUsage);
  const previousTokens = tokenUsageValue(previousSummary.tokenUsage);
  const currentPassRate = findMetric(current, "casePassRate");
  const previousPassRate = findMetric(previous, "casePassRate");

  return {
    statusChange: `${formatStatus(previousSummary.status)} -> ${formatStatus(currentSummary.status)}`,
    gateDelta: currentSummary.gateCount - previousSummary.gateCount,
    failedGateDelta: currentSummary.failedGateCount - previousSummary.failedGateCount,
    reviewGateDelta: currentSummary.reviewGateCount - previousSummary.reviewGateCount,
    tokenDelta:
      currentTokens === null || previousTokens === null
        ? null
        : currentTokens - previousTokens,
    casePassRateDelta:
      currentPassRate === null || previousPassRate === null
        ? null
        : currentPassRate - previousPassRate,
    newFailureBuckets: uniqueDiff(
      currentSummary.failureBuckets,
      previousSummary.failureBuckets
    ),
    resolvedFailureBuckets: uniqueDiff(
      previousSummary.failureBuckets,
      currentSummary.failureBuckets
    ),
    changedGateStatuses: changedGateStatuses(current, previous),
    changedCaseStatuses: changedCaseStatuses(current, previous),
  };
}

function findMetric(detail: QualityRunDetail, metricName: string): number | null {
  for (const gate of detail.gates) {
    const value = gate.metrics?.[metricName];
    if (typeof value === "number") {
      return value;
    }
  }
  return null;
}

function changedGateStatuses(
  current: QualityRunDetail,
  previous: QualityRunDetail
): string[] {
  const previousByName = new Map(
    previous.gates.map((gate) => [gate.name, getGateStatus(gate)])
  );
  return current.gates
    .map((gate) => {
      const previousStatus = previousByName.get(gate.name);
      const currentStatus = getGateStatus(gate);
      if (!previousStatus || previousStatus === currentStatus) {
        return "";
      }
      return `${formatGate(gate.name)}: ${formatStatus(previousStatus)} -> ${formatStatus(currentStatus)}`;
    })
    .filter(Boolean)
    .slice(0, 6);
}

function changedCaseStatuses(
  current: QualityRunDetail,
  previous: QualityRunDetail
): string[] {
  const previousByCaseId = new Map(
    previous.evalCases.map((item) => [item.caseId, item.status])
  );
  return current.evalCases
    .map((item) => {
      const previousStatus = previousByCaseId.get(item.caseId);
      if (!previousStatus || previousStatus === item.status) {
        return "";
      }
      return `${item.caseId}: ${formatStatus(previousStatus)} -> ${formatStatus(item.status)}`;
    })
    .filter(Boolean)
    .slice(0, 6);
}

function SignalGrid({
  signals,
}: {
  signals: Array<{ key: string; label: string; value: string; tone?: "success" | "warning" }>;
}) {
  return (
    <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
      {signals.map((signal) => {
        const valueClass =
          signal.tone === "success"
            ? "text-emerald-700"
            : signal.tone === "warning"
              ? "text-amber-700"
              : "text-slate-900";
        return (
          <div
            key={`${signal.key}-${signal.value}`}
            title={signal.key}
            className="min-w-0 rounded-md border border-slate-100 bg-slate-50 px-2 py-2"
          >
            <p className="truncate text-[11px] uppercase text-slate-500">
              {signal.label}
            </p>
            <p className={`mt-1 break-words text-xs font-semibold ${valueClass}`}>
              {signal.value}
            </p>
          </div>
        );
      })}
    </div>
  );
}
