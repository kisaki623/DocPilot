"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "@/lib/auth";
import { listDocuments, type DocumentListData, type DocumentListItem } from "@/lib/document-api";

const DEFAULT_PAGE_SIZE = 10;
const TERMINAL_STATUS = new Set(["SUCCESS", "FAILED"]);

type StatusFilter = "ALL" | "SUCCESS" | "FAILED" | "PENDING";

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

export default function DocumentsPage() {
  const [records, setRecords] = useState<DocumentListItem[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");

  const totalPages = useMemo(() => {
    if (total <= 0) {
      return 1;
    }
    return Math.max(1, Math.ceil(total / pageSize));
  }, [pageSize, total]);

  const fetchDocuments = useCallback(
    async (targetPageNo: number, silent?: boolean) => {
      if (!silent) {
        setLoading(true);
      }

      const token = getToken();
      if (!token) {
        setHasToken(false);
        setErrorMessage("未检测到登录状态，请先登录");
        setRecords([]);
        setTotal(0);
        setPageNo(targetPageNo);
        setLoading(false);
        return;
      }

      setHasToken(true);
      setErrorMessage("");

      try {
        const response = await listDocuments({ pageNo: targetPageNo, pageSize });
        const data: DocumentListData | null = response.data;

        setRecords(data?.records || []);
        setTotal(data?.total || 0);
        setPageNo(data?.pageNo || targetPageNo);
      } catch (error) {
        const message = error instanceof Error ? error.message : "加载文档列表失败";
        setErrorMessage(message);
        setRecords([]);
        setTotal(0);
        setPageNo(targetPageNo);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [pageSize]
  );

  useEffect(() => {
    fetchDocuments(1);
  }, [fetchDocuments]);

  useEffect(() => {
    if (loading || refreshing || hasToken === false || records.length === 0) {
      return;
    }
    const hasRunningTask = records.some((item) => !TERMINAL_STATUS.has(item.parseStatus || ""));
    if (!hasRunningTask) {
      return;
    }
    const timer = window.setInterval(() => {
      fetchDocuments(pageNo, true);
    }, 3000);
    return () => {
      window.clearInterval(timer);
    };
  }, [fetchDocuments, hasToken, loading, pageNo, records, refreshing]);

  const filteredRecords = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();
    return records.filter((item) => {
      if (statusFilter !== "ALL" && (item.parseStatus || "") !== statusFilter) {
        return false;
      }
      if (!keyword) {
        return true;
      }
      const text = [item.fileName, item.summary, item.parseStatusLabel, item.parseStatus]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return text.includes(keyword);
    });
  }, [records, searchKeyword, statusFilter]);

  const stats = useMemo(() => {
    const totalInPage = records.length;
    const successCount = records.filter((item) => item.parseStatus === "SUCCESS").length;
    const failedCount = records.filter((item) => item.parseStatus === "FAILED").length;
    const runningCount = records.filter((item) => !TERMINAL_STATUS.has(item.parseStatus || "")).length;
    return { totalInPage, successCount, failedCount, runningCount };
  }, [records]);

  async function handleRefresh() {
    setRefreshing(true);
    await fetchDocuments(pageNo, true);
  }

  async function handlePrevPage() {
    if (pageNo <= 1 || loading || refreshing) {
      return;
    }
    await fetchDocuments(pageNo - 1);
  }

  async function handleNextPage() {
    if (pageNo >= totalPages || loading || refreshing) {
      return;
    }
    await fetchDocuments(pageNo + 1);
  }

  return (
    <main className="dp-page max-w-6xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 mb-2">文档库</h1>
          <p className="text-slate-500 max-w-2xl">
            在这里管理您上传的所有文档。点击文档可查看详情并与 AI 对话。
          </p>
        </div>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={loading || refreshing}
            className="dp-btn dp-btn-secondary"
          >
            {refreshing ? "刷新中..." : "刷新列表"}
          </button>
          <Link href="/upload" className="dp-btn dp-btn-primary">
            上传文档
          </Link>
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

      <section className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6 mb-8">
        <div className="grid gap-6 lg:grid-cols-[1fr_auto] items-end">
          <label className="block w-full" htmlFor="documents-search-input">
            <span className="mb-2 block text-sm font-medium text-slate-700">搜索文档</span>
            <input
              id="documents-search-input"
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              className="w-full px-4 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              placeholder="搜索文件名或摘要..."
            />
          </label>

          <div>
            <span className="mb-2 block text-sm font-medium text-slate-700">状态</span>
            <div className="flex gap-2 bg-slate-100 p-1 rounded-lg">
              {(["ALL", "SUCCESS", "PENDING", "FAILED"] as const).map((value) => {
                const labels: Record<string, string> = {
                  ALL: "全部",
                  SUCCESS: "成功",
                  PENDING: "处理中",
                  FAILED: "失败"
                };
                return (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setStatusFilter(value)}
                    className={`px-4 py-1.5 rounded-md text-sm font-medium transition-all ${
                      statusFilter === value
                        ? "bg-white text-slate-900 shadow-sm"
                        : "text-slate-500 hover:text-slate-700 hover:bg-slate-200"
                    }`}
                  >
                    {labels[value] || value}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      <section className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-slate-900">文档列表</h2>
          <p className="text-sm text-slate-500">
            共 {total} 份文档（第 {pageNo}/{totalPages} 页）
          </p>
        </div>

        {loading ? (
          <div className="text-center py-12">
            <p className="text-slate-500">正在加载文档列表...</p>
          </div>
        ) : null}

        {!loading && records.length === 0 ? (
          <div className="text-center py-16 bg-slate-50 rounded-xl border border-slate-100">
            <p className="text-slate-500 mb-4">知识库还是空的</p>
            <Link href="/upload" className="dp-btn dp-btn-primary px-6">
              上传第一份文档
            </Link>
          </div>
        ) : null}

        {!loading && records.length > 0 && filteredRecords.length === 0 ? (
          <div className="text-center py-12 bg-slate-50 rounded-xl border border-slate-100">
            <p className="text-slate-500">未找到符合条件的文档，请尝试更改搜索词或状态筛选。</p>
          </div>
        ) : null}

        {!loading && filteredRecords.length > 0 ? (
          <ul className="space-y-4">
            {filteredRecords.map((item) => (
              <li key={item.documentId} className="group bg-white border border-slate-100 rounded-xl p-5 hover:border-blue-200 hover:shadow-md transition-all">
                <div className="flex justify-between items-start gap-4 mb-3">
                  <div className="flex-1 min-w-0">
                    <Link href={`/documents/${item.documentId}`} className="text-lg font-bold text-slate-900 group-hover:text-blue-700 transition-colors truncate block">
                      {item.fileName || `未命名文档 #${item.documentId}`}
                    </Link>
                    <p className="text-sm text-slate-500 mt-1 line-clamp-2">
                      {item.summary || "正在生成摘要中..."}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="px-2.5 py-1 bg-slate-100 text-slate-600 rounded text-xs font-medium">
                      {item.fileType?.toUpperCase() || "未知格式"}
                    </span>
                    <span className={resolveStatusBadge(item.parseStatus)}>
                      {item.parseStatusLabel || item.parseStatus || "处理中"}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between mt-4 pt-4 border-t border-slate-50">
                  <span className="text-xs text-slate-400">
                    上传时间：{formatDateTime(item.createTime)}
                  </span>
                  <Link href={`/documents/${item.documentId}`} className="text-sm font-medium text-blue-600 hover:text-blue-700 flex items-center gap-1">
                    阅读文档 <span aria-hidden="true">&rarr;</span>
                  </Link>
                </div>
              </li>
            ))}
          </ul>
        ) : null}

        {(!loading && totalPages > 1) ? (
          <div className="mt-8 pt-6 border-t border-slate-100 flex items-center justify-between">
            <button
              type="button"
              onClick={handlePrevPage}
              disabled={pageNo <= 1}
              className="dp-btn dp-btn-secondary px-6 disabled:opacity-50"
            >
              上一页
            </button>
            <span className="text-sm text-slate-500 font-medium">
              {pageNo} / {totalPages}
            </span>
            <button
              type="button"
              onClick={handleNextPage}
              disabled={pageNo >= totalPages}
              className="dp-btn dp-btn-secondary px-6 disabled:opacity-50"
            >
              下一页
            </button>
          </div>
        ) : null}
      </section>
    </main>
  );
}
