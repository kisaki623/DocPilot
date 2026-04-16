"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { clearToken, getToken } from "@/lib/auth";
import { listDocuments, type DocumentListItem } from "@/lib/document-api";

const TERMINAL_STATUS = new Set(["SUCCESS", "FAILED"]);

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

function parseStatusBadge(status: string): string {
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

export default function DashboardPage() {
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [records, setRecords] = useState<DocumentListItem[]>([]);

  const fetchOverview = useCallback(async (silent?: boolean) => {
    if (!silent) {
      setLoading(true);
    }

    const token = getToken();
    if (!token) {
      setHasToken(false);
      setRecords([]);
      setErrorMessage("未检测到登录状态，请先登录。");
      setLoading(false);
      setRefreshing(false);
      return;
    }

    setHasToken(true);
    setErrorMessage("");

    try {
      const response = await listDocuments({ pageNo: 1, pageSize: 20 });
      setRecords(response.data?.records || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载控制台概览失败";
      setErrorMessage(message);
      setRecords([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchOverview();
  }, [fetchOverview]);

  useEffect(() => {
    if (!records.some((item) => !TERMINAL_STATUS.has(item.parseStatus || ""))) {
      return;
    }
    const timer = window.setInterval(() => {
      fetchOverview(true);
    }, 5000);
    return () => {
      window.clearInterval(timer);
    };
  }, [fetchOverview, records]);

  const stats = useMemo(() => {
    const total = records.length;
    const success = records.filter((item) => item.parseStatus === "SUCCESS").length;
    const failed = records.filter((item) => item.parseStatus === "FAILED").length;
    const running = records.filter((item) => !TERMINAL_STATUS.has(item.parseStatus || "")).length;
    return { total, success, failed, running };
  }, [records]);

  const recentRecords = useMemo(() => records.slice(0, 6), [records]);

  return (
    <main className="dp-page max-w-6xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8">
        <div className="flex justify-between items-start">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 mb-2">工作台</h1>
            <p className="text-slate-500">
              欢迎回来。您可以在这里管理已上传的文档，并快速进行阅读和智能问答。
            </p>
          </div>
          <div className="flex gap-3">
            <Link href="/upload" className="dp-btn dp-btn-primary px-6">
              上传新文档
            </Link>
            <button
              type="button"
              onClick={() => {
                clearToken();
                setHasToken(false);
                setRecords([]);
                setErrorMessage("已退出登录。");
              }}
              className="dp-btn dp-btn-secondary"
            >
              退出登录
            </button>
          </div>
        </div>
      </section>

      {hasToken === false ? (
        <section className="bg-slate-50 text-slate-600 p-4 rounded-xl text-center mb-8">
          当前未登录，请先前往 <Link href="/login" className="text-blue-600 hover:underline">登录页</Link>。
        </section>
      ) : null}

      {errorMessage && hasToken !== false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">{errorMessage}</section>
      ) : null}

      <section className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <article className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <p className="text-sm font-medium text-slate-500 mb-1">近期文档</p>
          <p className="text-3xl font-bold text-slate-900">{loading ? "--" : stats.total}</p>
        </article>
        <article className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <p className="text-sm font-medium text-slate-500 mb-1">正在处理</p>
          <p className="text-3xl font-bold text-blue-600">{loading ? "--" : stats.running}</p>
        </article>
        <article className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <p className="text-sm font-medium text-slate-500 mb-1">解析成功</p>
          <p className="text-3xl font-bold text-emerald-600">{loading ? "--" : stats.success}</p>
        </article>
        <article className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <p className="text-sm font-medium text-slate-500 mb-1">解析失败</p>
          <p className="text-3xl font-bold text-red-600">{loading ? "--" : stats.failed}</p>
        </article>
      </section>

      <section className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-slate-900">最近文档</h2>
            <div className="flex gap-2">
              <Link href="/documents" className="text-sm text-blue-600 hover:underline mr-4">
                查看全部
              </Link>
              <button
                type="button"
                onClick={() => {
                  setRefreshing(true);
                  fetchOverview(true);
                }}
                disabled={loading || refreshing}
                className="text-sm text-slate-500 hover:text-slate-800"
              >
                {refreshing ? "刷新中..." : "刷新"}
              </button>
            </div>
          </div>

          {loading ? <p className="text-sm text-slate-500 text-center py-8">正在加载列表...</p> : null}

          {!loading && recentRecords.length === 0 ? (
            <div className="text-center py-12 bg-slate-50 rounded-xl">
              <p className="text-slate-500 mb-4">还没有上传任何文档</p>
              <Link href="/upload" className="dp-btn dp-btn-primary">去上传第一份文档</Link>
            </div>
          ) : null}

          {!loading && recentRecords.length > 0 ? (
            <ul className="space-y-4">
              {recentRecords.map((item) => (
                <li key={item.documentId} className="group flex flex-col p-4 rounded-xl border border-slate-100 hover:border-blue-100 hover:shadow-sm transition-all bg-slate-50 hover:bg-white">
                  <div className="flex items-start justify-between gap-4 mb-2">
                    <Link href={`/documents/${item.documentId}`} className="text-base font-semibold text-slate-900 group-hover:text-blue-700 transition-colors line-clamp-1 flex-1">
                      {item.fileName || `文档 #${item.documentId}`}
                    </Link>
                    <span className={parseStatusBadge(item.parseStatus || "")}>{item.parseStatusLabel || item.parseStatus}</span>
                  </div>
                  <p className="text-sm text-slate-500 line-clamp-2 mb-3 flex-1">{item.summary || "暂无摘要"}</p>
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>{formatDateTime(item.createTime)}</span>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </article>

        <article className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6 self-start">
          <h2 className="text-xl font-bold text-slate-900 mb-4">快速入门</h2>
          <ol className="space-y-4 relative before:absolute before:inset-y-0 before:left-[11px] before:w-px before:bg-slate-200 ml-1">
            <li className="relative pl-8">
              <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">1</div>
              <p className="font-semibold text-slate-800 text-sm">上传文档</p>
              <p className="text-xs text-slate-500 mt-1">支持 TXT, PDF, Markdown 格式，系统会自动进行解析与特征提取。</p>
            </li>
            <li className="relative pl-8">
              <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">2</div>
              <p className="font-semibold text-slate-800 text-sm">等待解析完成</p>
              <p className="text-xs text-slate-500 mt-1">控制台会实时显示文档的解析进度，成功后即可使用智能问答。</p>
            </li>
            <li className="relative pl-8">
              <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">3</div>
              <p className="font-semibold text-slate-800 text-sm">智能问答</p>
              <p className="text-xs text-slate-500 mt-1">进入文档详情页，向 AI 助手提问，获取带有精准原文引用的回答。</p>
            </li>
          </ol>
        </article>
      </section>
    </main>
  );
}
