"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "@/lib/auth";
import {
  getQualityRunDetail,
  type QualityEvalCaseResultDetail,
  type QualityGateSummary,
  type QualityRunDetail,
  type QualityTraceReference,
  type QualityTraceStepDetail,
} from "@/lib/quality-api";
import {
  formatBucketList,
  formatCaseType,
  formatFlagList,
  formatGate,
  formatMetricList,
  formatStatus,
  labelTraceStep,
} from "@/lib/quality-labels";

interface TraceQuery {
  marker: string;
  caseId: string;
  traceId: string;
  agentRunId: string;
  conversationId: string;
}

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

function summarizeBuckets(values?: string[]): string {
  return formatBucketList(values, 5);
}

function compactMetrics(metrics?: Record<string, number>): string {
  return formatMetricList(metrics, 8);
}

function compactFlags(flags?: Record<string, boolean>): string {
  return formatFlagList(flags, 8);
}

function readTraceQuery(): TraceQuery {
  const params = new URLSearchParams(window.location.search);
  return {
    marker: params.get("marker") || "",
    caseId: params.get("caseId") || "",
    traceId: params.get("traceId") || "",
    agentRunId: params.get("agentRunId") || "",
    conversationId: params.get("conversationId") || "",
  };
}

function matchesQuery(reference: QualityTraceReference, query: TraceQuery): boolean {
  if (query.caseId && reference.caseId !== query.caseId) {
    return false;
  }
  if (query.traceId && reference.traceId !== query.traceId) {
    return false;
  }
  if (query.agentRunId && reference.agentRunId !== query.agentRunId) {
    return false;
  }
  if (query.conversationId && reference.conversationId !== query.conversationId) {
    return false;
  }
  return true;
}

