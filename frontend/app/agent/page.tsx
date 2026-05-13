"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
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
    label: "场景1：文档总结",
    task: "Please summarize this document for interview demo, including key capabilities and known boundaries."
  },
  {
    key: "status-summary",
    label: "场景2：状态+总结",
    task: "Please check the parse status first, then provide an overview and next-step suggestion for this document."
  },
  {
    key: "evidence-qa",
    label: "场景3：证据问答",
    task: "Please answer with evidence: what are the core technical highlights in this document?"
  }
] as const;

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
  if (normalized.includes("task too short")) {
    return "任务描述太短，请补充更多细节后再试。";
  }
  if (normalized.includes("code=1014") || normalized.includes("rate limit") || normalized.includes("too frequent")) {
    return "请求过于频繁，请稍后再试。";
  }
  return message;
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
        setPersistedTraceError("持久化执行轨迹为空，请稍后重试。");
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
      setRunError("请先选择一个文档。");
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
        <p className="dp-eyebrow">Agent Studio</p>
        <h1 className="dp-title">Agent 工具链演示</h1>
        <p className="dp-subtitle">
          这里可以直接体验 <code>/api/ai/agent/run</code>：输入任务后查看工具决策、步骤 trace 与最终回答，适合录屏演示与面试讲解。
        </p>
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
          {hasToken && !loadingDocuments && documents.length === 0 ? (
            <div className="dp-alert dp-alert-info mb-4">
              你还没有可用文档，先去 <Link href="/upload" className="underline font-bold">上传页</Link> 完成上传与解析。
            </div>
          ) : null}

          <div className="grid gap-4">
            <label htmlFor="agent-document-select" className="grid gap-2">
              <span className="text-sm font-semibold text-slate-700">选择文档</span>
              <select
                id="agent-document-select"
                className="dp-select"
                value={selectedDocumentId}
                onChange={(event) => setSelectedDocumentId(event.target.value)}
                disabled={loadingDocuments || documents.length === 0}
              >
                {documents.length === 0 ? <option value="">暂无可选文档</option> : null}
                {documents.map((item) => (
                  <option key={item.documentId} value={item.documentId}>
                    #{item.documentId} {item.fileName} ({item.parseStatusLabel || item.parseStatus})
                  </option>
                ))}
              </select>
            </label>

            {selectedDocument ? (
              <div className="dp-card-soft">
                <div className="flex items-center justify-between gap-2">
                  <p className="font-semibold text-slate-800 truncate">{selectedDocument.fileName}</p>
                  <span className={resolveStatusBadge(selectedDocument.parseStatus)}>
                    {selectedDocument.parseStatusLabel || selectedDocument.parseStatus}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-2">文档 ID: {selectedDocument.documentId}</p>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">{selectedDocument.summary || "暂无摘要"}</p>
              </div>
            ) : null}

            <div className="grid gap-2">
              <span className="text-sm font-semibold text-slate-700">演示模板</span>
              <div className="flex flex-wrap gap-2">
                {TASK_TEMPLATES.map((template) => (
                  <button
                    key={template.key}
                    type="button"
                    className="dp-btn dp-btn-ghost"
                    onClick={() => setTask(template.task)}
                    disabled={running}
                  >
                    {template.label}
                  </button>
                ))}
              </div>
            </div>

            <label htmlFor="agent-task-input" className="grid gap-2">
              <span className="text-sm font-semibold text-slate-700">Agent 任务</span>
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
                {running ? "Agent 运行中..." : "运行 Agent"}
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
              <p>执行后会展示：决策工具、每一步输入输出摘要、最终回答与引用片段。</p>
            </div>
          ) : (
            <div className="grid gap-4">
              <div className="dp-kpi-grid">
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">Decision</p>
                  <p className="dp-kpi-value text-lg">{result.decision || "-"}</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">Total Duration</p>
                  <p className="dp-kpi-value text-lg">{result.totalDurationMs ?? 0} ms</p>
                </div>
                <div className="dp-kpi-card">
                  <p className="dp-kpi-label">Task ID</p>
                  <p className="dp-kpi-value text-lg">{result.taskId ?? "-"}</p>
                </div>
              </div>

              <div className="dp-card-soft text-xs text-slate-600">
                <p>Trace ID: <span className="font-mono">{result.traceId || "-"}</span></p>
                <p className="mt-1">Started: {formatDateTime(result.startedAt)}</p>
                <p className="mt-1">Finished: {formatDateTime(result.finishedAt)}</p>
              </div>

              {result.routingReason ? (
                <div className="dp-card-soft">
                  <p className="text-sm font-semibold text-slate-700 mb-2">路由决策</p>
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
                  <p className="text-sm font-semibold text-slate-700">持久化执行轨迹</p>
                  {loadingPersistedTrace ? <span className="text-xs text-slate-500">加载中...</span> : null}
                </div>

                {persistedTraceError ? (
                  <div className="dp-alert dp-alert-info mb-3">
                    持久化 trace 暂时无法展示：{persistedTraceError}
                  </div>
                ) : null}

                {persistedTrace ? (
                  <div className="grid gap-3">
                    <div className="grid gap-2 sm:grid-cols-2 text-xs text-slate-600">
                      <p>Task ID: <span className="font-mono">{persistedTrace.task.id}</span></p>
                      <p>Status: <span className="font-semibold">{persistedTrace.task.status || "-"}</span></p>
                      <p>Decision: <span className="font-semibold">{persistedTrace.task.decision || "-"}</span></p>
                      <p>Total Duration: {persistedTrace.task.totalDurationMs ?? "-"} ms</p>
                      <p>Step Count: {persistedTrace.steps.length}</p>
                      <p>Finished: {formatDateTime(persistedTrace.task.finishTime)}</p>
                    </div>

                    {persistedTrace.steps.length > 0 ? (
                      <ol className="dp-list-clean">
                        {persistedTrace.steps.map((step) => (
                          <li key={`${step.taskId}-${step.stepIndex}-${step.toolName}`} className="rounded-lg border border-blue-100 bg-blue-50/50 p-3">
                            <div className="flex items-center justify-between gap-2 mb-1">
                              <p className="text-sm font-semibold text-slate-800">#{step.stepIndex} {step.toolName}</p>
                              <span className="text-xs text-slate-500">{step.durationMs ?? "-"} ms</span>
                            </div>
                            <p className="text-xs text-slate-600">Status: <span className="font-semibold">{step.status || "-"}</span></p>
                            <p className="text-xs text-slate-600 mt-1 line-clamp-2"><span className="font-semibold">输入：</span>{step.inputSummary || "-"}</p>
                            <p className="text-xs text-slate-600 mt-1 line-clamp-2"><span className="font-semibold">输出：</span>{step.outputSummary || "-"}</p>
                          </li>
                        ))}
                      </ol>
                    ) : (
                      <p className="text-sm text-slate-500">持久化 trace 暂无步骤。</p>
                    )}
                  </div>
                ) : !loadingPersistedTrace && !persistedTraceError ? (
                  <p className="text-sm text-slate-500">
                    {result.taskId ? "等待持久化 trace 返回。" : "本次响应未返回 taskId，无法查询持久化 trace。"}
                  </p>
                ) : null}
              </div>

              <div className="dp-card-soft">
                <p className="text-sm font-semibold text-slate-700 mb-2">工具步骤 Trace</p>
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
                  <p className="text-sm text-slate-500">暂无 trace 步骤。</p>
                )}
              </div>

              <div className="dp-card-soft">
                <p className="text-sm font-semibold text-slate-700 mb-2">引用片段</p>
                {result.citations && result.citations.length > 0 ? (
                  <ul className="dp-list-clean">
                    {result.citations.map((item, index) => (
                      <li key={`${item.chunkIndex}-${item.charStart}-${index}`} className="rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-700">
                        <p className="font-semibold text-slate-800 mb-1">引用 {index + 1}</p>
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
