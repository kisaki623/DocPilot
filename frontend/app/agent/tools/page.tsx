"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "@/lib/auth";
import { listDocuments, type DocumentListItem } from "@/lib/document-api";
import { callAgentTool, listAgentTools, type ToolCallData, type ToolSpecItem } from "@/lib/tool-api";

function statusBadge(status?: string): string {
  if (status === "SUCCESS" || status === "LOW") {
    return "dp-badge dp-badge-success";
  }
  if (status === "FAILED" || status === "HIGH") {
    return "dp-badge dp-badge-danger";
  }
  if (status === "MEDIUM") {
    return "dp-badge dp-badge-warning";
  }
  return "dp-badge dp-badge-info";
}

function stringifyJson(value: unknown): string {
  try {
    return JSON.stringify(value ?? {}, null, 2);
  } catch {
    return "{}";
  }
}

function buildDefaultArguments(toolName: string, documentId?: number): Record<string, unknown> {
  const normalized = toolName.toLowerCase();
  if (normalized.includes("rag")) {
    return {
      documentId: documentId || 0,
      question: "这份文档的核心技术亮点是什么？",
      topK: 5
    };
  }
  if (normalized.includes("qa")) {
    return {
      documentId: documentId || 0,
      question: "请基于文档内容回答核心观点。"
    };
  }
  if (normalized.includes("status") || normalized.includes("summary")) {
    return {
      documentId: documentId || 0
    };
  }
  return {};
}