export default function QualityTracePage() {
  const [query, setQuery] = useState<TraceQuery>({
    marker: "",
    caseId: "",
    traceId: "",
    agentRunId: "",
    conversationId: "",
  });
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [detail, setDetail] = useState<QualityRunDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const loadDetail = useCallback(async (marker: string) => {
    setLoading(true);
    setErrorMessage("");
    try {
      const response = await getQualityRunDetail(marker);
      setDetail(response.data);
    } catch (error) {
      setDetail(null);
      setErrorMessage(
        error instanceof Error ? error.message : "加载链路定位详情失败"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const nextQuery = readTraceQuery();
    setQuery(nextQuery);
    const routeSmoke = new URLSearchParams(window.location.search).get("routeSmoke");
    if (routeSmoke === "1") {
      setHasToken(true);
      setLoading(false);
      setErrorMessage("");
      return;
    }
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setLoading(false);
      setErrorMessage("未检测到登录状态。");
      return;
    }
    setHasToken(true);
    if (!nextQuery.marker) {
      setLoading(false);
      setErrorMessage("缺少质量运行 marker。");
      return;
    }
    void loadDetail(nextQuery.marker);
  }, [loadDetail]);

  const references = useMemo(
    () => (detail?.traceReferences || []).filter((item) => matchesQuery(item, query)),
    [detail?.traceReferences, query]
  );

  const primaryReference = references[0] || null;
  const relatedGate = useMemo(
    () =>
      primaryReference
        ? detail?.gates.find((gate) => gate.name === primaryReference.gateName) || null
        : null,
    [detail?.gates, primaryReference]
  );
  const relatedEvalCase = useMemo(
    () =>
      primaryReference
        ? detail?.evalCases.find((item) => item.caseId === primaryReference.caseId) ||
          null
        : null,
    [detail?.evalCases, primaryReference]
  );

  if (hasToken === false) {
    return (
      <main className="dp-page mx-auto max-w-5xl px-4 py-8">
        <section className="dp-hero">
          <p className="dp-eyebrow">Agent Quality Console</p>
          <h1 className="dp-title">链路定位</h1>
          <p className="dp-subtitle">登录后查看内部质量定位详情。</p>
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
    <main className="dp-page mx-auto max-w-6xl px-4 py-8">
      <section className="dp-hero">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="min-w-0">
            <p className="dp-eyebrow">Agent Quality Console</p>
            <h1 className="dp-title">链路定位</h1>
            <p className="dp-subtitle max-w-3xl">
              只展示脱敏质量结果中的定位摘要和安全指标。
            </p>
          </div>
          <Link href="/quality?autoload=1" className="dp-btn dp-btn-secondary">
            返回总览
          </Link>
        </div>
      </section>

      {errorMessage ? (
        <section className="dp-card border-red-200 bg-red-50 text-sm text-red-700">
          {errorMessage}
        </section>
      ) : null}

      <section className="dp-card min-w-0">
        <h2 className="dp-section-title">定位参数</h2>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <SmallFact label="marker" value={query.marker || "-"} />
          <SmallFact label="caseId" value={query.caseId || "-"} />
          <SmallFact label="traceId" value={query.traceId || "-"} />
          <SmallFact label="agentRunId" value={query.agentRunId || "-"} />
          <SmallFact label="conversationId" value={query.conversationId || "-"} />
        </div>
      </section>

      {loading ? (
        <section className="dp-card">
          <p className="dp-meta">加载中...</p>
        </section>
      ) : detail ? (
        <>
          <section className="dp-card min-w-0">
            <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
              <div className="min-w-0">
                <p className="dp-eyebrow">运行</p>
                <h2 className="mt-2 break-words text-xl font-bold text-slate-950">
                  {detail.summary.marker}
                </h2>
                <p className="mt-2 break-words text-sm text-slate-600">
                  {detail.summary.source} / {detail.summary.artifactName}
                </p>
              </div>
              <span className={statusBadge(detail.summary.status)}>
                {formatStatus(detail.summary.status)}
              </span>
            </div>
          </section>

          <section className="dp-card min-w-0">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="dp-section-title">匹配链路</h2>
                <p className="mt-1 text-xs text-slate-500">
                  匹配 {references.length} 条
                </p>
              </div>
            </div>
            <div className="mt-4 grid gap-3">
              {references.length === 0 ? (
                <p className="dp-meta">没有匹配的链路定位项。</p>
              ) : (
                references.map((reference) => (
                  <TraceReferenceCard
                    key={`${reference.caseId}-${reference.traceId}-${reference.agentRunId}`}
                    reference={reference}
                  />
                ))
              )}
            </div>
          </section>

          <TraceWaterfallCard reference={primaryReference} />

          <section className="grid gap-4 lg:grid-cols-2">
            <RelatedGateCard gate={relatedGate} />
            <RelatedEvalCaseCard item={relatedEvalCase} />
          </section>
        </>
      ) : null}
    </main>
  );
}

function SmallFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 break-words text-sm font-semibold text-slate-900">
        {value}
      </p>
    </div>
  );
}

function TraceReferenceCard({ reference }: { reference: QualityTraceReference }) {
  const status = reference.status || "REVIEW";
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <p className="break-words text-sm font-semibold text-slate-900">
            {reference.caseId || "-"}
          </p>
          <p className="mt-1 break-words text-xs text-slate-500">
            {formatGate(reference.gateName)} / {formatCaseType(reference.caseType || "agent_quality")}
          </p>
        </div>
        <span className={statusBadge(status)}>{formatStatus(status)}</span>
      </div>
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        <SmallFact label="traceId" value={reference.traceId || "-"} />
        <SmallFact label="agentRunId" value={reference.agentRunId || "-"} />
        <SmallFact label="conversationId" value={reference.conversationId || "-"} />
      </div>
      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <SmallFact
          label="失败类型"
          value={summarizeBuckets(reference.failureBuckets)}
        />
        <SmallFact
          label="复查类型"
          value={summarizeBuckets(reference.reviewBuckets)}
        />
      </div>
    </div>
  );
}

