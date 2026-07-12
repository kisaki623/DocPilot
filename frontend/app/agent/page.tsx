"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import { citationChunkLabel, citationLocatorLabel, citationSourceTitle, citationStructureLabel } from "@/lib/citation-display";
import {
  getAgentTask,
  getAgentTaskSteps,
  runDocumentAgent,
  type AgentTaskTraceData,
  type DocumentAgentRunData
} from "@/lib/agent-api";
import { listDocuments, type DocumentListItem } from "@/lib/document-api";

const TASK_TEMPLATES = [
  {
    key: "summary",
    label: "文档摘要",
    helper: "提炼文档主题、关键结论与后续建议。",
    task: "请总结这篇文档，提炼 3 个核心结论和 2 个后续建议。"
  },
  {
    key: "status-summary",
    label: "解析校验与摘要",
    helper: "先确认文档就绪状态，再生成结构化摘要。",
    task: "请先检查文档解析状态，再给出这篇文档的概览和下一步建议。"
  },
  {
    key: "evidence-qa",
    label: "引用问答",
    helper: "基于文档内容回答，并关联引用来源。",
    task: "请根据原文内容回答：这篇文档的核心技术亮点是什么？"
  },
  {
    key: "rag-retrieval",
    label: "检索召回",
    helper: "查看与问题相关的召回片段和来源信息。",
    task: "请根据文档内容检索相关片段，并回答这些片段能够支持哪些结论。"
  }
] as const;

const SHOWCASE_POINTS = [
  "围绕文档状态、摘要、问答和检索召回组织工具能力",
  "后端根据任务内容选择合适工具，并返回可解释的选择依据",
  "执行步骤、耗时和状态可在页面中连续追踪",
  "问答结果保留引用来源，便于回到原文核对",
  "检索场景呈现相关片段、相关度和来源信息"
];

const BOUNDARY_POINTS = [
  "当前页面聚焦已解析文档上的 Agent 工具链",
  "上传、解析和文档管理仍在其他页面独立呈现",
  "检索召回采用当前运行配置，向量服务由后端配置控制",
  "工具选择和工具执行均由服务端受控逻辑完成，页面不执行任意模型指令"
];

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

function resolveStatusBadge(status: string | undefined): string {
  if (status === "SUCCESS") {
    return "dp-badge dp-badge-success";
  }
  if (status === "FAILED") {
    return "dp-badge dp-badge-danger";
  }
  if (status === "PENDING") {
    return "dp-badge dp-badge-warning";
  }
  return "dp-badge dp-badge-info";
}

function normalizeRunError(message: string): string {
  const normalized = message.toLowerCase();
  if (normalized.includes("code=1010") || message.includes("无权") || message.includes("不存在")) {
    return "文档不存在或当前账号无权访问，请重新选择可访问的文档。";
  }
  if (normalized.includes("task too short")) {
    return "任务描述太短，请补充更多细节后再试。";
  }
  if (normalized.includes("code=1014") || normalized.includes("rate limit") || normalized.includes("too frequent")) {
    return "请求过于频繁，请稍后再试。";
  }
  return message;
}

function normalizeDocumentIdInput(value: string): string {
  return value.replace(/\D/g, "");
}

function formatScore(score: number | undefined): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "-";
  }
  return score.toFixed(4);
}

function metadataEntries(metadata?: Record<string, string>): Array<[string, string]> {
  if (!metadata) {
    return [];
  }
  return Object.entries(metadata).filter(([key, value]) => key && value);
}

function summarizeToolNames(steps?: Array<{ toolName?: string }>): string {
  const names = Array.from(new Set((steps || []).map((step) => step.toolName).filter(Boolean)));
  return names.length > 0 ? names.join(" / ") : "-";
}

function parseRagTraceSummary(summary?: string): Record<string, string> | null {
  if (!summary || !summary.includes("ragEnabled=")) {
    return null;
  }
  const allowedKeys = new Set([
    "ragEnabled",
    "embeddingProvider",
    "vectorStoreType",
    "topK",
    "retrievedCount",
    "contextTruncated",
    "fallbackUsed",
    "fallbackReason",
    "cacheKeyRagAware"
  ]);
  const fields: Record<string, string> = {};
  summary.split(",").forEach((part) => {
    const [rawKey, ...rawValue] = part.split("=");
    const key = rawKey.trim();
    if (!allowedKeys.has(key)) {
      return;
    }
    fields[key] = rawValue.join("=").trim() || "-";
  });
  return Object.keys(fields).length > 0 ? fields : null;
}

