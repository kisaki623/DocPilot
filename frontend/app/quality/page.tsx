"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "@/lib/auth";
import {
  getQualityRunDetail,
  listQualityEvalCases,
  listQualityRuns,
  type QualityEvalCaseCatalogItem,
  type QualityEvalCaseResultDetail,
  type QualityGateSummary,
  type QualityRunDetail,
  type QualityRunSummary,
  type QualityTokenUsageSummary,
  type QualityTraceReference,
} from "@/lib/quality-api";

const RESERVED_TABS = ["Overview", "Trace", "Eval", "Failures"];
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
];
const TRIAGE_STATUS_OPTIONS = ["ALL", "FAILED", "REVIEW", "BLOCKED", "PASS"];
const TRIAGE_BUCKET_CATEGORIES = [
  "RAG_RETRIEVAL_MISS",
  "CITATION_UNSUPPORTED",
  "DISTRACTOR_CITATION",
  "NO_EVIDENCE_FALSE_POSITIVE",
  "MEMORY_CONFLICT",
  "TOOL_FAILURE",
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
    lower.includes("unsupported") ||
    lower.includes("grounding") ||
    lower.includes("support")
  ) {
    return "CITATION_UNSUPPORTED";
  }
  if (
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
  if (lower.includes("tool")) {
    return "TOOL_FAILURE";
  }
  if (
    lower.includes("permission") ||
    lower.includes("forbidden") ||
    lower.includes("unauthorized") ||
    lower.includes("scope")
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
  if (!values || values.length === 0) {
    return "-";
  }
  return values.slice(0, 4).join(" / ");
}

function tokenUsageTotal(runs: QualityRunSummary[]): number {
  return runs.reduce((sum, item) => sum + (item.tokenUsage?.totalTokens || 0), 0);
}

function tokenUsageValue(tokenUsage?: QualityTokenUsageSummary): number | null {
  return typeof tokenUsage?.totalTokens === "number" ? tokenUsage.totalTokens : null;
}

function formatTokenUsage(tokenUsage?: QualityTokenUsageSummary): string {
  if (!tokenUsage) {
    return "-";
  }
  const parts = [
    ["prompt", tokenUsage.promptTokens],
    ["completion", tokenUsage.completionTokens],
    ["total", tokenUsage.totalTokens],
  ]
    .filter(([, value]) => typeof value === "number")
    .map(([label, value]) => `${label}: ${formatNumber(value as number)}`);
  if (typeof tokenUsage.estimatedCost === "number") {
    parts.push(`cost: ${formatNumber(tokenUsage.estimatedCost)}`);
  }
  return parts.length === 0 ? "-" : parts.join(" / ");
}

function compactMetrics(metrics: Record<string, number>): string {
  const entries = Object.entries(metrics || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .slice(0, 4)
    .map(([key, value]) => `${key}: ${formatNumber(value)}`)
    .join(" / ");
}

function signalEntries(
  metrics: Record<string, number>,
  flags: Record<string, boolean>,
  limit = 8
): Array<{ key: string; value: string; tone?: "success" | "warning" }> {
  const metricEntries = Object.entries(metrics || {}).map(([key, value]) => ({
    key,
    value: formatNumber(value),
  }));
  const flagEntries = Object.entries(flags || {}).map(([key, value]) => ({
    key,
    value: value ? "true" : "false",
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
  const entries = Object.entries(flags || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .slice(0, 4)
    .map(([key, value]) => `${key}: ${value ? "true" : "false"}`)
    .join(" / ");
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
  const [selectedMarker, setSelectedMarker] = useState("");
  const [compareMarker, setCompareMarker] = useState("");
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
        const catalogResponse = await listQualityEvalCases();
        setEvalCatalog(catalogResponse.data || []);
      } catch {
        setEvalCatalog([]);
      }
    } catch (error) {
      setRuns([]);
      setEvalCatalog([]);
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
      tokens: tokenUsageTotal(runs),
    };
  }, [runs]);

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
      <section className="dp-hero">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <p className="dp-eyebrow">Agent Quality Console</p>
            <h1 className="dp-title">内部质量控制台</h1>
            <p className="dp-subtitle max-w-3xl">
              聚合 smoke、audit、RAG、Memory 和 Eval 的脱敏质量摘要。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {RESERVED_TABS.map((tab, index) => (
              <span
                key={tab}
                className={index === 0 ? "dp-badge dp-badge-info" : "dp-badge dp-badge-neutral"}
              >
                {tab}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <MetricCard label="Runs" value={stats.total} />
        <MetricCard label="PASS" value={stats.pass} tone="success" />
        <MetricCard label="REVIEW" value={stats.review} tone="warning" />
        <MetricCard label="FAILED" value={stats.failed} tone="danger" />
        <MetricCard label="Tokens" value={formatNumber(stats.tokens)} />
      </section>

      {errorMessage ? (
        <section className="dp-card border-red-200 bg-red-50 text-sm text-red-700">
          {errorMessage}
        </section>
      ) : null}

      <section className="grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.55fr)]">
        <div className="grid min-w-0 gap-4">
          <div className="dp-card min-w-0">
            <div className="flex items-center justify-between gap-3">
              <h2 className="dp-section-title">Overview</h2>
              <button
                type="button"
                onClick={() => loadRuns()}
                className="dp-btn dp-btn-secondary px-3 py-2"
                disabled={loading}
              >
                刷新
              </button>
            </div>

            <div className="mt-4 grid gap-2">
              {loading ? (
                <p className="dp-meta">加载中...</p>
              ) : runs.length === 0 ? (
                <p className="dp-meta">暂无质量运行记录。</p>
              ) : (
                runs.map((run) => (
                  <button
                    key={`${run.source}-${run.marker}`}
                    type="button"
                    onClick={() => setSelectedMarker(run.marker)}
                    className={`w-full min-w-0 rounded-lg border p-3 text-left transition ${
                      selectedMarker === run.marker
                        ? "border-blue-300 bg-blue-50"
                        : "border-slate-200 bg-white hover:border-blue-200"
                    }`}
                  >
                    <div className="flex min-w-0 items-center justify-between gap-2">
                      <span className="truncate text-sm font-semibold text-slate-900">
                        {run.marker}
                      </span>
                      <span className={statusBadge(run.status)}>{run.status}</span>
                    </div>
                    <div className="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
                      <span>{run.source}</span>
                      <span>{formatDateTime(run.updatedAt)}</span>
                    </div>
                    <div className="mt-2 text-xs text-slate-600">
                      gates {run.gateCount} / failed {run.failedGateCount} / review{" "}
                      {run.reviewGateCount}
                    </div>
                  </button>
                ))
              )}
            </div>
          </div>

          <EvalCatalogPanel items={evalCatalog} />
        </div>

        <RunDetailPanel
          detail={detail}
          loading={detailLoading}
          runs={runs}
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
          <h2 className="dp-section-title">Eval Catalog</h2>
          <p className="mt-1 text-xs text-slate-500">
            {filteredItems.length} / {items.length} cases
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">P0</span>
      </div>

      <div className="mt-4 grid gap-2 sm:grid-cols-3">
        <CatalogFilterSelect
          label="risk"
          value={riskFilter}
          options={riskOptions}
          onChange={setRiskFilter}
        />
        <CatalogFilterSelect
          label="owner"
          value={ownerFilter}
          options={ownerOptions}
          onChange={setOwnerFilter}
        />
        <CatalogFilterSelect
          label="status"
          value={statusFilter}
          options={statusOptions}
          onChange={setStatusFilter}
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
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="min-w-0 text-xs font-medium text-slate-600">
      <span className="mb-1 block uppercase text-slate-400">{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="w-full min-w-0 rounded-md border border-slate-200 bg-white px-2 py-2 text-xs text-slate-700 outline-none focus:border-blue-300"
      >
        <option value="ALL">ALL</option>
        {options.map((option) => (
          <option key={`${label}-${option}`} value={option}>
            {option}
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
            {item.caseType || "agent_quality"}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            v{item.caseVersion || 0} / {item.owner || "-"} /{" "}
            {item.lastUpdated || "-"}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            source: {summarizeBuckets(item.sourceIssueIds || [])}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            verified: {item.lastVerifiedMarker || "-"}
          </p>
        </div>
        <span className={statusBadge(item.latestStatus)}>
          {item.latestStatus || "NOT_RUN"}
        </span>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {item.riskLevel ? (
          <span className="dp-badge dp-badge-warning">{item.riskLevel}</span>
        ) : null}
        {item.tags.slice(0, 4).map((tag) => (
          <span key={`${item.caseId}-${tag}`} className="dp-badge dp-badge-neutral">
            {tag}
          </span>
        ))}
      </div>
      <p className="mt-3 break-words text-xs text-slate-600">
        evidence: {summarizeBuckets(item.expectedEvidence)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        tools: {summarizeBuckets(item.expectedTools)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        scoring: {summarizeBuckets(item.scoringRules)}
      </p>
      <p className="mt-1 break-words text-xs text-slate-600">
        remediation: {summarizeBuckets(item.remediationHints || [])}
      </p>
      <p className="mt-2 break-words text-xs text-slate-500">
        latest: {item.latestRunMarker || "-"}
      </p>
      <p className="mt-1 break-words text-xs text-amber-700">
        buckets: {bucketText}
      </p>
    </div>
  );
}

function RunDetailPanel({
  detail,
  loading,
  runs,
  selectedMarker,
  compareMarker,
  compareDetail,
  compareLoading,
  onCompareMarkerChange,
}: {
  detail: QualityRunDetail | null;
  loading: boolean;
  runs: QualityRunSummary[];
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
        <p className="dp-meta">选择一条 run 查看详情。</p>
      </div>
    );
  }

  return (
    <RunDetailContent
      detail={detail}
      runs={runs}
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
  selectedMarker,
  compareMarker,
  compareDetail,
  compareLoading,
  onCompareMarkerChange,
}: {
  detail: QualityRunDetail;
  runs: QualityRunSummary[];
  selectedMarker: string;
  compareMarker: string;
  compareDetail: QualityRunDetail | null;
  compareLoading: boolean;
  onCompareMarkerChange: (marker: string) => void;
}) {
  const { summary } = detail;
  const [filters, setFilters] = useState<TriageFilters>(DEFAULT_TRIAGE_FILTERS);

  useEffect(() => {
    setFilters(DEFAULT_TRIAGE_FILTERS);
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

  return (
    <div className="grid min-w-0 gap-4">
      <section className="dp-card min-w-0">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0">
            <p className="dp-eyebrow">Run Detail</p>
            <h2 className="mt-2 break-words text-xl font-bold text-slate-950">
              {summary.marker}
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              {summary.source} / {summary.artifactName}
            </p>
          </div>
          <span className={statusBadge(summary.status)}>{summary.status}</span>
        </div>

        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SmallFact label="更新时间" value={formatDateTime(summary.updatedAt)} />
          <SmallFact label="Gate" value={`${summary.gateCount}`} />
          <SmallFact
            label="失败 / REVIEW"
            value={`${summary.failedGateCount} / ${summary.reviewGateCount}`}
          />
          <SmallFact label="Token usage" value={formatTokenUsage(summary.tokenUsage)} />
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <BucketBox title="Failure Buckets" values={summary.failureBuckets} />
          <BucketBox title="Review Buckets" values={summary.reviewBuckets} />
        </div>
      </section>

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

      <TraceReferencePanel
        references={filteredTraceReferences}
        runMarker={summary.marker}
      />

      <section className="dp-card min-w-0">
        <h3 className="dp-section-title">Gate 列表</h3>
        <div className="mt-3 grid gap-2">
          {filteredGates.length === 0 ? (
            <p className="dp-meta">暂无 gate 明细。</p>
          ) : (
            filteredGates.map((gate) => <GateRow key={gate.name} gate={gate} />)
          )}
        </div>
      </section>

      <section className="dp-card min-w-0">
        <h3 className="dp-section-title">Eval Case</h3>
        <div className="mt-3 grid gap-2">
          {filteredEvalCases.length === 0 ? (
            <p className="dp-meta">暂无 eval case 明细。</p>
          ) : (
            filteredEvalCases.map((item) => (
              <EvalCaseRow key={item.caseId} item={item} />
            ))
          )}
        </div>
      </section>
    </div>
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
        current.examples.push(`${label}: ${bucket}`);
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
          <h3 className="dp-section-title">Failure Triage</h3>
          <p className="mt-1 text-xs text-slate-500">
            gates {resultCounts.gates} / eval {resultCounts.evalCases} / traces{" "}
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
          label="Status"
          value={filters.status}
          options={TRIAGE_STATUS_OPTIONS}
          onChange={(value) => updateFilter("status", value)}
        />
        <TriageSelect
          label="Bucket"
          value={filters.bucketCategory}
          options={["ALL", ...TRIAGE_BUCKET_CATEGORIES]}
          onChange={(value) => updateFilter("bucketCategory", value)}
        />
        <TriageSelect
          label="Gate"
          value={filters.gateName}
          options={["ALL", ...gateNames]}
          onChange={(value) => updateFilter("gateName", value)}
        />
        <TriageSelect
          label="Case Type"
          value={filters.caseType}
          options={["ALL", ...caseTypes]}
          onChange={(value) => updateFilter("caseType", value)}
        />
      </div>

      <div className="mt-4 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
        {bucketSummaries.length === 0 ? (
          <p className="dp-meta">暂无失败桶或 REVIEW 桶。</p>
        ) : (
          bucketSummaries.map((item) => (
            <button
              key={item.category}
              type="button"
              onClick={() => updateFilter("bucketCategory", item.category)}
              className={`min-w-0 rounded-lg border p-3 text-left transition ${
                filters.bucketCategory === item.category
                  ? "border-blue-300 bg-blue-50"
                  : "border-slate-200 bg-white hover:border-blue-200"
              }`}
            >
              <div className="flex min-w-0 items-start justify-between gap-2">
                <p className="break-words text-xs font-semibold text-slate-900">
                  {item.category}
                </p>
                <span className="dp-badge dp-badge-neutral">{item.count}</span>
              </div>
              <p className="mt-2 text-xs text-slate-600">
                failed {item.failedCount} / review {item.reviewCount}
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
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
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
            {option}
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
          <h3 className="dp-section-title">Trace 定位</h3>
          <p className="mt-1 text-xs text-slate-500">
            失败和 REVIEW case 的脱敏定位入口。
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">{references.length}</span>
      </div>
      <div className="mt-3 grid gap-2">
        {references.length === 0 ? (
          <p className="dp-meta">暂无 trace 定位项。</p>
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
            {reference.gateName || "-"} / {reference.caseType || "agent_quality"}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <Link
            href={traceHref}
            className="rounded border border-blue-200 bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700 hover:border-blue-300"
          >
            打开
          </Link>
          <span className={statusBadge(status)}>{status}</span>
        </div>
      </div>
      <div className="mt-3 grid gap-2 lg:grid-cols-3">
        <TraceIdBox label="traceId" value={reference.traceId} />
        <TraceIdBox label="agentRunId" value={reference.agentRunId} />
        <TraceIdBox label="conversationId" value={reference.conversationId} />
      </div>
      <div className="mt-2 grid gap-2 md:grid-cols-2">
        <p className="break-words text-xs text-red-700">
          failure: {summarizeBuckets(reference.failureBuckets)}
        </p>
        <p className="break-words text-xs text-amber-700">
          review: {summarizeBuckets(reference.reviewBuckets)}
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

function BucketBox({ title, values }: { title: string; values: string[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-xs font-semibold uppercase text-slate-500">{title}</p>
      <p className="mt-2 break-words text-sm text-slate-700">
        {summarizeBuckets(values)}
      </p>
    </div>
  );
}

function GateRow({ gate }: { gate: QualityGateSummary }) {
  const status = gate.status || (gate.passed === false ? "FAILED" : "PASS");
  const signals = signalEntries(gate.metrics, gate.flags);
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {gate.name}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            metrics: {compactMetrics(gate.metrics)}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            flags: {compactFlags(gate.flags)}
          </p>
        </div>
        <span className={statusBadge(status)}>{status}</span>
      </div>
      {signals.length > 0 ? <SignalGrid signals={signals} /> : null}
      <div className="mt-2 grid gap-2 md:grid-cols-2">
        <p className="break-words text-xs text-red-700">
          failure: {summarizeBuckets(gate.failureBuckets)}
        </p>
        <p className="break-words text-xs text-amber-700">
          review: {summarizeBuckets(gate.reviewBuckets)}
        </p>
      </div>
    </div>
  );
}

function EvalCaseRow({ item }: { item: QualityEvalCaseResultDetail }) {
  const signals = signalEntries(item.metrics, item.flags);
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {item.caseId}
          </p>
          <p className="mt-1 text-xs text-slate-500">
            {item.caseType || "agent_quality"}
          </p>
        </div>
        <span className={statusBadge(item.status)}>{item.status}</span>
      </div>
      <div className="mt-2 grid gap-2 text-xs text-slate-600 md:grid-cols-2">
        <p className="break-words">trace: {item.traceId || "-"}</p>
        <p className="break-words">agentRun: {item.agentRunId || "-"}</p>
      </div>
      {signals.length > 0 ? <SignalGrid signals={signals} /> : null}
      <p className="mt-2 break-words text-xs text-red-700">
        failure: {summarizeBuckets(item.failureBuckets)}
      </p>
      <p className="mt-1 break-words text-xs text-amber-700">
        review: {summarizeBuckets(item.reviewBuckets)}
      </p>
    </div>
  );
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
          <h3 className="dp-section-title">Model / Cost Summary</h3>
          <p className="mt-1 text-xs text-slate-500">
            token usage and runtime counters
          </p>
        </div>
        <span className="dp-badge dp-badge-neutral">numeric only</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SmallFact label="Prompt tokens" value={formatNumber(summary.promptTokens)} />
        <SmallFact
          label="Completion tokens"
          value={formatNumber(summary.completionTokens)}
        />
        <SmallFact label="Total tokens" value={formatNumber(summary.totalTokens)} />
        <SmallFact label="Estimated cost" value={formatNumber(summary.estimatedCost)} />
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <SmallFact label="Model calls" value={formatNumber(summary.modelCallCount)} />
        <SmallFact label="Tool calls" value={formatNumber(summary.toolCallCount)} />
        <SmallFact label="Latency ms" value={formatNumber(summary.latencyMs)} />
        <SmallFact label="Duration ms" value={formatNumber(summary.durationMs)} />
        <SmallFact label="Retries" value={formatNumber(summary.retryCount)} />
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
          <h3 className="dp-section-title">Run Comparison</h3>
          <p className="mt-1 text-xs text-slate-500">
            current vs previous
          </p>
        </div>
        <label className="min-w-0 lg:min-w-72">
          <span className="text-xs font-semibold uppercase text-slate-500">
            Previous Run
          </span>
          <select
            value={compareMarker}
            onChange={(event) => onCompareMarkerChange(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
          >
            <option value="">NONE</option>
            {options.map((run) => (
              <option key={run.marker} value={run.marker}>
                {run.marker}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading ? (
        <p className="mt-4 dp-meta">加载对比 run...</p>
      ) : !comparison ? (
        <p className="mt-4 dp-meta">选择 previous run 后查看差异。</p>
      ) : (
        <>
          <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SmallFact label="Status" value={comparison.statusChange} />
            <SmallFact label="Gate delta" value={formatDelta(comparison.gateDelta)} />
            <SmallFact
              label="Failed delta"
              value={formatDelta(comparison.failedGateDelta)}
            />
            <SmallFact
              label="Token delta"
              value={formatDelta(comparison.tokenDelta)}
            />
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SmallFact
              label="Review delta"
              value={formatDelta(comparison.reviewGateDelta)}
            />
            <SmallFact
              label="Case pass rate"
              value={formatDelta(comparison.casePassRateDelta, 4)}
            />
            <SmallFact
              label="New failures"
              value={`${comparison.newFailureBuckets.length}`}
            />
            <SmallFact
              label="Resolved failures"
              value={`${comparison.resolvedFailureBuckets.length}`}
            />
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <BucketBox title="New Failure Buckets" values={comparison.newFailureBuckets} />
            <BucketBox title="Resolved Failure Buckets" values={comparison.resolvedFailureBuckets} />
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <BucketBox title="Gate Status Changes" values={comparison.changedGateStatuses} />
            <BucketBox title="Eval Case Changes" values={comparison.changedCaseStatuses} />
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
    statusChange: `${previousSummary.status || "-"} -> ${currentSummary.status || "-"}`,
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
      return `${gate.name}: ${previousStatus} -> ${currentStatus}`;
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
      return `${item.caseId}: ${previousStatus} -> ${item.status}`;
    })
    .filter(Boolean)
    .slice(0, 6);
}

function SignalGrid({
  signals,
}: {
  signals: Array<{ key: string; value: string; tone?: "success" | "warning" }>;
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
            className="min-w-0 rounded-md border border-slate-100 bg-slate-50 px-2 py-2"
          >
            <p className="truncate text-[11px] uppercase text-slate-500">
              {signal.key}
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