function TraceWaterfallCard({
  reference,
}: {
  reference: QualityTraceReference | null;
}) {
  const steps = reference?.steps || [];
  return (
    <section className="dp-card min-w-0">
      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 className="dp-section-title">链路瀑布图</h2>
          <p className="mt-1 text-xs text-slate-500">
            仅展示脱敏步骤、状态、数值指标和失败桶。
          </p>
        </div>
        <span className="dp-badge dp-badge-info">{steps.length} 步</span>
      </div>
      {steps.length === 0 ? (
        <p className="mt-4 dp-meta">
          当前脱敏结果没有可展示的步骤摘要；后续运行会继续沉淀。
        </p>
      ) : (
        <div className="mt-5 space-y-3">
          {steps.map((step, index) => (
            <TraceStepRow
              key={`${step.stepType}-${index}`}
              step={step}
              index={index}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function TraceStepRow({
  step,
  index,
}: {
  step: QualityTraceStepDetail;
  index: number;
}) {
  return (
    <div className="grid gap-3 rounded-lg border border-slate-200 bg-white p-3 md:grid-cols-[44px_1fr]">
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-900 text-sm font-semibold text-white">
        {index + 1}
      </div>
      <div className="min-w-0">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0">
            <p className="break-words text-sm font-semibold text-slate-900">
              <span title={step.stepType || ""}>{labelTraceStep(step.stepType)}</span>
            </p>
            <p className="mt-1 break-words text-xs text-slate-500">
              {step.label || labelTraceStep(step.stepType)}
            </p>
          </div>
          <span className={statusBadge(step.status)}>
            {formatStatus(step.status || "REVIEW")}
          </span>
        </div>
        <div className="mt-3 grid gap-3 lg:grid-cols-3">
          <SmallFact label="数值指标" value={compactMetrics(step.metrics)} />
          <SmallFact label="布尔门禁" value={compactFlags(step.flags)} />
          <SmallFact label="失败/复查类型" value={summarizeBuckets(step.buckets)} />
        </div>
      </div>
    </div>
  );
}

function RelatedGateCard({ gate }: { gate: QualityGateSummary | null }) {
  return (
    <section className="dp-card min-w-0">
      <h2 className="dp-section-title">关联门禁</h2>
      {!gate ? (
        <p className="mt-3 dp-meta">暂无关联门禁。</p>
      ) : (
        <div className="mt-3 rounded-lg border border-slate-200 bg-white p-3">
          <div className="flex items-start justify-between gap-3">
            <p className="break-words text-sm font-semibold text-slate-900">
              <span title={gate.name}>{formatGate(gate.name)}</span>
            </p>
            <span className={statusBadge(gate.status || (gate.passed ? "PASS" : "FAILED"))}>
              {formatStatus(gate.status || (gate.passed ? "PASS" : "FAILED"))}
            </span>
          </div>
          <p className="mt-3 break-words text-xs text-slate-600">
            指标: {compactMetrics(gate.metrics)}
          </p>
          <p className="mt-2 break-words text-xs text-slate-600">
            布尔门禁: {compactFlags(gate.flags)}
          </p>
          <p className="mt-2 break-words text-xs text-red-700">
            失败: {summarizeBuckets(gate.failureBuckets)}
          </p>
          <p className="mt-1 break-words text-xs text-amber-700">
            复查: {summarizeBuckets(gate.reviewBuckets)}
          </p>
        </div>
      )}
    </section>
  );
}

function RelatedEvalCaseCard({
  item,
}: {
  item: QualityEvalCaseResultDetail | null;
}) {
  return (
    <section className="dp-card min-w-0">
      <h2 className="dp-section-title">关联评测用例</h2>
      {!item ? (
        <p className="mt-3 dp-meta">暂无关联评测用例。</p>
      ) : (
        <div className="mt-3 rounded-lg border border-slate-200 bg-white p-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="break-words text-sm font-semibold text-slate-900">
                {item.caseId}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {formatCaseType(item.caseType || "agent_quality")}
              </p>
            </div>
            <span className={statusBadge(item.status)}>{formatStatus(item.status)}</span>
          </div>
          <p className="mt-3 break-words text-xs text-slate-600">
            traceId: {item.traceId || "-"} / agentRunId: {item.agentRunId || "-"}
          </p>
          <p className="mt-2 break-words text-xs text-slate-600">
            指标: {compactMetrics(item.metrics)}
          </p>
          <p className="mt-2 break-words text-xs text-slate-600">
            布尔门禁: {compactFlags(item.flags)}
          </p>
          <p className="mt-2 break-words text-xs text-red-700">
            失败: {summarizeBuckets(item.failureBuckets)}
          </p>
          <p className="mt-1 break-words text-xs text-amber-700">
            复查: {summarizeBuckets(item.reviewBuckets)}
          </p>
        </div>
      )}
    </section>
  );
}
