"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { clearToken, getToken } from "@/lib/auth";
import { listDocuments, type DocumentListItem } from "@/lib/document-api";

const TERMINAL_STATUS = new Set(["SUCCESS", "FAILED"]);

const showcaseChecks = [
  "上传后自动进入文档登记、解析任务与状态追踪",
  "文档问答支持引用来源与流式回答",
  "知识库可呈现跨文档检索与命中文档分布",
  "会话可结合摘要、长期记忆与上下文溯源",
  "Agent 工具链保留工具选择与执行记录",
];

const commandSteps = [
  "上传解析",
  "文档问答",
  "知识库",
  "会话记忆",
  "工具链",
];

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
      const message =
        error instanceof Error ? error.message : "加载空间概览失败";
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
    const success = records.filter(
      (item) => item.parseStatus === "SUCCESS",
    ).length;
    const failed = records.filter(
      (item) => item.parseStatus === "FAILED",
    ).length;
    const running = records.filter(
      (item) => !TERMINAL_STATUS.has(item.parseStatus || ""),
    ).length;
    return { total, success, failed, running };
  }, [records]);

  const recentRecords = useMemo(() => records.slice(0, 6), [records]);

  return (
    <main className="dp-page max-w-6xl mx-auto py-8 px-4">
      <section className="dp-hero dp-hero-product">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="dp-eyebrow">Workspace Overview</p>
            <h1 className="mt-2 text-3xl font-bold text-slate-950">
              DocPilot 文档工作空间
            </h1>
            <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
              从这里查看文档处理状态，并进入文档问答、知识库检索、会话记忆和
              Agent 工具链等核心流程。
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              {commandSteps.map((item, index) => (
                <span key={item} className="dp-badge dp-badge-info">
                  {index + 1}. {item}
                </span>
              ))}
            </div>
          </div>
          <div className="flex flex-wrap justify-end gap-3">
            <Link href="/upload" className="dp-btn dp-btn-primary px-6">
              上传新文档
            </Link>
            <Link
              href="/knowledge-bases"
              className="dp-btn dp-btn-secondary px-6"
            >
              知识库
            </Link>
            <Link
              href="/conversations"
              className="dp-btn dp-btn-secondary px-6"
            >
              会话记忆
            </Link>
            <Link href="/agent" className="dp-btn dp-btn-ghost px-6">
              Agent 工具链
            </Link>
            <Link href="/agent/tools" className="dp-btn dp-btn-ghost px-6">
              工具箱
            </Link>
            {hasToken ? (
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
            ) : null}
          </div>
        </div>
      </section>

      {hasToken === false ? (
        <section className="bg-slate-50 text-slate-600 p-4 rounded-xl text-center mb-8">
          当前未登录，请先前往{" "}
          <Link href="/login" className="text-blue-600 hover:underline">
            登录页
          </Link>
          。
        </section>
      ) : null}

      {errorMessage && hasToken !== false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8">
          {errorMessage}
        </section>
      ) : null}

      <section className="dp-kpi-grid">
        <article className="dp-kpi-card">
          <p className="dp-kpi-label">近期文档</p>
          <p className="dp-kpi-value text-slate-900">
            {loading ? "--" : stats.total}
          </p>
        </article>
        <article className="dp-kpi-card">
          <p className="dp-kpi-label">正在处理</p>
          <p className="dp-kpi-value text-blue-600">
            {loading ? "--" : stats.running}
          </p>
        </article>
        <article className="dp-kpi-card">
          <p className="dp-kpi-label">解析成功</p>
          <p className="dp-kpi-value text-emerald-600">
            {loading ? "--" : stats.success}
          </p>
        </article>
        <article className="dp-kpi-card">
          <p className="dp-kpi-label">解析失败</p>
          <p className="dp-kpi-value text-red-600">
            {loading ? "--" : stats.failed}
          </p>
        </article>
      </section>

      <section className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <article className="dp-card">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-bold text-slate-900">最近文档</h2>
            <div className="flex gap-2">
              <Link
                href="/documents"
                className="text-sm text-blue-600 hover:underline mr-4"
              >
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

          {loading ? (
            <p className="text-sm text-slate-500 text-center py-8">
              正在加载列表...
            </p>
          ) : null}

          {!loading && recentRecords.length === 0 ? (
            <div className="text-center py-12 bg-slate-50 rounded-xl">
              <p className="text-slate-500 mb-4">还没有上传任何文档</p>
              <Link href="/upload" className="dp-btn dp-btn-primary">
                去上传第一份文档
              </Link>
            </div>
          ) : null}

          {!loading && recentRecords.length > 0 ? (
            <ul className="space-y-4">
              {recentRecords.map((item) => (
                <li
                  key={item.documentId}
                  className="group flex flex-col p-4 rounded-xl border border-slate-100 hover:border-blue-100 hover:shadow-sm transition-all bg-slate-50 hover:bg-white"
                >
                  <div className="flex items-start justify-between gap-4 mb-2">
                    <Link
                      href={`/documents/${item.documentId}`}
                      className="text-base font-semibold text-slate-900 group-hover:text-blue-700 transition-colors line-clamp-1 flex-1"
                    >
                      {item.fileName || `文档 #${item.documentId}`}
                    </Link>
                    <span className={parseStatusBadge(item.parseStatus || "")}>
                      {item.parseStatusLabel || item.parseStatus}
                    </span>
                  </div>
                  <p className="text-sm text-slate-500 line-clamp-2 mb-3 flex-1">
                    {item.summary || "暂无摘要"}
                  </p>
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>{formatDateTime(item.createTime)}</span>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </article>

        <div className="grid content-start gap-6">
          <article className="dp-card">
            <p className="dp-eyebrow">Runbook</p>
            <h2 className="mt-2 text-xl font-bold text-slate-900 mb-4">
              推荐工作流
            </h2>
            <ol className="space-y-4 relative before:absolute before:inset-y-0 before:left-[11px] before:w-px before:bg-slate-200 ml-1">
              <li className="relative pl-8">
                <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                  1
                </div>
                <p className="font-semibold text-slate-800 text-sm">上传文档</p>
                <p className="text-xs text-slate-500 mt-1">
                  上传后自动创建文档与解析任务，观察异步解析状态。
                </p>
              </li>
              <li className="relative pl-8">
                <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                  2
                </div>
                <p className="font-semibold text-slate-800 text-sm">
                  文档问答
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  进入详情页提问，查看引用来源与流式回答。
                </p>
              </li>
              <li className="relative pl-8">
                <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                  3
                </div>
                <p className="font-semibold text-slate-800 text-sm">
                  知识库检索
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  加入多份已解析文档，进行跨文档检索与资料集问答。
                </p>
              </li>
              <li className="relative pl-8">
                <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                  4
                </div>
                <p className="font-semibold text-slate-800 text-sm">
                  会话记忆
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  绑定知识库后继续提问，查看摘要、长期记忆与上下文溯源。
                </p>
              </li>
              <li className="relative pl-8">
                <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-bold">
                  5
                </div>
                <p className="font-semibold text-slate-800 text-sm">
                  Agent 工具链
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  在 Agent 页面或工具箱查看工具选择、执行记录与最终回答。
                </p>
              </li>
            </ol>
          </article>

          <article className="dp-card dp-dark-card">
            <h2 className="text-xl font-bold text-white mb-4">
              核心流程状态
            </h2>
            <ul className="grid gap-2">
              {showcaseChecks.map((item) => (
                <li
                  key={item}
                  className="rounded-lg border border-blue-400/25 bg-white/10 px-3 py-2 text-xs leading-5 text-slate-100"
                >
                  {item}
                </li>
              ))}
            </ul>
            <Link
              href="/conversations"
              className="dp-btn dp-btn-primary mt-4 w-full"
            >
              打开会话记忆
            </Link>
          </article>
        </div>
      </section>
    </main>
  );
}