export default function AgentToolsPage() {
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [tools, setTools] = useState<ToolSpecItem[]>([]);
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [selectedToolName, setSelectedToolName] = useState("");
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | undefined>();
  const [argumentsText, setArgumentsText] = useState("{}");
  const [loading, setLoading] = useState(true);
  const [calling, setCalling] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [result, setResult] = useState<ToolCallData | null>(null);

  const selectedTool = useMemo(
    () => tools.find((item) => item.name === selectedToolName) || null,
    [selectedToolName, tools]
  );

  const parsedDocuments = useMemo(
    () => documents.filter((item) => item.parseStatus === "SUCCESS"),
    [documents]
  );

  const loadData = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setTools([]);
      setDocuments([]);
      setLoading(false);
      return;
    }

    setHasToken(true);
    setErrorMessage("");
    setLoading(true);
    try {
      const [toolsResponse, docsResponse] = await Promise.all([
        listAgentTools(),
        listDocuments({ pageNo: 1, pageSize: 100 })
      ]);
      const nextTools = toolsResponse.data || [];
      const nextDocuments = docsResponse.data?.records || [];
      const firstDocument = nextDocuments.find((item) => item.parseStatus === "SUCCESS") || nextDocuments[0];
      const firstCallableTool = nextTools.find((item) => item.callableByToolCallApi) || nextTools[0];

      setTools(nextTools);
      setDocuments(nextDocuments);
      setSelectedDocumentId(firstDocument?.documentId);
      setSelectedToolName(firstCallableTool?.name || "");
      setArgumentsText(stringifyJson(buildDefaultArguments(firstCallableTool?.name || "", firstDocument?.documentId)));
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载工具列表失败";
      setErrorMessage(message);
      setTools([]);
      setDocuments([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function handleSelectTool(toolName: string) {
    setSelectedToolName(toolName);
    setResult(null);
    setErrorMessage("");
    setArgumentsText(stringifyJson(buildDefaultArguments(toolName, selectedDocumentId)));
  }

  function handleSelectDocument(documentIdText: string) {
    const documentId = Number(documentIdText);
    const nextDocumentId = Number.isNaN(documentId) || documentId <= 0 ? undefined : documentId;
    setSelectedDocumentId(nextDocumentId);
    setArgumentsText(stringifyJson(buildDefaultArguments(selectedToolName, nextDocumentId)));
  }

  async function handleCallTool() {
    if (!selectedToolName) {
      setErrorMessage("请选择要调用的工具");
      return;
    }

    let parsedArguments: Record<string, unknown>;
    try {
      const parsed = JSON.parse(argumentsText || "{}") as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("arguments 必须是 JSON 对象");
      }
      parsedArguments = parsed as Record<string, unknown>;
    } catch (error) {
      const message = error instanceof Error ? error.message : "JSON 参数解析失败";
      setErrorMessage(message);
      return;
    }

    setCalling(true);
    setErrorMessage("");
    setResult(null);
    try {
      const response = await callAgentTool({
        toolName: selectedToolName,
        arguments: parsedArguments
      });
      setResult(response.data);
    } catch (error) {
      const message = error instanceof Error ? error.message : "工具调用失败";
      setErrorMessage(message);
    } finally {
      setCalling(false);
    }
  }

  return (
    <main className="dp-page max-w-7xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="text-sm font-bold text-slate-400 tracking-wider uppercase mb-1">ToolCall Console</p>
          <h1 className="text-3xl font-bold text-slate-900 mb-2">Agent 工具台</h1>
          <p className="text-slate-500 max-w-3xl">
            查看服务端 ToolSpec，并通过受控 ToolCall API 调用文档状态、摘要、问答与 RAG 工具。
          </p>
        </div>
        <div className="flex gap-3">
          <button type="button" onClick={loadData} disabled={loading} className="dp-btn dp-btn-secondary">
            {loading ? "刷新中..." : "刷新"}
          </button>
          <Link href="/agent" className="dp-btn dp-btn-primary">Agent 工作流</Link>
        </div>
      </section>

      {hasToken === false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">
          当前未登录，请先前往 <Link href="/login" className="underline font-bold">登录页</Link>。
        </section>
      ) : null}

      {errorMessage && hasToken !== false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">{errorMessage}</section>
      ) : null}

      <section className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <aside className="space-y-6">
          <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900">工具列表</h2>
              <span className="text-xs text-slate-400">{tools.length} 个</span>
            </div>
            {loading ? <p className="text-sm text-slate-400">加载中...</p> : null}
            {!loading && tools.length === 0 ? <p className="text-sm text-slate-400">暂无工具定义。</p> : null}
            <ul className="space-y-2">
              {tools.map((tool) => (
                <li key={tool.name}>
                  <button
                    type="button"
                    onClick={() => handleSelectTool(tool.name)}
                    className={`w-full rounded-xl border p-3 text-left transition-all ${
                      selectedToolName === tool.name
                        ? "border-blue-200 bg-blue-50"
                        : "border-slate-100 bg-slate-50 hover:border-blue-100 hover:bg-white"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-slate-900">{tool.displayName || tool.name}</span>
                      <span className={statusBadge(tool.riskLevel)}>{tool.riskLevel || "LOW"}</span>
                    </div>
                    <p className="mt-1 line-clamp-2 text-xs text-slate-500">{tool.description || tool.name}</p>
                    <div className="mt-2 flex flex-wrap gap-2 text-[11px]">
                      <span className={tool.callableByToolCallApi ? "text-emerald-700" : "text-slate-400"}>
                        {tool.callableByToolCallApi ? "API 可调用" : "API 不可调用"}
                      </span>
                      <span className={tool.safeForLlmSelection ? "text-blue-700" : "text-slate-400"}>
                        {tool.safeForLlmSelection ? "可供选择器使用" : "不供选择器使用"}
                      </span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          </article>

          <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
            <h2 className="mb-3 text-base font-bold text-slate-900">文档参数辅助</h2>
            <select
              value={selectedDocumentId || ""}
              onChange={(event) => handleSelectDocument(event.target.value)}
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">不使用文档模板</option>
              {parsedDocuments.map((item) => (
                <option key={item.documentId} value={item.documentId}>
                  {item.fileName || `文档 #${item.documentId}`}
                </option>
              ))}
            </select>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              选择文档会用该 documentId 生成参数模板，最终调用仍以右侧 JSON 为准。
            </p>
          </article>
        </aside>

        <section className="space-y-6">
          <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
            <div className="mb-5 flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
              <div>
                <h2 className="text-xl font-bold text-slate-900">{selectedTool?.displayName || selectedToolName || "选择工具"}</h2>
                <p className="mt-1 text-sm text-slate-500">{selectedTool?.description || "请选择左侧工具后填写参数。"}</p>
              </div>
              {selectedTool ? <span className={statusBadge(selectedTool.riskLevel)}>{selectedTool.riskLevel || "LOW"}</span> : null}
            </div>

            {selectedTool ? (
              <div className="mb-5 grid gap-4 xl:grid-cols-[1fr_1fr]">
                <details className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                  <summary className="cursor-pointer text-sm font-semibold text-slate-700">参数 Schema</summary>
                  <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
                    {stringifyJson(selectedTool.parameterSchema)}
                  </pre>
                </details>
                <details className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                  <summary className="cursor-pointer text-sm font-semibold text-slate-700">结果 Schema</summary>
                  <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
                    {stringifyJson(selectedTool.resultSchema)}
                  </pre>
                </details>
              </div>
            ) : null}

            <label className="block">
              <span className="mb-2 block text-sm font-bold text-slate-700">arguments JSON</span>
              <textarea
                value={argumentsText}
                onChange={(event) => setArgumentsText(event.target.value)}
                rows={10}
                spellCheck={false}
                className="w-full rounded-xl border border-slate-200 bg-slate-950 px-4 py-3 font-mono text-sm leading-6 text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <div className="mt-4 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={handleCallTool}
                disabled={calling || !selectedToolName || selectedTool?.callableByToolCallApi === false}
                className="dp-btn dp-btn-primary px-6"
              >
                {calling ? "调用中..." : "调用工具"}
              </button>
              <button
                type="button"
                onClick={() => setArgumentsText(stringifyJson(buildDefaultArguments(selectedToolName, selectedDocumentId)))}
                className="dp-btn dp-btn-secondary px-6"
              >
                重置模板
              </button>
              <button
                type="button"
                onClick={() => {
                  setResult(null);
                  setErrorMessage("");
                }}
                className="dp-btn dp-btn-ghost px-6"
              >
                清空结果
              </button>
            </div>
          </article>

          <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-bold text-slate-900">调用结果</h2>
              {result ? <span className={statusBadge(result.status)}>{result.status}</span> : null}
            </div>
            {!result ? (
              <p className="rounded-xl border border-slate-100 bg-slate-50 p-5 text-sm text-slate-400">
                暂无结果。选择工具并提交参数后，这里会展示 ToolCallResult。
              </p>
            ) : (
              <div className="space-y-4">
                <div className="grid gap-3 md:grid-cols-3">
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                    <p className="text-xs text-slate-400">工具</p>
                    <p className="mt-1 font-semibold text-slate-800">{result.toolName}</p>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                    <p className="text-xs text-slate-400">状态</p>
                    <p className="mt-1 font-semibold text-slate-800">{result.status}</p>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                    <p className="text-xs text-slate-400">耗时</p>
                    <p className="mt-1 font-semibold text-slate-800">{result.durationMs ?? "-"} ms</p>
                  </div>
                </div>

                {result.outputSummary ? (
                  <div className="rounded-xl border border-blue-100 bg-blue-50/60 p-4">
                    <p className="mb-2 text-sm font-bold text-blue-800">输出摘要</p>
                    <p className="text-sm leading-6 text-slate-700">{result.outputSummary}</p>
                  </div>
                ) : null}

                {result.errorMessage ? (
                  <div className="rounded-xl border border-red-100 bg-red-50 p-4">
                    <p className="mb-1 text-sm font-bold text-red-700">{result.errorType || "调用失败"}</p>
                    <p className="text-sm text-red-600">{result.errorMessage}</p>
                  </div>
                ) : null}

                <div className="grid gap-4 xl:grid-cols-[1fr_1fr]">
                  <details className="rounded-xl border border-slate-100 bg-slate-50 p-4" open>
                    <summary className="cursor-pointer text-sm font-semibold text-slate-700">result</summary>
                    <pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
                      {stringifyJson(result.result)}
                    </pre>
                  </details>
                  <details className="rounded-xl border border-slate-100 bg-slate-50 p-4">
                    <summary className="cursor-pointer text-sm font-semibold text-slate-700">citations / retrievalHits</summary>
                    <pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
                      {stringifyJson({
                        citations: result.citations || [],
                        retrievalHits: result.retrievalHits || []
                      })}
                    </pre>
                  </details>
                </div>
              </div>
            )}
          </article>
        </section>
      </section>
    </main>
  );
}
