"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import { listDocuments, type DocumentListItem } from "@/lib/document-api";
import {
  addKnowledgeBaseDocuments,
  askKnowledgeBaseRagQuestion,
  createKnowledgeBase,
  getKnowledgeBaseDetail,
  listKnowledgeBases,
  removeKnowledgeBaseDocument,
  retrieveKnowledgeBaseRag,
  type KnowledgeBaseCitationItem,
  type KnowledgeBaseDetailData,
  type KnowledgeBaseItem,
  type KnowledgeBaseQaData,
  type KnowledgeBaseRetrievalData,
} from "@/lib/knowledge-base-api";

const DEFAULT_QUESTION = "请基于资料集内容，归纳这些文档的核心主题和关键结论。";

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

function statusBadge(status?: string): string {
  if (status === "SUCCESS" || status === "ACTIVE") {
    return "dp-badge dp-badge-success";
  }
  if (status === "FAILED" || status === "REMOVED") {
    return "dp-badge dp-badge-danger";
  }
  if (status === "PENDING") {
    return "dp-badge dp-badge-warning";
  }
  return "dp-badge dp-badge-info";
}

function formatScore(score?: number): string {
  if (typeof score !== "number" || Number.isNaN(score)) {
    return "-";
  }
  return score.toFixed(4);
}

function formatScoreDetails(item: {
  vectorScore?: number;
  keywordScore?: number;
  fusedScore?: number;
  rerankScore?: number;
}): string {
  const parts = [
    ["vector", item.vectorScore],
    ["keyword", item.keywordScore],
    ["fused", item.fusedScore],
    ["rerank", item.rerankScore],
  ]
    .filter(([, score]) => typeof score === "number" && !Number.isNaN(score))
    .map(([label, score]) => `${label}: ${(score as number).toFixed(4)}`);
  return parts.join(" / ");
}

function formatHitCounts(counts?: Record<string, number>): string {
  const entries = Object.entries(counts || {});
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .map(([documentId, count]) => `#${documentId}: ${count}`)
    .join(" / ");
}

function buildSessionId(knowledgeBaseId: number): string {
  return `kb${knowledgeBaseId}-${Date.now().toString(36)}`;
}

