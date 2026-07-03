"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import { deleteDocument, getDocumentDetail, type DocumentDetailData } from "@/lib/document-api";
import {
  askDocumentQuestion,
  askDocumentQuestionStream,
  getDocumentQaHistory,
  type DocumentQaCitationItem,
  type DocumentQaHistoryItem,
  type DocumentQaStreamPayload
} from "@/lib/qa-api";
import {
  askDocumentRagQuestion,
  askDocumentRagQuestionStream,
  retrieveDocumentRag,
  type RagCitationItem,
  type RagRetrievalData,
  type RagStreamPayload
} from "@/lib/rag-api";
import { reparseTask } from "@/lib/task-api";

const DETAIL_STATUS_POLLING_TIMEOUT_MS = 120_000;
const TERMINAL_PARSE_STATUS = new Set(["SUCCESS", "FAILED"]);

type QaMode = "legacy" | "rag";

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

function formatScore(score: number | undefined): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "-";
  }
  return score.toFixed(4);
}

function citationQuote(citation: { quoteText?: string; snippet?: string }): string {
  return citation.quoteText?.trim() || citation.snippet?.trim() || "-";
}

function normalizeRagError(message: string): string {
  if (message.includes("无权") || message.includes("不存在")) {
    return "文档不存在或当前账号无权访问，请重新选择自己的文档。";
  }
  if (message.toLowerCase().includes("no evidence")) {
    return "暂未找到足够相关的引用来源，请换一个更贴近文档内容的问题。";
  }
  return message;
}

