"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import { getDocumentDetail, type DocumentDetailData } from "@/lib/document-api";
import {
  askDocumentQuestion,
  askDocumentQuestionStream,
  getDocumentQaHistory,
  type DocumentQaCitationItem,
  type DocumentQaHistoryItem
} from "@/lib/qa-api";
import { reparseTask } from "@/lib/task-api";

const DETAIL_STATUS_POLLING_TIMEOUT_MS = 120_000;
const TERMINAL_PARSE_STATUS = new Set(["SUCCESS", "FAILED"]);

type ViewMode = "rendered" | "raw";

function formatDateTime(input: string): string {
  if (!input) {
    return "-";
  }
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function normalizeDocumentId(rawValue: string | string[] | undefined): string {
  if (Array.isArray(rawValue)) {
    return rawValue[0] || "";
  }
  return rawValue || "";
}

function buildErrorHint(message: string): string {
  if (message.includes("无权") || message.includes("鏃犳潈")) {
    return "该文档不属于当前登录用户，请返回列表选择自己的文档。";
  }
  if (message.includes("不存在") || message.includes("涓嶅瓨鍦")) {
    return "文档可能不存在，或已被删除。";
  }
  return "";
}

function buildSessionStorageKey(documentId: number): string {
  return `docpilot:qa:session:d:${documentId}`;
}

function generateSessionId(documentId: number): string {
  return `d${documentId}-${Date.now().toString(36)}`;
}

function parseStatusBadge(status: string | undefined): string {
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

function parseProgressLabel(status: string | undefined): string {
  if (!status) {
    return "等待创建";
  }
  if (status === "SUCCESS") {
    return "解析完成";
  }
  if (status === "FAILED") {
    return "解析失败";
  }
  return "解析中";
}

export default function DocumentDetailPage() {
  const params = useParams<{ documentId: string | string[] }>();
  const documentIdParam = useMemo(() => normalizeDocumentId(params?.documentId), [params]);

  const [detail, setDetail] = useState<DocumentDetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [hasToken, setHasToken] = useState<boolean | null>(null);

  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [citations, setCitations] = useState<DocumentQaCitationItem[]>([]);
  const [asking, setAsking] = useState(false);
  const [useStreamingQa, setUseStreamingQa] = useState(true);
  const [streamingQa, setStreamingQa] = useState(false);
  const [qaErrorMessage, setQaErrorMessage] = useState("");

  const [sessionId, setSessionId] = useState("");
  const [sessionHint, setSessionHint] = useState("");
  const [historyList, setHistoryList] = useState<DocumentQaHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyErrorMessage, setHistoryErrorMessage] = useState("");

  const [reparsing, setReparsing] = useState(false);
  const [reparseMessage, setReparseMessage] = useState("");

  const [statusPolling, setStatusPolling] = useState(false);
  const [statusPollingStartedAt, setStatusPollingStartedAt] = useState<number | null>(null);

  const streamAbortRef = useRef<AbortController | null>(null);
  const [answerViewMode, setAnswerViewMode] = useState<ViewMode>("rendered");

  const fetchQaHistory = useCallback(async (documentId: number) => {
    setHistoryLoading(true);
    setHistoryErrorMessage("");
    try {
      const response = await getDocumentQaHistory(documentId, 10);
      setHistoryList(response.data || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载问答历史失败";
      setHistoryErrorMessage(message);
      setHistoryList([]);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    setErrorMessage("");

    const token = getToken();
    if (!token) {
      setHasToken(false);
      setDetail(null);
      setErrorMessage("未检测到登录状态，请先登录");
      setHistoryList([]);
      setHistoryErrorMessage("");
      setLoading(false);
      return;
    }

    setHasToken(true);

    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setDetail(null);
      setErrorMessage("文档 ID 无效");
      setHistoryList([]);
      setHistoryErrorMessage("");
      setLoading(false);
      return;
    }

    try {
      const response = await getDocumentDetail(documentId);
      if (!response.data) {
        setDetail(null);
        setErrorMessage("未获取到文档详情数据");
        return;
      }
      setDetail(response.data);
      await fetchQaHistory(documentId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载文档详情失败";
      setDetail(null);
      setErrorMessage(message);
      setHistoryList([]);
    } finally {
      setLoading(false);
    }
  }, [documentIdParam, fetchQaHistory]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  useEffect(() => {
    return () => {
      streamAbortRef.current?.abort();
      streamAbortRef.current = null;
      setStreamingQa(false);
    };
  }, []);

  useEffect(() => {
    const documentId = Number(documentIdParam);
    if (!hasToken || Number.isNaN(documentId) || documentId <= 0 || !detail) {
      setStatusPolling(false);
      setStatusPollingStartedAt(null);
      return;
    }

    const currentStatus = detail.parseStatus || "";
    if (TERMINAL_PARSE_STATUS.has(currentStatus)) {
      setStatusPolling(false);
      setStatusPollingStartedAt(null);
      return;
    }

    setStatusPolling(true);
    if (!statusPollingStartedAt) {
      setStatusPollingStartedAt(Date.now());
    }

    let active = true;
    const timer = window.setInterval(async () => {
      try {
        if (statusPollingStartedAt && Date.now() - statusPollingStartedAt > DETAIL_STATUS_POLLING_TIMEOUT_MS) {
          setStatusPolling(false);
          setErrorMessage("解析状态轮询超时，请点击刷新或稍后重试。");
          return;
        }
        const response = await getDocumentDetail(documentId);
        if (!active || !response.data) {
          return;
        }
        setDetail(response.data);
        const nextStatus = response.data.parseStatus || "";
        if (TERMINAL_PARSE_STATUS.has(nextStatus)) {
          setStatusPolling(false);
          setStatusPollingStartedAt(null);
        }
      } catch {
        if (!active) {
          return;
        }
        setStatusPolling(false);
        setStatusPollingStartedAt(null);
      }
    }, 2000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [detail, documentIdParam, hasToken, statusPollingStartedAt]);

  useEffect(() => {
    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setSessionId("");
      setSessionHint("");
      return;
    }

    const storageKey = buildSessionStorageKey(documentId);
    const cachedSession = window.localStorage.getItem(storageKey);
    if (cachedSession) {
      setSessionId(cachedSession);
      setSessionHint("已恢复最近会话，可继续多轮提问。");
      return;
    }

    const generatedSession = generateSessionId(documentId);
    window.localStorage.setItem(storageKey, generatedSession);
    setSessionId(generatedSession);
    setSessionHint("已创建默认会话。");
  }, [documentIdParam]);

  const errorHint = useMemo(() => buildErrorHint(errorMessage), [errorMessage]);

  async function handleAskQuestion() {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setQaErrorMessage("未检测到登录状态，请先登录后提问");
      return;
    }

    const normalizedQuestion = question.trim();
    if (!normalizedQuestion) {
      setQaErrorMessage("请输入问题后再提交");
      return;
    }

    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setQaErrorMessage("文档 ID 无效，无法提问");
      return;
    }

    const normalizedSessionId = (sessionId || "").trim() || generateSessionId(documentId);
    let nextSessionId = normalizedSessionId;
    window.localStorage.setItem(buildSessionStorageKey(documentId), normalizedSessionId);
    if (!sessionId.trim()) {
      setSessionId(normalizedSessionId);
    }

    setQaErrorMessage("");
    setAsking(true);
    setStreamingQa(useStreamingQa);
    setAnswer("");
    setCitations([]);

    const askByNormalApi = async () => {
      const response = await askDocumentQuestion({
        documentId,
        question: normalizedQuestion,
        sessionId: normalizedSessionId
      });
      setAnswer(response.data?.answer || "");
      setCitations(response.data?.citations || []);
      nextSessionId = (response.data?.sessionId || "").trim() || normalizedSessionId;
      setSessionHint("当前会话已续用，后续提问会自动携带历史上下文。");
    };

    try {
      if (useStreamingQa) {
        const controller = new AbortController();
        streamAbortRef.current = controller;
        await askDocumentQuestionStream(
          {
            documentId,
            question: normalizedQuestion,
            sessionId: normalizedSessionId
          },
          {
            onChunk: (chunk) => {
              if (!chunk) {
                return;
              }
              setAnswer((prev) => prev + chunk);
            },
            onDone: () => {
              setStreamingQa(false);
              setSessionHint("当前会话已续用，后续提问会自动携带历史上下文。");
            },
            onError: (message) => {
              setQaErrorMessage(message || "流式问答失败");
              setStreamingQa(false);
            }
          },
          controller.signal
        );
        setStreamingQa(false);
      } else {
        await askByNormalApi();
      }

      setSessionId(nextSessionId);
      window.localStorage.setItem(buildSessionStorageKey(documentId), nextSessionId);
      await fetchQaHistory(documentId);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setStreamingQa(false);
        setQaErrorMessage("已停止流式输出");
        return;
      }
      const streamErrorMessage = error instanceof Error ? error.message : "流式问答失败";
      if (useStreamingQa) {
        setStreamingQa(false);
        try {
          await askByNormalApi();
          setSessionId(nextSessionId);
          window.localStorage.setItem(buildSessionStorageKey(documentId), nextSessionId);
          await fetchQaHistory(documentId);
          setQaErrorMessage("");
          setSessionHint(`流式问答失败（${streamErrorMessage}），已自动降级为普通问答。`);
          return;
        } catch (fallbackError) {
          const fallbackMessage = fallbackError instanceof Error ? fallbackError.message : "普通问答也失败";
          setQaErrorMessage(`流式问答失败：${streamErrorMessage}；降级后仍失败：${fallbackMessage}`);
        }
      } else {
        setQaErrorMessage(streamErrorMessage);
      }
      setAnswer("");
      setCitations([]);
    } finally {
      setAsking(false);
      setStreamingQa(false);
      streamAbortRef.current = null;
    }
  }

  async function handleReparse() {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setReparseMessage("未检测到登录状态，请先登录后重新解析。");
      return;
    }

    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setReparseMessage("文档 ID 无效，无法重新解析。");
      return;
    }

    setReparsing(true);
    setReparseMessage("");

    try {
      const response = await reparseTask(documentId);
      const statusLabel = response.data?.statusLabel || response.data?.status || "PENDING";
      setReparseMessage(`已触发重新解析，当前状态：${statusLabel}。`);
      setAnswer("");
      setCitations([]);
      setQaErrorMessage("");
      setStatusPollingStartedAt(Date.now());
      await fetchDetail();
    } catch (error) {
      const message = error instanceof Error ? error.message : "重新解析失败";
      setReparseMessage(message);
    } finally {
      setReparsing(false);
    }
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8 flex items-center justify-between">
        <div>
          <p className="text-sm font-bold text-slate-400 tracking-wider uppercase mb-1">文档工作台</p>
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-3">
            {detail?.title || detail?.fileName || "文档详情"}
            {detail ? (
              <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                detail.parseStatus === "SUCCESS" ? "bg-emerald-100 text-emerald-700" :
                detail.parseStatus === "FAILED" ? "bg-rose-100 text-rose-700" :
                "bg-blue-100 text-blue-700"
              }`}>
                {detail.parseStatusLabel || detail.parseStatus || "未知状态"}
              </span>
            ) : null}
          </h1>
        </div>

        <div className="flex gap-3">
          {detail && TERMINAL_PARSE_STATUS.has(detail.parseStatus || "") ? (
            <button
              type="button"
              onClick={handleReparse}
              disabled={loading || reparsing}
              className="dp-btn dp-btn-secondary"
            >
              {reparsing ? "重新解析中..." : "重新解析"}
            </button>
          ) : null}
          <button
            type="button"
            onClick={fetchDetail}
            disabled={loading}
            className="dp-btn dp-btn-secondary"
          >
            {loading ? "刷新中..." : "刷新"}
          </button>
          <Link href="/documents" className="dp-btn dp-btn-primary">
            返回文档库
          </Link>
        </div>
      </section>

      {hasToken === false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">
          未登录或登录态丢失，请先前往 <Link href="/login" className="underline font-bold">登录页</Link>。
        </section>
      ) : null}

      {loading && !detail ? (
        <section className="text-center py-12 text-slate-500">正在加载文档内容...</section>
      ) : null}

      {!loading && errorMessage ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">
          <p>{errorMessage}</p>
          {errorHint ? <p className="mt-1 text-sm">{errorHint}</p> : null}
        </section>
      ) : null}

      {!loading && !errorMessage && detail ? (
        <>
          {reparseMessage ? <section className="bg-blue-50 text-blue-700 p-4 rounded-xl mb-8">{reparseMessage}</section> : null}

          <section className="grid gap-6 lg:grid-cols-[1fr_350px]">
            <div className="space-y-6">
              <article className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
                <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                  <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                    <span className="w-2 h-6 bg-blue-600 rounded-sm"></span> AI 问答助手
                  </h2>
                  <div className="flex items-center gap-4 text-sm text-slate-600">
                    <label className="flex items-center gap-2 cursor-pointer hover:text-slate-900 transition-colors">
                      <input
                        type="checkbox"
                        checked={useStreamingQa}
                        onChange={(event) => setUseStreamingQa(event.target.checked)}
                        disabled={asking}
                        className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                      />
                      实时打字输出
                    </label>
                  </div>
                </div>

                <div className="p-6">
                  {detail.parseStatus !== "SUCCESS" ? (
                    <div className="bg-amber-50 text-amber-800 p-3 rounded-lg text-sm mb-4">
                      文档尚未解析成功，暂时无法提问。请等待解析完成。
                    </div>
                  ) : null}

                  <div className="space-y-4">
                    {sessionHint ? <p className="text-xs text-slate-500 bg-slate-50 p-2 rounded inline-block">{sessionHint}</p> : null}

                    <div>
                      <textarea
                        id="qa-question-input"
                        value={question}
                        onChange={(event) => setQuestion(event.target.value)}
                        rows={3}
                        placeholder="向 AI 提问，例如：文档的核心观点是什么？"
                        className="w-full px-4 py-3 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all resize-none"
                      />
                    </div>

                    <div className="flex gap-3">
                      <button
                        type="button"
                        onClick={handleAskQuestion}
                        disabled={asking || detail.parseStatus !== "SUCCESS"}
                        className="dp-btn dp-btn-primary flex-1 justify-center py-2.5"
                      >
                        {asking ? "AI 思考中..." : "发送问题"}
                      </button>
                      {streamingQa ? (
                        <button
                          type="button"
                          onClick={() => streamAbortRef.current?.abort()}
                          className="dp-btn dp-btn-danger px-6"
                        >
                          停止
                        </button>
                      ) : null}
                      <button
                        type="button"
                        onClick={() => {
                          setQuestion("");
                          setAnswer("");
                          setCitations([]);
                          setQaErrorMessage("");
                        }}
                        disabled={asking}
                        className="dp-btn dp-btn-secondary px-6"
                      >
                        清空
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          const docId = Number(documentIdParam);
                          if (!Number.isNaN(docId) && docId > 0) {
                            const newSessId = generateSessionId(docId);
                            setSessionId(newSessId);
                            window.localStorage.setItem(buildSessionStorageKey(docId), newSessId);
                            setSessionHint("已开启全新对话。");
                            setAnswer("");
                            setCitations([]);
                          }
                        }}
                        className="dp-btn dp-btn-ghost px-4 text-slate-500 hover:text-slate-800"
                        title="开启新话题，清除上下文"
                      >
                        新对话
                      </button>
                    </div>

                    {qaErrorMessage ? <p className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{qaErrorMessage}</p> : null}

                    {(answer || asking) && (
                      <div className="mt-6 pt-6 border-t border-slate-100">
                        <div className="mb-4 flex items-center justify-between">
                          <h3 className="text-sm font-bold text-slate-700 flex items-center gap-2">
                            <div className="w-6 h-6 rounded bg-blue-100 text-blue-700 flex items-center justify-center text-xs">AI</div>
                            回答内容
                          </h3>
                        </div>

                        {streamingQa && <p className="text-xs text-blue-600 mb-2 animate-pulse">正在生成中...</p>}

                        <div className="prose prose-slate max-w-none text-[0.95rem] leading-relaxed bg-slate-50 p-5 rounded-xl border border-slate-100">
                          {answer ? (
                            answerViewMode === "rendered" && !streamingQa ? (
                              <MarkdownViewer markdown={answer} showViewToggle={false} emptyText="" variant="answer" />
                            ) : (
                              <pre className="whitespace-pre-wrap break-words font-sans">{answer}</pre>
                            )
                          ) : (
                            <span className="text-slate-400">等待回答...</span>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </article>

              <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
                <div className="border-b border-slate-100 pb-4 mb-4">
                  <h2 className="text-lg font-bold text-slate-900">文档正文</h2>
                </div>
                <div className="prose prose-slate max-w-none">
                  <MarkdownViewer markdown={detail.content} emptyText="暂无正文内容" variant="document" />
                </div>
              </article>
            </div>

            <aside className="space-y-6">
              {detail.summary && (
                <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
                  <h2 className="text-base font-bold text-slate-900 mb-3 flex items-center gap-2">
                    <span className="text-blue-600">✧</span> AI 摘要
                  </h2>
                  <div className="text-sm text-slate-600 bg-blue-50/50 p-4 rounded-xl border border-blue-100/50 leading-relaxed">
                    <MarkdownViewer markdown={detail.summary} showViewToggle={false} emptyText="" variant="summary" />
                  </div>
                </article>
              )}

              <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
                <h2 className="text-base font-bold text-slate-900 mb-3 block">证据引用</h2>
                {citations.length === 0 ? (
                  <p className="text-sm text-slate-400 italic">暂无相关引用片段。</p>
                ) : (
                  <ul className="space-y-3">
                    {citations.map((citation, index) => (
                      <li key={`${citation.chunkIndex}-${citation.charStart}-${index}`} className="bg-slate-50 p-3 rounded-lg border border-slate-100 text-sm">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs font-bold text-blue-700 bg-blue-100 px-2 py-0.5 rounded">引用 {index + 1}</span>
                          <span className="text-xs text-slate-400">score: {citation.score.toFixed(2)}</span>
                        </div>
                        <p className="text-slate-700 line-clamp-4 hover:line-clamp-none transition-all cursor-pointer" title="点击查看全部内容">
                          {citation.snippet}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </article>

              <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
                <h2 className="text-base font-bold text-slate-900 mb-3">历史记录</h2>
                {historyLoading ? <p className="text-sm text-slate-400">加载中...</p> : null}
                {!historyLoading && historyList.length === 0 ? (
                  <p className="text-sm text-slate-400 italic">暂无对话记录。</p>
                ) : null}
                {!historyLoading && historyList.length > 0 ? (
                  <ul className="space-y-4">
                    {historyList.map((item) => (
                      <li key={item.id} className="border-l-2 border-slate-200 pl-3 py-1">
                        <p className="text-xs text-slate-400 mb-1">{formatDateTime(item.createTime)}</p>
                        <p className="text-sm font-medium text-slate-800 mb-1 line-clamp-2">{item.question}</p>
                        <p className="text-sm text-slate-500 line-clamp-2">{item.answer}</p>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </article>
            </aside>
          </section>
        </>
      ) : null}
    </main>
  );
}