export default function KnowledgeBasesPage() {
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<
    number | null
  >(null);
  const [detail, setDetail] = useState<KnowledgeBaseDetailData | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<number[]>([]);
  const [mutationMessage, setMutationMessage] = useState("");
  const [mutating, setMutating] = useState(false);

  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [creating, setCreating] = useState(false);

  const [question, setQuestion] = useState(DEFAULT_QUESTION);
  const [answer, setAnswer] = useState("");
  const [retrieval, setRetrieval] = useState<KnowledgeBaseRetrievalData | null>(
    null,
  );
  const [qaResult, setQaResult] = useState<KnowledgeBaseQaData | null>(null);
  const [citations, setCitations] = useState<KnowledgeBaseCitationItem[]>([]);
  const [qaError, setQaError] = useState("");
  const [asking, setAsking] = useState(false);
  const [retrieving, setRetrieving] = useState(false);
  const [noEvidence, setNoEvidence] = useState(false);
  const [sessionId, setSessionId] = useState("");

  const loadKnowledgeBases = useCallback(
    async (preferredId?: number) => {
      const token = getToken();
      if (!token) {
        setHasToken(false);
        setKnowledgeBases([]);
        setSelectedKnowledgeBaseId(null);
        setDetail(null);
        setLoading(false);
        return;
      }

      setHasToken(true);
      setErrorMessage("");
      try {
        const [kbResponse, docResponse] = await Promise.all([
          listKnowledgeBases(),
          listDocuments({ pageNo: 1, pageSize: 100 }),
        ]);
        const nextKnowledgeBases = kbResponse.data || [];
        setKnowledgeBases(nextKnowledgeBases);
        setDocuments(docResponse.data?.records || []);

        const nextSelected =
          preferredId ||
          selectedKnowledgeBaseId ||
          nextKnowledgeBases[0]?.id ||
          null;
        setSelectedKnowledgeBaseId(nextSelected);
      } catch (error) {
        const message =
          error instanceof Error ? error.message : "加载知识库失败";
        setErrorMessage(message);
        setKnowledgeBases([]);
        setDocuments([]);
        setSelectedKnowledgeBaseId(null);
        setDetail(null);
      } finally {
        setLoading(false);
      }
    },
    [selectedKnowledgeBaseId],
  );

  const loadDetail = useCallback(async (knowledgeBaseId: number) => {
    setDetailLoading(true);
    setMutationMessage("");
    setQaError("");
    try {
      const response = await getKnowledgeBaseDetail(knowledgeBaseId);
      setDetail(response.data);
      setSessionId((current) => current || buildSessionId(knowledgeBaseId));
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "加载知识库详情失败";
      setQaError(message);
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    loadKnowledgeBases();
  }, [loadKnowledgeBases]);

  useEffect(() => {
    if (selectedKnowledgeBaseId) {
      loadDetail(selectedKnowledgeBaseId);
    } else {
      setDetail(null);
    }
  }, [loadDetail, selectedKnowledgeBaseId]);

  const activeDocumentIds = useMemo(
    () => new Set((detail?.documents || []).map((item) => item.documentId)),
    [detail],
  );

  const parsedDocuments = useMemo(
    () => documents.filter((item) => item.parseStatus === "SUCCESS"),
    [documents],
  );

  const candidateDocuments = useMemo(
    () =>
      parsedDocuments.filter((item) => !activeDocumentIds.has(item.documentId)),
    [activeDocumentIds, parsedDocuments],
  );

  async function handleCreateKnowledgeBase() {
    const name = newName.trim();
    if (!name) {
      setErrorMessage("请输入知识库名称");
      return;
    }

    setCreating(true);
    setErrorMessage("");
    try {
      const response = await createKnowledgeBase({
        name,
        description: newDescription.trim(),
      });
      setNewName("");
      setNewDescription("");
      await loadKnowledgeBases(response.data?.id);
      setMutationMessage("知识库已创建。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "创建知识库失败";
      setErrorMessage(message);
    } finally {
      setCreating(false);
    }
  }

  async function handleAddDocuments() {
    if (!selectedKnowledgeBaseId || selectedDocumentIds.length === 0) {
      setMutationMessage("请选择要加入知识库的已解析文档。");
      return;
    }

    setMutating(true);
    setMutationMessage("");
    try {
      const response = await addKnowledgeBaseDocuments(
        selectedKnowledgeBaseId,
        selectedDocumentIds,
      );
      setSelectedDocumentIds([]);
      setMutationMessage(
        `已更新知识库文档，当前有效文档数：${response.data?.activeDocumentCount ?? "-"}`,
      );
      await loadDetail(selectedKnowledgeBaseId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "添加文档失败";
      setMutationMessage(message);
    } finally {
      setMutating(false);
    }
  }

  async function handleRemoveDocument(documentId: number) {
    if (!selectedKnowledgeBaseId) {
      return;
    }
    setMutating(true);
    setMutationMessage("");
    try {
      const response = await removeKnowledgeBaseDocument(
        selectedKnowledgeBaseId,
        documentId,
      );
      setMutationMessage(
        `已移除文档，当前有效文档数：${response.data?.activeDocumentCount ?? "-"}`,
      );
      await loadDetail(selectedKnowledgeBaseId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "移除文档失败";
      setMutationMessage(message);
    } finally {
      setMutating(false);
    }
  }

  async function handleRetrieve() {
    if (!selectedKnowledgeBaseId) {
      return;
    }
    const query = question.trim();
    if (!query) {
      setQaError("请输入检索问题");
      return;
    }

    setRetrieving(true);
    setQaError("");
    setRetrieval(null);
    setQaResult(null);
    setCitations([]);
    setNoEvidence(false);
    try {
      const response = await retrieveKnowledgeBaseRag(selectedKnowledgeBaseId, {
        query,
        topK: 6,
      });
      setRetrieval(response.data || null);
      setCitations(response.data?.citations || []);
      setNoEvidence(Boolean(response.data?.noEvidence));
    } catch (error) {
      const message = error instanceof Error ? error.message : "知识库检索失败";
      setQaError(message);
    } finally {
      setRetrieving(false);
    }
  }

  async function handleAsk() {
    if (!selectedKnowledgeBaseId) {
      return;
    }
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion) {
      setQaError("请输入问题");
      return;
    }

    const normalizedSessionId =
      sessionId || buildSessionId(selectedKnowledgeBaseId);
    setSessionId(normalizedSessionId);
    setAsking(true);
    setQaError("");
    setAnswer("");
    setRetrieval(null);
    setQaResult(null);
    setCitations([]);
    setNoEvidence(false);
    try {
      const response = await askKnowledgeBaseRagQuestion(
        selectedKnowledgeBaseId,
        {
          question: normalizedQuestion,
          topK: 6,
          sessionId: normalizedSessionId,
        },
      );
      setAnswer(response.data?.answer || "");
      setQaResult(response.data || null);
      setRetrieval(response.data?.retrieval || null);
      setCitations(response.data?.citations || []);
      setNoEvidence(Boolean(response.data?.noEvidence));
      setSessionId(response.data?.sessionId || normalizedSessionId);
    } catch (error) {
      const message = error instanceof Error ? error.message : "知识库问答失败";
      setQaError(message);
    } finally {
      setAsking(false);
    }
  }

  function toggleDocument(documentId: number) {
    setSelectedDocumentIds((current) => {
      if (current.includes(documentId)) {
        return current.filter((item) => item !== documentId);
      }
      return [...current, documentId];
    });
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <section className="dp-hero dp-hero-product flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="dp-eyebrow">Knowledge Workspace</p>
          <h1 className="mt-2 text-3xl font-bold text-slate-950">
            多文档知识库
          </h1>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
            将多份已解析文档组织为资料集，围绕资料集进行检索、问答和引用来源查看。
            页面保留必要的运行概览，便于确认回答依据来自哪些文档。
          </p>
        </div>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => loadKnowledgeBases()}
            disabled={loading}
            className="dp-btn dp-btn-secondary"
          >
            {loading ? "刷新中..." : "刷新"}
          </button>
          <Link href="/upload" className="dp-btn dp-btn-primary">
            上传文档
          </Link>
        </div>
      </section>

      {hasToken === false ? (
        <section className="dp-card grid gap-5 lg:grid-cols-[1fr_240px] lg:items-center">
          <div>
            <p className="dp-eyebrow">Sign in required</p>
            <h2 className="mt-2 text-2xl font-bold text-slate-950">
              登录后管理知识库
            </h2>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
              登录后可创建知识库、加入已解析文档，并围绕资料集进行检索问答与引用来源查看。
            </p>
          </div>
          <Link href="/login" className="dp-btn dp-btn-primary">
            前往登录
          </Link>
        </section>
      ) : null}

      {errorMessage && hasToken !== false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">
          {errorMessage}
        </section>
      ) : null}

      {hasToken !== false ? (
      <section className="grid gap-6 lg:grid-cols-[330px_1fr]">
        <aside className="space-y-6">
          <article className="dp-card">
            <h2 className="text-base font-bold text-slate-900 mb-4">
              创建知识库
            </h2>
            <div className="space-y-3">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">
                  名称
                </span>
                <input
                  value={newName}
                  onChange={(event) => setNewName(event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="例如：产品需求资料集"
                />
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">
                  描述
                </span>
                <textarea
                  value={newDescription}
                  onChange={(event) => setNewDescription(event.target.value)}
                  rows={3}
                  className="w-full resize-none rounded-lg border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="用于归档多份文档的用途说明"
                />
              </label>
              <button
                type="button"
                onClick={handleCreateKnowledgeBase}
                disabled={creating}
                className="dp-btn dp-btn-primary w-full"
              >
                {creating ? "创建中..." : "创建知识库"}
              </button>
            </div>
          </article>

          <article className="dp-card">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900">知识库列表</h2>
              <span className="text-xs text-slate-400">
                {knowledgeBases.length} 个
              </span>
            </div>
            {loading ? (
              <p className="text-sm text-slate-400">加载中...</p>
            ) : null}
            {!loading && knowledgeBases.length === 0 ? (
              <p className="text-sm text-slate-400">
                暂无知识库，先创建一个用于多文档问答。
              </p>
            ) : null}
            <ul className="space-y-2">
              {knowledgeBases.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedKnowledgeBaseId(item.id);
                      setAnswer("");
                      setRetrieval(null);
                      setQaResult(null);
                      setCitations([]);
                      setNoEvidence(false);
                      setSessionId(buildSessionId(item.id));
                    }}
                    className={`w-full rounded-xl border p-3 text-left transition-all ${
                      selectedKnowledgeBaseId === item.id
                        ? "border-blue-200 bg-blue-50"
                        : "border-slate-100 bg-slate-50 hover:border-blue-100 hover:bg-white"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-slate-900">
                        {item.name}
                      </span>
                      <span className={statusBadge(item.status)}>
                        {item.status || "ACTIVE"}
                      </span>
                    </div>
                    <p className="mt-1 line-clamp-2 text-xs text-slate-500">
                      {item.description || "暂无描述"}
                    </p>
                    <p className="mt-2 text-[11px] text-slate-400">
                      {formatDateTime(item.createTime)}
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          </article>
        </aside>

        <section className="space-y-6">
          {!selectedKnowledgeBaseId ? (
            <article className="dp-card p-10 text-center text-slate-500">
              请选择或创建一个知识库。
            </article>
          ) : null}

          {selectedKnowledgeBaseId ? (
            <>
              <article className="dp-card">
                <div className="mb-5 flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">
                      {detail?.name || "知识库详情"}
                    </h2>
                    <p className="mt-1 text-sm text-slate-500">
                      {detail?.description || "暂无描述"}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() =>
                      selectedKnowledgeBaseId &&
                      loadDetail(selectedKnowledgeBaseId)
                    }
                    disabled={detailLoading}
                    className="dp-btn dp-btn-secondary"
                  >
                    {detailLoading ? "加载中..." : "刷新详情"}
                  </button>
                </div>

                {mutationMessage ? (
                  <p className="mb-4 rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-700">
                    {mutationMessage}
                  </p>
                ) : null}

                <div className="mb-6 grid gap-3 md:grid-cols-3">
                  <div className="dp-kpi-card">
                    <p className="dp-kpi-label">已加入文档</p>
                    <p className="dp-kpi-value text-base">
                      {detail?.documents?.length ?? 0}
                    </p>
                  </div>
                  <div className="dp-kpi-card">
                    <p className="dp-kpi-label">可选文档</p>
                    <p className="dp-kpi-value text-base">
                      {candidateDocuments.length}
                    </p>
                  </div>
                  <div className="dp-kpi-card">
                    <p className="dp-kpi-label">会话状态</p>
                    <p className="dp-kpi-value text-base">
                      {sessionId ? "ready" : "new"}
                    </p>
                  </div>
                </div>

                <div className="grid gap-6 xl:grid-cols-[1fr_1fr]">
                  <div>
                    <h3 className="mb-3 text-sm font-bold text-slate-700">
                      已加入文档
                    </h3>
                    {detailLoading ? (
                      <p className="text-sm text-slate-400">正在加载文档...</p>
                    ) : null}
                    {!detailLoading &&
                    (!detail?.documents || detail.documents.length === 0) ? (
                      <p className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-400">
                        还没有文档。请从右侧选择解析成功的文档加入。
                      </p>
                    ) : null}
                    <ul className="space-y-3">
                      {(detail?.documents || []).map((item) => (
                        <li
                          key={`${item.knowledgeBaseId}-${item.documentId}`}
                          className="rounded-xl border border-slate-100 bg-slate-50 p-4"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <Link
                                href={`/documents/${item.documentId}`}
                                className="font-semibold text-slate-900 hover:text-blue-700"
                              >
                                {item.documentTitle ||
                                  `文档 #${item.documentId}`}
                              </Link>
                              <p className="mt-1 text-xs text-slate-500">
                                documentId: {item.documentId}
                              </p>
                            </div>
                            <span className={statusBadge(item.parseStatus)}>
                              {item.parseStatus || "-"}
                            </span>
                          </div>
                          <button
                            type="button"
                            onClick={() =>
                              handleRemoveDocument(item.documentId)
                            }
                            disabled={mutating}
                            className="mt-3 text-xs font-semibold text-red-600 hover:text-red-700"
                          >
                            移出知识库
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div>
                    <h3 className="mb-3 text-sm font-bold text-slate-700">
                      添加已解析文档
                    </h3>
                    {candidateDocuments.length === 0 ? (
                      <p className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-400">
                        暂无可加入的解析成功文档。
                      </p>
                    ) : (
                      <div className="max-h-80 space-y-2 overflow-auto rounded-xl border border-slate-100 bg-slate-50 p-3">
                        {candidateDocuments.map((item) => (
                          <label
                            key={item.documentId}
                            className="flex cursor-pointer items-start gap-3 rounded-lg bg-white p-3 text-sm hover:bg-blue-50"
                          >
                            <input
                              type="checkbox"
                              checked={selectedDocumentIds.includes(
                                item.documentId,
                              )}
                              onChange={() => toggleDocument(item.documentId)}
                              className="mt-1"
                            />
                            <span className="min-w-0">
                              <span className="block truncate font-semibold text-slate-800">
                                {item.fileName || `文档 #${item.documentId}`}
                              </span>
                              <span className="block text-xs text-slate-500">
                                {item.summary || "暂无摘要"}
                              </span>
                            </span>
                          </label>
                        ))}
                      </div>
                    )}
                    <button
                      type="button"
                      onClick={handleAddDocuments}
                      disabled={mutating || selectedDocumentIds.length === 0}
                      className="dp-btn dp-btn-primary mt-4 w-full"
                    >
                      {mutating
                        ? "更新中..."
                        : `添加选中文档 (${selectedDocumentIds.length})`}
                    </button>
                  </div>
                </div>
              </article>

              <article className="dp-card">
                <div className="mb-5 flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900">
                      知识库问答
                    </h2>
                    <p className="mt-1 text-sm text-slate-500">
                      基于资料集检索相关内容，并生成带引用来源的回答。
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      if (selectedKnowledgeBaseId) {
                        setSessionId(buildSessionId(selectedKnowledgeBaseId));
                        setAnswer("");
                        setRetrieval(null);
                        setQaResult(null);
                        setCitations([]);
                        setNoEvidence(false);
                      }
                    }}
                    className="dp-btn dp-btn-secondary"
                  >
                    新对话
                  </button>
                </div>

                <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  rows={3}
                  className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="输入跨文档问题..."
                />

                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    type="button"
                    onClick={handleAsk}
                    disabled={
                      asking ||
                      !detail?.documents ||
                      detail.documents.length === 0
                    }
                    className="dp-btn dp-btn-primary px-6"
                  >
                    {asking ? "回答中..." : "生成回答"}
                  </button>
                  <button
                    type="button"
                    onClick={handleRetrieve}
                    disabled={
                      retrieving ||
                      !detail?.documents ||
                      detail.documents.length === 0
                    }
                    className="dp-btn dp-btn-secondary px-6"
                  >
                    {retrieving ? "检索中..." : "查看引用来源"}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setAnswer("");
                      setRetrieval(null);
                      setQaResult(null);
                      setCitations([]);
                      setQaError("");
                      setNoEvidence(false);
                    }}
                    className="dp-btn dp-btn-ghost px-6"
                  >
                    清空结果
                  </button>
                </div>

                {qaError ? (
                  <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
                    {qaError}
                  </p>
                ) : null}
                {noEvidence ? (
                  <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    暂未找到足够相关的引用来源。
                  </p>
                ) : null}

                {retrieval || qaResult ? (
                  <div className="mt-5 rounded-xl border border-blue-100 bg-blue-50/50 p-4">
                    <div className="mb-3 flex flex-col gap-1 md:flex-row md:items-end md:justify-between">
                      <div>
                        <p className="text-xs font-bold uppercase tracking-wide text-blue-700">
                          Retrieval Overview
                        </p>
                        <h3 className="text-base font-bold text-slate-950">
                          本次回答依据
                        </h3>
                      </div>
                      <span className="dp-badge dp-badge-info">
                        Session {sessionId || "-"}
                      </span>
                    </div>
                    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                    <div className="dp-kpi-card">
                      <p className="dp-kpi-label">检索通道</p>
                      <p className="dp-kpi-value text-base">
                        {retrieval?.provider || "-"}
                      </p>
                      <p className="mt-1 truncate text-xs text-slate-500">
                        {retrieval?.collection || "索引集合 -"}
                      </p>
                    </div>
                    <div className="dp-kpi-card">
                      <p className="dp-kpi-label">引用来源</p>
                      <p className="dp-kpi-value text-base">
                        {retrieval?.hits?.length || 0} / {citations.length}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">片段 / 引用</p>
                    </div>
                    <div className="dp-kpi-card">
                      <p className="dp-kpi-label">回答引擎</p>
                      <p className="dp-kpi-value text-base">
                        {qaResult?.answerProvider || "-"}
                      </p>
                      <p className="mt-1 truncate text-xs text-slate-500">
                        {qaResult?.answerModel || "模型 -"}
                      </p>
                    </div>
                    <div className="dp-kpi-card">
                      <p className="dp-kpi-label">生成次数</p>
                      <p className="dp-kpi-value text-base">
                        {qaResult?.modelCallCount ?? "-"}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        来源不足: {noEvidence ? "是" : "否"}
                      </p>
                    </div>
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs leading-5 text-slate-600 md:col-span-2 xl:col-span-4">
                      来源文档分布：{formatHitCounts(retrieval?.documentHitCounts)}
                      <span className="mx-2 text-slate-300">/</span>
                      模式：{retrieval?.retrievalMode || "vector"}
                      <span className="mx-2 text-slate-300">/</span>
                      Rerank：{retrieval?.rerankApplied ? retrieval.rerankModel || "enabled" : "未启用"}
                    </div>
                    </div>
                  </div>
                ) : null}

                {answer ? (
                  <div className="mt-6 rounded-xl border border-slate-100 bg-slate-50 p-5">
                    <h3 className="mb-3 text-sm font-bold text-slate-700">
                      回答
                    </h3>
                    <MarkdownViewer
                      markdown={answer}
                      showViewToggle={false}
                      emptyText=""
                      variant="answer"
                      mode="inline"
                    />
                  </div>
                ) : null}

                {retrieval || citations.length > 0 ? (
                  <div className="mt-6 grid gap-4 xl:grid-cols-[1fr_1fr]">
                    <div className="rounded-xl border border-blue-100 bg-blue-50/60 p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <h3 className="text-sm font-bold text-blue-800">
                          召回片段
                        </h3>
                        <span className="text-xs text-blue-700">
                          {retrieval?.hits?.length || 0} 条
                        </span>
                      </div>
                      {retrieval?.hits && retrieval.hits.length > 0 ? (
                        <ol className="space-y-3">
                          {retrieval.hits.map((hit, index) => (
                            <li
                              key={`${hit.vectorId || hit.chunkId}-${index}`}
                              className="rounded-lg border border-white bg-white p-3 text-sm"
                            >
                              <div className="mb-2 flex items-center justify-between gap-2">
                                <p className="font-semibold text-slate-800">
                                  {hit.documentTitle ||
                                    `文档 #${hit.documentId}`}
                                </p>
                                <span className="text-xs text-slate-500">
                                  {formatScore(hit.score)}
                                </span>
                              </div>
                              <p className="line-clamp-4 text-xs leading-5 text-slate-600 hover:line-clamp-none">
                                {hit.content || "-"}
                              </p>
                              {formatScoreDetails(hit) ? (
                                <p className="mt-2 text-[11px] text-slate-400">
                                  {formatScoreDetails(hit)}
                                </p>
                              ) : null}
                            </li>
                          ))}
                        </ol>
                      ) : (
                        <p className="text-sm text-blue-700">暂无召回片段。</p>
                      )}
                    </div>

                    <div className="rounded-xl border border-slate-100 bg-white p-4">
                      <div className="mb-3 flex items-center justify-between">
                        <h3 className="text-sm font-bold text-slate-800">
                          引用来源
                        </h3>
                        <span className="text-xs text-slate-500">
                          {citations.length} 条
                        </span>
                      </div>
                      {citations.length > 0 ? (
                        <ul className="space-y-3">
                          {citations.map((citation, index) => (
                            <li
                              key={`${citation.documentId}-${citation.chunkId}-${index}`}
                              className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm"
                            >
                              <div className="mb-2 flex items-center justify-between gap-2">
                                <p className="font-semibold text-slate-800">
                                  {citation.documentTitle ||
                                    `文档 #${citation.documentId}`}
                                </p>
                                <span className="text-xs text-slate-500">
                                  {formatScore(citation.score)}
                                </span>
                              </div>
                              <p className="line-clamp-4 text-xs leading-5 text-slate-600 hover:line-clamp-none">
                                {citation.snippet || "-"}
                              </p>
                              {formatScoreDetails(citation) ? (
                                <p className="mt-2 text-[11px] text-slate-400">
                                  {formatScoreDetails(citation)}
                                </p>
                              ) : null}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="text-sm text-slate-400">暂无引用来源。</p>
                      )}
                    </div>
                  </div>
                ) : null}
              </article>
            </>
          ) : null}
        </section>
      </section>
      ) : null}
    </main>
  );
}