export default function DocumentDetailPage() {
  const params = useParams<{ documentId: string | string[] }>();
  const router = useRouter();
  const documentIdParam = useMemo(() => normalizeDocumentId(params?.documentId), [params]);

  const [detail, setDetail] = useState<DocumentDetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [hasToken, setHasToken] = useState<boolean | null>(null);

  const [question, setQuestion] = useState("");
  const [qaMode, setQaMode] = useState<QaMode>("rag");
  const [answer, setAnswer] = useState("");
  const [citations, setCitations] = useState<DocumentQaCitationItem[]>([]);
  const [ragCitations, setRagCitations] = useState<RagCitationItem[]>([]);
  const [ragRetrieval, setRagRetrieval] = useState<RagRetrievalData | null>(null);
  const [ragRetrieving, setRagRetrieving] = useState(false);
  const [ragNoEvidence, setRagNoEvidence] = useState(false);
  const [ragFallbackReason, setRagFallbackReason] = useState("");
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
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteMessage, setDeleteMessage] = useState("");

  const [statusPolling, setStatusPolling] = useState(false);
  const [statusPollingStartedAt, setStatusPollingStartedAt] = useState<number | null>(null);

  const streamAbortRef = useRef<AbortController | null>(null);
  const [firstTokenLatencyMs, setFirstTokenLatencyMs] = useState<number | null>(null);

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

  function applyStreamPayload(payload?: DocumentQaStreamPayload): string | null {
    if (!payload) {
      return null;
    }
    let nextSessionId: string | null = null;
    if (payload.sessionId && payload.sessionId.trim()) {
      nextSessionId = payload.sessionId.trim();
      setSessionId(nextSessionId);
      const documentId = Number(documentIdParam);
      if (!Number.isNaN(documentId) && documentId > 0) {
        window.localStorage.setItem(buildSessionStorageKey(documentId), nextSessionId);
      }
    }
    if (Array.isArray(payload.citations)) {
      setCitations(payload.citations);
    }
    return nextSessionId;
  }

  function applyRagStreamPayload(payload?: RagStreamPayload): string | null {
    if (!payload) {
      return null;
    }
    let nextSessionId: string | null = null;
    if (payload.sessionId && payload.sessionId.trim()) {
      nextSessionId = payload.sessionId.trim();
      setSessionId(nextSessionId);
      const documentId = Number(documentIdParam);
      if (!Number.isNaN(documentId) && documentId > 0) {
        window.localStorage.setItem(buildSessionStorageKey(documentId), nextSessionId);
      }
    }
    if (payload.retrieval) {
      setRagRetrieval(payload.retrieval);
    }
    if (Array.isArray(payload.citations)) {
      setRagCitations(payload.citations);
    }
    setRagNoEvidence(Boolean(payload.noEvidence));
    setRagFallbackReason(payload.fallbackReason || "");
    return nextSessionId;
  }

  async function handleRetrieveRag() {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setQaErrorMessage("未检测到登录状态，请先登录后检索");
      return;
    }

    const normalizedQuestion = question.trim();
    if (!normalizedQuestion) {
      setQaErrorMessage("请输入检索问题后再试");
      return;
    }

    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setQaErrorMessage("文档 ID 无效，无法检索");
      return;
    }

    setQaErrorMessage("");
    setRagRetrieving(true);
    setRagRetrieval(null);
    setRagCitations([]);
    setRagNoEvidence(false);
    setRagFallbackReason("");

    try {
      const response = await retrieveDocumentRag({
        documentId,
        query: normalizedQuestion,
        topK: 5
      });
      setRagRetrieval(response.data || null);
      setRagCitations(response.data?.citations || []);
      setRagNoEvidence(Boolean(response.data?.noEvidence));
    } catch (error) {
      const message = error instanceof Error ? error.message : "检索预览失败";
      setQaErrorMessage(normalizeRagError(message));
    } finally {
      setRagRetrieving(false);
    }
  }

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
    setRagCitations([]);
    setRagRetrieval(null);
    setRagNoEvidence(false);
    setRagFallbackReason("");
    setFirstTokenLatencyMs(null);
    const streamStartedAtMs = useStreamingQa ? Date.now() : 0;
    let firstChunkReceived = false;
    let streamedChunkCount = 0;

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

    const askByRagApi = async () => {
      const response = await askDocumentRagQuestion(documentId, {
        question: normalizedQuestion,
        sessionId: normalizedSessionId,
        topK: 5
      });
      setAnswer(response.data?.answer || "");
      setRagRetrieval(response.data?.retrieval || null);
      setRagCitations(response.data?.citations || []);
      setRagNoEvidence(Boolean(response.data?.noEvidence));
      setRagFallbackReason(response.data?.fallbackReason || "");
      nextSessionId = (response.data?.sessionId || "").trim() || normalizedSessionId;
      setSessionHint("当前检索会话已续用，后续提问会自动携带上下文。");
    };

    try {
      if (qaMode === "rag" && useStreamingQa) {
        const controller = new AbortController();
        streamAbortRef.current = controller;
        await askDocumentRagQuestionStream(
          documentId,
          {
            question: normalizedQuestion,
            sessionId: normalizedSessionId,
            topK: 5
          },
          {
            onMeta: (payload) => {
              const payloadSessionId = applyRagStreamPayload(payload);
              if (payloadSessionId) {
                nextSessionId = payloadSessionId;
              }
            },
            onRetrieval: (payload) => {
              setRagRetrieval(payload);
              setRagNoEvidence(Boolean(payload.noEvidence));
            },
            onCitation: (payload) => {
              setRagCitations((current) => [...current, payload]);
            },
            onChunk: (chunk) => {
              if (!chunk) {
                return;
              }
              streamedChunkCount += 1;
              if (!firstChunkReceived && streamStartedAtMs > 0) {
                firstChunkReceived = true;
                setFirstTokenLatencyMs(Math.max(0, Date.now() - streamStartedAtMs));
              }
              setAnswer((prev) => prev + chunk);
            },
            onDone: (payload) => {
              const payloadSessionId = applyRagStreamPayload(payload);
              if (payloadSessionId) {
                nextSessionId = payloadSessionId;
              }
              setStreamingQa(false);
              setSessionHint("当前检索会话已续用，后续提问会自动携带上下文。");
            },
            onError: (message) => {
              setQaErrorMessage(normalizeRagError(message || "检索增强问答失败"));
              setStreamingQa(false);
            }
          },
          controller.signal
        );
        setStreamingQa(false);
      } else if (qaMode === "legacy" && useStreamingQa) {
        const controller = new AbortController();
        streamAbortRef.current = controller;
        await askDocumentQuestionStream(
          {
            documentId,
            question: normalizedQuestion,
            sessionId: normalizedSessionId
          },
          {
            onMeta: (payload) => {
              const payloadSessionId = applyStreamPayload(payload);
              if (payloadSessionId) {
                nextSessionId = payloadSessionId;
              }
            },
            onChunk: (chunk) => {
              if (!chunk) {
                return;
              }
              streamedChunkCount += 1;
              if (!firstChunkReceived && streamStartedAtMs > 0) {
                firstChunkReceived = true;
                setFirstTokenLatencyMs(Math.max(0, Date.now() - streamStartedAtMs));
              }
              setAnswer((prev) => prev + chunk);
            },
            onDone: (payload) => {
              const payloadSessionId = applyStreamPayload(payload);
              if (payloadSessionId) {
                nextSessionId = payloadSessionId;
              }
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
      } else if (qaMode === "rag") {
        await askByRagApi();
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
          if (qaMode === "rag") {
            await askByRagApi();
          } else {
            await askByNormalApi();
          }
          setSessionId(nextSessionId);
          window.localStorage.setItem(buildSessionStorageKey(documentId), nextSessionId);
          await fetchQaHistory(documentId);
          setQaErrorMessage("");
          setSessionHint(`实时输出中断（${streamErrorMessage}），已自动切换为非流式问答。`);
          return;
        } catch (fallbackError) {
          const fallbackMessage = fallbackError instanceof Error ? fallbackError.message : "普通问答也失败";
          if (streamedChunkCount > 0) {
            setQaErrorMessage(`实时输出中断：${streamErrorMessage}；切换普通问答后仍未完成：${fallbackMessage}。已保留当前已生成内容。`);
          } else {
            setQaErrorMessage(`实时输出中断：${streamErrorMessage}；切换普通问答后仍未完成：${fallbackMessage}`);
          }
        }
      } else {
        setQaErrorMessage(streamErrorMessage);
      }
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
      setRagCitations([]);
      setRagRetrieval(null);
      setRagNoEvidence(false);
      setRagFallbackReason("");
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

  async function handleDeleteDocument() {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setDeleteMessage("未检测到登录状态，请先登录后删除。");
      return;
    }

    const documentId = Number(documentIdParam);
    if (!documentIdParam || Number.isNaN(documentId) || documentId <= 0) {
      setDeleteMessage("文档 ID 无效，无法删除。");
      return;
    }

    setDeleting(true);
    setDeleteMessage("");
    try {
      await deleteDocument(documentId);
      router.push("/documents");
    } catch (error) {
      const message = error instanceof Error ? error.message : "删除文档失败";
      setDeleteMessage(message);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8 flex items-center justify-between">
        <div>
          <p className="text-sm font-bold text-slate-400 tracking-wider uppercase mb-1">Document Space</p>
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
          {detail ? (
            confirmingDelete ? (
              <div className="flex items-center gap-2 rounded-xl border border-red-100 bg-red-50 px-3 py-2">
                <span className="text-sm font-semibold text-red-700">确认删除？</span>
                <button
                  type="button"
                  onClick={handleDeleteDocument}
                  disabled={deleting}
                  className="text-sm font-semibold text-red-700 hover:text-red-800 disabled:opacity-50"
                >
                  {deleting ? "删除中..." : "确认"}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(false)}
                  disabled={deleting}
                  className="text-sm font-medium text-slate-500 hover:text-slate-700"
                >
                  取消
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => {
                  setConfirmingDelete(true);
                  setDeleteMessage("");
                }}
                disabled={loading || deleting}
                className="dp-btn dp-btn-secondary text-red-600 hover:text-red-700"
              >
                删除文档
              </button>
            )
          ) : null}
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
          {deleteMessage ? <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">{deleteMessage}</section> : null}

          <section className="grid gap-6 lg:grid-cols-[1fr_350px]">
            <div className="space-y-6">
              <article className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
                <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                  <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                    <span className="w-2 h-6 bg-blue-600 rounded-sm"></span> 文档问答与引用来源
                  </h2>
                  <div className="flex flex-wrap items-center justify-end gap-3 text-sm text-slate-600">
                    <div className="inline-flex rounded-xl border border-slate-200 bg-white p-1">
                      <button
                        type="button"
                        onClick={() => setQaMode("rag")}
                        disabled={asking}
                        className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                          qaMode === "rag" ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-50"
                        }`}
                      >
                        检索问答
                      </button>
                      <button
                        type="button"
                        onClick={() => setQaMode("legacy")}
                        disabled={asking}
                        className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                          qaMode === "legacy" ? "bg-blue-600 text-white" : "text-slate-600 hover:bg-slate-50"
                        }`}
                      >
                        普通问答
                      </button>
                    </div>
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
                      {qaMode === "rag" ? (
                        <button
                          type="button"
                          onClick={handleRetrieveRag}
                          disabled={asking || ragRetrieving || detail.parseStatus !== "SUCCESS"}
                          className="dp-btn dp-btn-secondary px-5"
                        >
                          {ragRetrieving ? "检索中..." : "检索预览"}
                        </button>
                      ) : null}
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
                          setRagCitations([]);
                          setRagRetrieval(null);
                          setRagNoEvidence(false);
                          setRagFallbackReason("");
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
                            setRagCitations([]);
                            setRagRetrieval(null);
                            setRagNoEvidence(false);
                            setRagFallbackReason("");
                          }
                        }}
                        className="dp-btn dp-btn-ghost px-4 text-slate-500 hover:text-slate-800"
                        title="开启新话题，清除上下文"
                      >
                        新对话
                      </button>
                    </div>

                    {qaErrorMessage ? <p className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{qaErrorMessage}</p> : null}
                    {qaMode === "rag" && ragNoEvidence ? (
                      <p className="bg-amber-50 text-amber-800 p-3 rounded-lg text-sm">
                        暂未找到足够相关的引用来源{ragFallbackReason ? `：${ragFallbackReason}` : "。"}
                      </p>
                    ) : null}

                    {(answer || asking) && (
                      <div className="mt-6 pt-6 border-t border-slate-100">
                        <div className="mb-4 flex items-center justify-between">
                          <h3 className="text-sm font-bold text-slate-700 flex items-center gap-2">
                            <div className="w-6 h-6 rounded bg-blue-100 text-blue-700 flex items-center justify-center text-xs">AI</div>
                            回答内容
                          </h3>
                        </div>

                        {streamingQa && <p className="text-xs text-blue-600 mb-2 animate-pulse">正在生成中...</p>}
                        {firstTokenLatencyMs !== null ? (
                          <p className="mb-2 text-xs text-slate-500">首字延迟：{firstTokenLatencyMs} ms</p>
                        ) : null}

                        <div className="bg-slate-50 p-5 rounded-xl border border-slate-100 min-h-[120px]">
                          {answer ? (
                            <MarkdownViewer markdown={answer} showViewToggle={false} emptyText="" variant="answer" mode="inline" />
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
                <h2 className="text-base font-bold text-slate-900 mb-3 block">引用来源</h2>
                {qaMode === "rag" ? (
                  <div className="space-y-4">
                    {ragRetrieval ? (
                      <div className="rounded-xl border border-blue-100 bg-blue-50/60 p-3 text-xs text-slate-600">
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-blue-700">检索命中</span>
                          <span>{ragRetrieval.hits?.length ?? ragRetrieval.hitCount ?? 0} 条</span>
                        </div>
                        <p className="mt-1">TopK: {ragRetrieval.topK ?? "-"} / Index: {ragRetrieval.indexVersion ?? "-"}</p>
                      </div>
                    ) : null}

                    {ragCitations.length === 0 && !ragRetrieval?.hits?.length ? (
                      <p className="text-sm text-slate-400 italic">暂无引用来源或召回片段。</p>
                    ) : null}

                    {ragCitations.length > 0 ? (
                      <ul className="space-y-3">
                        {ragCitations.map((citation, index) => (
                          <li key={`${citation.chunkId}-${citation.chunkIndex}-${index}`} className="bg-slate-50 p-3 rounded-lg border border-slate-100 text-sm">
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-xs font-bold text-blue-700 bg-blue-100 px-2 py-0.5 rounded">引用 {citation.index ?? index + 1}</span>
                              <span className="text-xs text-slate-400">score: {formatScore(citation.score)}</span>
                            </div>
                            <p className="text-xs text-slate-500 mb-1">
                              chunk #{citation.chunkIndex ?? "-"} · version {citation.indexVersion ?? "-"}
                            </p>
                            <p className="text-slate-800 line-clamp-4 hover:line-clamp-none transition-all cursor-pointer" title="精确引用原文">
                              {citationQuote(citation)}
                            </p>
                            {citation.quoteText && citation.snippet && citation.quoteText !== citation.snippet ? (
                              <p className="mt-2 line-clamp-3 text-xs text-slate-500 hover:line-clamp-none">
                                上下文：{citation.snippet}
                              </p>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                    ) : null}

                    {ragRetrieval?.hits && ragRetrieval.hits.length > 0 ? (
                      <details className="rounded-xl border border-slate-100 bg-white p-3">
                        <summary className="cursor-pointer text-sm font-semibold text-slate-700">查看召回片段</summary>
                        <ol className="mt-3 space-y-3">
                          {ragRetrieval.hits.map((hit, index) => (
                            <li key={`${hit.vectorId || hit.chunkId}-${index}`} className="rounded-lg border border-emerald-100 bg-emerald-50/60 p-3 text-xs text-slate-700">
                              <div className="mb-2 flex items-center justify-between gap-2">
                                <span className="font-semibold text-emerald-700">片段 {index + 1}</span>
                                <span>score: {formatScore(hit.score)}</span>
                              </div>
                              <p className="line-clamp-5 hover:line-clamp-none">{hit.content || "-"}</p>
                            </li>
                          ))}
                        </ol>
                      </details>
                    ) : null}
                  </div>
                ) : citations.length === 0 ? (
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
                {!historyLoading && historyErrorMessage ? (
                  <p className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{historyErrorMessage}</p>
                ) : null}
                {!historyLoading && historyList.length === 0 ? (
                  <p className="text-sm text-slate-400 italic">暂无对话记录。</p>
                ) : null}
                {!historyLoading && historyList.length > 0 ? (
                  <ul className="space-y-4">
                    {historyList.map((item) => (
                      <li key={item.id} className="border-l-2 border-slate-200 pl-3 py-1">
                        <p className="text-xs text-slate-400 mb-1">{formatDateTime(item.createTime)}</p>
                        <p className="text-sm font-medium text-slate-800 mb-1 line-clamp-2">{item.question}</p>
                        <div className="rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
                          <MarkdownViewer
                            markdown={item.answer}
                            showViewToggle={false}
                            emptyText=""
                            variant="history"
                            mode="inline"
                          />
                        </div>
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