function formatTraceValue(value?: string): string {
  if (value === "true") {
    return "是";
  }
  if (value === "false") {
    return "否";
  }
  return value && value.length > 0 ? value : "-";
}

export default function AgentPage() {
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [loadingDocuments, setLoadingDocuments] = useState(true);
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [documentsError, setDocumentsError] = useState("");

  const [selectedDocumentId, setSelectedDocumentId] = useState("");
  const [task, setTask] = useState<string>(TASK_TEMPLATES[0].task);
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState("");
  const [result, setResult] = useState<DocumentAgentRunData | null>(null);
  const [persistedTrace, setPersistedTrace] = useState<AgentTaskTraceData | null>(null);
  const [loadingPersistedTrace, setLoadingPersistedTrace] = useState(false);
  const [persistedTraceError, setPersistedTraceError] = useState("");

  const loadDocuments = useCallback(async () => {
    setLoadingDocuments(true);
    setDocumentsError("");

    const token = getToken();
    if (!token) {
      setHasToken(false);
      setDocuments([]);
      setSelectedDocumentId("");
      setLoadingDocuments(false);
      return;
    }

    setHasToken(true);
    try {
      const response = await listDocuments({ pageNo: 1, pageSize: 100 });
      const records = response.data?.records || [];
      setDocuments(records);
      const preferred = records.find((item) => item.parseStatus === "SUCCESS") || records[0];
      setSelectedDocumentId(preferred ? String(preferred.documentId) : "");
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载文档失败";
      setDocumentsError(message);
      setDocuments([]);
      setSelectedDocumentId("");
    } finally {
      setLoadingDocuments(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  const selectedDocument = useMemo(
    () => documents.find((item) => String(item.documentId) === selectedDocumentId) || null,
    [documents, selectedDocumentId]
  );

  const workflowItems = useMemo(() => {
    if (!result) {
      return [];
    }

    const persistedSteps = persistedTrace?.steps || [];
    const runtimeSteps = result.steps || [];
    const visibleSteps = persistedSteps.length > 0 ? persistedSteps : runtimeSteps;
    const persistedStatus = persistedTrace
      ? `已记录 ${persistedTrace.steps.length} 个编排步骤`
      : result.taskId
        ? loadingPersistedTrace
          ? "正在加载执行轨迹"
          : "运行记录已创建"
        : "暂无运行记录";

    return [
      {
        label: "接收任务",
        status: "done",
        detail: "已选择文档",
        evidence: result.task ? "任务已接收" : "任务载荷已接收"
      },
      {
        label: "选择工具",
        status: result.decision ? "done" : "waiting",
        detail: result.decision || "-",
        evidence: result.routingReason || "未返回路由说明"
      },
      {
        label: "执行工具",
        status: visibleSteps.length > 0 ? "done" : "waiting",
        detail: summarizeToolNames(visibleSteps),
        evidence: `${visibleSteps.length} 个步骤`
      },
      {
        label: "生成结果",
        status: result.finalAnswer ? "done" : "waiting",
        detail: result.success === false ? "执行失败" : "回答已生成",
        evidence: `${result.totalDurationMs ?? 0} ms`
      },
      {
        label: "记录执行轨迹",
        status: persistedTrace ? "done" : result.taskId ? "waiting" : "waiting",
        detail: persistedStatus,
        evidence: persistedTrace?.task.status || (result.taskId ? "正在同步执行轨迹" : "等待记录")
      }
    ];
  }, [loadingPersistedTrace, persistedTrace, result]);

  const ragTraceSummary = useMemo(() => {
    if (!result) {
      return null;
    }
    const steps = persistedTrace?.steps && persistedTrace.steps.length > 0 ? persistedTrace.steps : result.steps || [];
    const ragStep = steps.find((step) =>
      step.toolName === "document_rag_tool" || Boolean(parseRagTraceSummary(step.outputSummary))
    );
    return parseRagTraceSummary(ragStep?.outputSummary);
  }, [persistedTrace, result]);

  const ragTraceItems = useMemo(() => {
    if (!ragTraceSummary) {
      return [];
    }
    return [
      ["RAG 开启", ragTraceSummary.ragEnabled],
      ["Embedding Provider", ragTraceSummary.embeddingProvider],
      ["Vector Store", ragTraceSummary.vectorStoreType],
      ["Top K", ragTraceSummary.topK],
      ["召回数量", ragTraceSummary.retrievedCount],
      ["上下文截断", ragTraceSummary.contextTruncated],
      ["Fallback 使用", ragTraceSummary.fallbackUsed],
      ["Fallback 原因", ragTraceSummary.fallbackReason],
      ["缓存 Key 含 RAG", ragTraceSummary.cacheKeyRagAware]
    ];
  }, [ragTraceSummary]);

  useEffect(() => {
    if (result && String(result.documentId) !== selectedDocumentId) {
      setResult(null);
      setPersistedTrace(null);
      setPersistedTraceError("");
      setRunError("");
    }
  }, [result, selectedDocumentId]);

  async function loadPersistedTrace(taskId: number) {
    setLoadingPersistedTrace(true);
    setPersistedTraceError("");
    setPersistedTrace(null);

    try {
      const [taskResponse, stepsResponse] = await Promise.all([
        getAgentTask(taskId),
        getAgentTaskSteps(taskId)
      ]);

      if (!taskResponse.data?.task) {
        setPersistedTraceError("执行轨迹暂未生成，请稍后重试。");
        return;
      }

      setPersistedTrace({
        task: taskResponse.data.task,
        steps: stepsResponse.data || taskResponse.data.steps || []
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "持久化执行轨迹加载失败";
      setPersistedTraceError(message);
    } finally {
      setLoadingPersistedTrace(false);
    }
  }

  async function handleRunAgent() {
    setRunError("");
    setResult(null);
    setPersistedTrace(null);
    setPersistedTraceError("");

    if (!hasToken) {
      setRunError("未登录，请先登录后再运行 Agent。");
      return;
    }

    const documentId = Number(selectedDocumentId);
    if (!selectedDocumentId || Number.isNaN(documentId) || documentId <= 0) {
      setRunError("请先选择文档，或手动输入当前账号可访问的文档编号。");
      return;
    }
    const previousSessionId = result?.documentId === documentId ? result.sessionId : undefined;

    const normalizedTask = task.trim();
    if (!normalizedTask) {
      setRunError("请输入任务描述后再运行 Agent。");
      return;
    }

    setRunning(true);

    try {
      const response = await runDocumentAgent({
        documentId,
        task: normalizedTask,
        sessionId: previousSessionId
      });
      if (!response.data) {
        setRunError("Agent 返回为空，请稍后重试。");
        return;
      }
      setResult(response.data);
      if (response.data.taskId && response.data.taskId > 0) {
        void loadPersistedTrace(response.data.taskId);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Agent 调用失败";
      setRunError(normalizeRunError(message));
    } finally {
      setRunning(false);
    }
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <section className="dp-hero">
        <p className="dp-eyebrow">Agent Workflow</p>
        <h1 className="dp-title">Agent 工具链编排</h1>
        <p className="dp-subtitle">
          选择当前账号可访问的已解析文档，运行文档工具链，查看工具选择、
          执行记录、回答内容与引用来源。
        </p>
      </section>

      <section className="grid gap-4 mb-6 lg:grid-cols-[1fr_1fr]">
        <article className="dp-card">
          <div className="flex items-center justify-between gap-3 mb-3">
            <h2 className="dp-section-title">能力概览</h2>
            <span className="dp-badge dp-badge-info">工具编排</span>
          </div>
          <p className="text-sm text-slate-600 leading-6">
            当前页面呈现文档 Agent 的核心流程：后端按任务选择文档状态、摘要、问答或检索工具，
            前端同步展示工具选择、执行记录、最终回答和引用来源。
          </p>
          <ul className="mt-4 grid gap-2 text-sm text-slate-700">
            {SHOWCASE_POINTS.map((point) => (
              <li key={point} className="flex gap-2">
                <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-emerald-500" />
                <span>{point}</span>
              </li>
            ))}
          </ul>
        </article>

        <details className="dp-card">
          <summary className="cursor-pointer list-none">
            <div className="flex items-center justify-between gap-3">
              <h2 className="dp-section-title">开发者说明</h2>
              <span className="dp-badge dp-badge-warning">默认折叠</span>
            </div>
            <p className="mt-2 text-sm text-slate-600 leading-6">
              详细实现说明默认折叠，主流程优先呈现可观察的 Agent 工具链。
            </p>
          </summary>
          <ul className="mt-4 grid gap-2 text-sm text-slate-700">
            {BOUNDARY_POINTS.map((point) => (
              <li key={point} className="flex gap-2">
                <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-amber-500" />
                <span>{point}</span>
              </li>
            ))}
          </ul>
        </details>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.1fr_1fr]">
        <article className="dp-card">
          <div className="flex items-center justify-between gap-3 mb-4">
            <h2 className="dp-section-title">任务输入</h2>
            <button type="button" onClick={loadDocuments} className="dp-btn dp-btn-secondary" disabled={loadingDocuments}>
              {loadingDocuments ? "刷新中..." : "刷新文档"}
            </button>
          </div>

          {hasToken === false ? (
            <div className="dp-alert dp-alert-error mb-4">
              当前未登录，请先前往 <Link href="/login" className="underline font-bold">登录页</Link>。
            </div>
          ) : null}

          {documentsError ? <div className="dp-alert dp-alert-error mb-4">{documentsError}</div> : null}
          <div className="dp-alert dp-alert-info mb-4">
            建议选择已解析成功的文档，以便完整查看工具选择、执行步骤、回答与引用来源。
          </div>
          {hasToken && !loadingDocuments && documents.length === 0 ? (
            <div className="dp-alert dp-alert-info mb-4">
              你还没有可用文档，先去 <Link href="/upload" className="underline font-bold">上传页</Link> 完成上传与解析。
            </div>
          ) : null}

          <div className="grid gap-4">
            <div className="grid gap-3 md:grid-cols-[1fr_220px]">
              <label htmlFor="agent-document-select" className="grid gap-2">
                <span className="text-sm font-semibold text-slate-700">选择文档</span>
                <select
                  id="agent-document-select"
                  className="dp-select"
                  value={selectedDocument ? selectedDocumentId : ""}
                  onChange={(event) => setSelectedDocumentId(event.target.value)}
                  disabled={loadingDocuments || documents.length === 0}
                >
                  {documents.length === 0 ? <option value="">暂无可选文档</option> : <option value="">手动输入文档编号</option>}
                  {documents.map((item) => (
                    <option key={item.documentId} value={item.documentId}>
                      #{item.documentId} {item.fileName} ({item.parseStatusLabel || item.parseStatus})
                    </option>
                  ))}
                </select>
              </label>

              <label htmlFor="agent-document-id-input" className="grid gap-2">
                <span className="text-sm font-semibold text-slate-700">文档编号</span>
                <input
                  id="agent-document-id-input"
                  className="dp-input"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={selectedDocumentId}
                  onChange={(event) => setSelectedDocumentId(normalizeDocumentIdInput(event.target.value))}
                  placeholder="输入当前账号可访问的 ID"
                  disabled={hasToken === false}
                />
              </label>
            </div>

            {selectedDocument ? (
              <div className="dp-card-soft">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-semibold text-slate-800 truncate">{selectedDocument.fileName}</p>
                  <span className={resolveStatusBadge(selectedDocument.parseStatus)}>
                    {selectedDocument.parseStatusLabel || selectedDocument.parseStatus}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-2">文档编号：{selectedDocument.documentId}</p>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">{selectedDocument.summary || "暂无摘要"}</p>
              </div>
            ) : selectedDocumentId ? (
              <div className="dp-card-soft">
                <p className="font-semibold text-slate-800">手动指定文档编号：{selectedDocumentId}</p>
                <p className="text-xs text-slate-500 mt-2">
                  请确认该文档属于当前登录用户，且已经解析成功；无权限或不存在时会在运行结果中提示。
                </p>
              </div>
            ) : null}

            <div className="grid gap-2">
              <span className="text-sm font-semibold text-slate-700">任务模板</span>
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                {TASK_TEMPLATES.map((template) => (
                  <button
                    key={template.key}
                    type="button"
                    className="rounded-lg border border-slate-200 bg-white px-3 py-3 text-left shadow-sm transition hover:border-blue-300 hover:bg-blue-50 disabled:opacity-60"
                    onClick={() => setTask(template.task)}
                    disabled={running}
                  >
                    <span className="block text-sm font-semibold text-slate-800">{template.label}</span>
                    <span className="mt-1 block text-xs leading-5 text-slate-500">{template.helper}</span>
                  </button>
                ))}
              </div>
            </div>

            <label htmlFor="agent-task-input" className="grid gap-2">
              <span className="text-sm font-semibold text-slate-700">任务描述</span>
              <textarea
                id="agent-task-input"
                rows={4}
                className="dp-textarea"
                value={task}
                onChange={(event) => setTask(event.target.value)}
                placeholder="例如：先检查文档状态，再给我一个可用于汇报的摘要。"
              />
            </label>

            <div className="flex gap-3">
              <button
                type="button"
                onClick={handleRunAgent}
                disabled={running || hasToken === false || !selectedDocumentId}
                className="dp-btn dp-btn-primary"
              >
                {running ? "Agent 运行中..." : "运行工具链"}
              </button>
              <button
                type="button"
                className="dp-btn dp-btn-secondary"
                disabled={running}
                onClick={() => {
                  setResult(null);
                  setPersistedTrace(null);
                  setPersistedTraceError("");
                  setRunError("");
                }}
              >
                清空结果
              </button>
            </div>

            {runError ? <div className="dp-alert dp-alert-error">{runError}</div> : null}
          </div>
        </article>

        <article className="dp-card">
          <h2 className="dp-section-title mb-4">运行结果</h2>

          {!result ? (
            <div className="dp-card-soft text-sm text-slate-600">
              <p className="font-semibold mb-2">等待运行</p>
              <p>执行后会展示工具选择、执行步骤、最终回答与引用来源。</p>
            </div>
          ) : (
            <div className="grid gap-4">
              <div className="dp-kpi-grid">
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">工具决策</p>
                  <p className="dp-kpi-value text-lg">{result.decision || "-"}</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">总耗时</p>
                  <p className="dp-kpi-value text-lg">{result.totalDurationMs ?? 0} ms</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">运行记录</p>
                  <p className="dp-kpi-value text-lg">{result.taskId ? "已记录" : "-"}</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">引用数量</p>
                  <p className="dp-kpi-value text-lg">{result.citations?.length ?? 0}</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">检索片段</p>
                  <p className="dp-kpi-value text-lg">{result.ragResults?.length ?? 0}</p>
                </div>
              </div>

              <details className="dp-card-soft text-xs text-slate-600">
                <summary className="cursor-pointer text-sm font-semibold text-slate-700">开发者信息（默认折叠）</summary>
                <p className="mt-2">轨迹编号: <span className="font-mono">{result.traceId || "-"}</span></p>
                <p className="mt-1">开始时间: {formatDateTime(result.startedAt)}</p>
                <p className="mt-1">结束时间: {formatDateTime(result.finishedAt)}</p>
              </details>

              {result.routingReason ? (
                <div className="dp-card-soft">
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <p className="text-sm font-semibold text-slate-700">工具选择依据</p>
                    <span className="dp-badge dp-badge-info">工具选择</span>
                  </div>
                  <p className="text-sm text-slate-700">{result.routingReason}</p>
                  {result.matchedKeywords && result.matchedKeywords.length > 0 ? (
                    <div className="flex flex-wrap gap-2 mt-3">
                      {result.matchedKeywords.map((keyword) => (
                        <span key={keyword} className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
                          {keyword}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              ) : null}

              {ragTraceItems.length > 0 ? (
                <details className="dp-card-soft">
                  <summary className="cursor-pointer">
                    <div className="inline-flex w-full items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-slate-700">检索召回开发者摘要</p>
                      <span className="dp-badge dp-badge-info">默认折叠</span>
                    </div>
                  </summary>
                  <div className="grid gap-2 sm:grid-cols-2">
                    {ragTraceItems.map(([label, value]) => (
                      <div key={label} className="rounded-lg border border-slate-200 bg-white px-3 py-2">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
                        <p className="mt-1 break-words text-sm font-semibold text-slate-700">
                          {formatTraceValue(value)}
                        </p>
                      </div>
                    ))}
                  </div>
                </details>
              ) : null}

              <div className="dp-card-soft">
                <div className="flex items-center justify-between gap-2 mb-3">
                  <p className="text-sm font-semibold text-slate-700">Agent 工具链</p>
                  <span className="dp-badge dp-badge-info">轨迹视图</span>
                </div>
                <ol className="grid gap-3">
                  {workflowItems.map((item, index) => (
                    <li key={item.label} className="grid grid-cols-[2rem_1fr] gap-3">
                      <div className="flex flex-col items-center">
                        <span
                          className={
                            item.status === "done"
                              ? "flex h-8 w-8 items-center justify-center rounded-full bg-emerald-600 text-xs font-bold text-white"
                              : "flex h-8 w-8 items-center justify-center rounded-full bg-slate-300 text-xs font-bold text-white"
                          }
                        >
                          {index + 1}
                        </span>
                        {index < workflowItems.length - 1 ? <span className="mt-2 h-full min-h-5 w-px bg-slate-200" /> : null}
                      </div>
                      <div className="rounded-lg border border-slate-200 bg-white p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <p className="text-sm font-semibold text-slate-800">{item.label}</p>
                          <span className={item.status === "done" ? "dp-badge dp-badge-success" : "dp-badge dp-badge-warning"}>
                            {item.status === "done" ? "已完成" : "等待中"}
                          </span>
                        </div>
                        <p className="mt-1 text-xs font-semibold text-slate-600">{item.detail}</p>
                        <p className="mt-1 text-xs leading-5 text-slate-500">{item.evidence}</p>
                      </div>
                    </li>
                  ))}
                </ol>
              </div>

              <div className="dp-card-soft">
                <p className="text-sm font-semibold text-slate-700 mb-2">最终回答</p>
                <MarkdownViewer
                  markdown={result.finalAnswer || ""}
                  showViewToggle={false}
                  emptyText="暂无回答"
                  variant="history"
                  mode="inline"
                />
              </div>

              <div className="dp-card-soft">
                <div className="flex items-center justify-between gap-2 mb-2">
                  <p className="text-sm font-semibold text-slate-700">检索召回片段</p>
                  <span className="dp-badge dp-badge-warning">检索结果</span>
                </div>
                <p className="text-xs leading-5 text-slate-500 mb-3">
                  当前区域用于查看文档检索召回结果、相关片段和来源信息。
                </p>
                {result.ragResults && result.ragResults.length > 0 ? (
                  <ol className="dp-list-clean">
                    {result.ragResults.map((item) => {
                      const entries = metadataEntries(item.metadata);
                      return (
                        <li key={`${item.rank}-${item.chunkIndex}`} className="rounded-lg border border-emerald-100 bg-emerald-50/60 p-3">
                          <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                            <p className="text-sm font-semibold text-slate-800">
                              片段 {item.rank}
                            </p>
                            <span className="rounded-full border border-emerald-200 bg-white px-2.5 py-1 text-xs font-semibold text-emerald-700">
                              相关度 {formatScore(item.score)}
                            </span>
                          </div>
                          <p className="mb-1 text-xs font-semibold text-slate-700">{citationSourceTitle(item)}</p>
                          <p className="mb-1 text-xs text-slate-600">
                            {[citationLocatorLabel(item), citationChunkLabel(item)].filter(Boolean).join(" · ") || "来源定位待补充"}
                          </p>
                          {citationStructureLabel(item) ? (
                            <p className="mb-2 text-[11px] text-slate-500">{citationStructureLabel(item)}</p>
                          ) : null}
                          <p className="text-xs leading-5 text-slate-700">{item.snippet || "-"}</p>
                          {entries.length > 0 ? (
                            <details className="mt-3 text-[11px] text-slate-500">
                              <summary className="cursor-pointer font-semibold text-slate-600">查看片段来源信息</summary>
                              <div className="mt-2 grid gap-1 sm:grid-cols-2">
                              {entries.map(([key, value]) => (
                                <p key={key} className="truncate">
                                  <span className="font-semibold text-slate-600">{key}:</span> {value}
                                </p>
                              ))}
                              </div>
                            </details>
                          ) : null}
                        </li>
                      );
                    })}
                  </ol>
                ) : (
                  <p className="text-sm text-slate-500">本次结果未返回检索召回片段。请选择检索召回模板后重新运行。</p>
                )}
                {result.ragAnswerContext ? (
                  <details className="mt-3 rounded-lg border border-slate-200 bg-white p-3">
                    <summary className="cursor-pointer text-xs font-semibold text-slate-700">查看检索上下文摘要</summary>
                    <pre className="mt-2 max-h-56 overflow-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
                      {result.ragAnswerContext}
                    </pre>
                  </details>
                ) : null}
              </div>

              <div className="dp-card-soft">
                <div className="flex items-center justify-between gap-2 mb-2">
                  <p className="text-sm font-semibold text-slate-700">执行轨迹记录</p>
                  {persistedTrace ? <span className={resolveStatusBadge(persistedTrace.task.status)}>{persistedTrace.task.status || "-"}</span> : null}
                  {loadingPersistedTrace ? <span className="text-xs text-slate-500">加载中...</span> : null}
                </div>

                {persistedTraceError ? (
                  <div className="dp-alert dp-alert-info mb-3">
                    执行轨迹暂时无法展示：{persistedTraceError}
                  </div>
                ) : null}

                {persistedTrace ? (
                  <div className="grid gap-3">
                    <div className="grid gap-2 sm:grid-cols-2 text-xs text-slate-600">
                      <p>记录编号: <span className="font-mono">{persistedTrace.task.id}</span></p>
                      <p>状态: <span className="font-semibold">{persistedTrace.task.status || "-"}</span></p>
                      <p>工具决策: <span className="font-semibold">{persistedTrace.task.decision || "-"}</span></p>
                      <p>总耗时: {persistedTrace.task.totalDurationMs ?? "-"} ms</p>
                      <p>步骤数量: {persistedTrace.steps.length}</p>
                      <p>结束时间: {formatDateTime(persistedTrace.task.finishTime)}</p>
                    </div>

                    {persistedTrace.steps.length > 0 ? (
                      <ol className="dp-list-clean">
                        {persistedTrace.steps.map((step) => (
                          <li key={`${step.taskId}-${step.stepIndex}-${step.toolName}`} className="rounded-lg border border-blue-100 bg-blue-50/50 p-3">
                            <div className="flex items-center justify-between gap-2 mb-1">
                              <p className="text-sm font-semibold text-slate-800">#{step.stepIndex} {step.toolName}</p>
                              <span className="text-xs text-slate-500">{step.durationMs ?? "-"} ms</span>
                            </div>
                            <p className="text-xs text-slate-600">状态：<span className="font-semibold">{step.status || "-"}</span></p>
                            <p className="text-xs text-slate-600 mt-1 line-clamp-2"><span className="font-semibold">输入：</span>{step.inputSummary || "-"}</p>
                            <p className="text-xs text-slate-600 mt-1 line-clamp-2"><span className="font-semibold">输出：</span>{step.outputSummary || "-"}</p>
                          </li>
                        ))}
                      </ol>
                    ) : (
                      <p className="text-sm text-slate-500">执行轨迹暂无步骤。</p>
                    )}
                  </div>
                ) : !loadingPersistedTrace && !persistedTraceError ? (
                  <p className="text-sm text-slate-500">
                    {result.taskId ? "正在同步执行轨迹。" : "本次结果暂未生成执行轨迹记录。"}
                  </p>
                ) : null}
              </div>

              <div className="dp-card-soft">
                <p className="text-sm font-semibold text-slate-700 mb-2">实时步骤轨迹</p>
                {result.steps && result.steps.length > 0 ? (
                  <ol className="dp-list-clean">
                    {result.steps.map((step) => (
                      <li key={`${step.stepIndex}-${step.toolName}`} className="rounded-lg border border-slate-200 bg-white p-3">
                        <div className="flex items-center justify-between gap-2 mb-1">
                          <p className="text-sm font-semibold text-slate-800">#{step.stepIndex} {step.toolName}</p>
                          <span className="text-xs text-slate-500">{step.durationMs} ms</span>
                        </div>
                        <p className="text-xs text-slate-600"><span className="font-semibold">输入：</span>{step.inputSummary || "-"}</p>
                        <p className="text-xs text-slate-600 mt-1"><span className="font-semibold">输出：</span>{step.outputSummary || "-"}</p>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="text-sm text-slate-500">暂无步骤记录。</p>
                )}
              </div>

              <div className="dp-card-soft">
                <p className="text-sm font-semibold text-slate-700 mb-2">引用片段</p>
                {result.citations && result.citations.length > 0 ? (
                  <ul className="dp-list-clean">
                    {result.citations.map((item, index) => (
                      <li key={`${item.chunkIndex}-${item.charStart}-${index}`} className="rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-700">
                        <p className="font-semibold text-slate-800 mb-1">引用 {index + 1}</p>
                        <p className="mb-1 font-semibold text-slate-700">{citationSourceTitle(item)}</p>
                        <p className="mb-1 text-slate-500">
                          {[citationLocatorLabel(item), citationChunkLabel(item)].filter(Boolean).join(" · ") || "来源定位待补充"}
                        </p>
                        <p>{item.snippet}</p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-slate-500">本次结果未返回引用。</p>
                )}
              </div>
            </div>
          )}
        </article>
      </section>
    </main>
  );
}
