"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import {
  bindConversationKnowledgeBase,
  createConversation,
  deleteConversationSummary,
  getConversation,
  getConversationMessageTrace,
  getConversationSummary,
  listConversationMessages,
  listConversations,
  refreshConversationSummary,
  sendConversationMessage,
  unbindConversationKnowledgeBase,
  type ContextTraceData,
  type ConversationContextMode,
  type ConversationItem,
  type ConversationMessageItem,
  type ConversationSummaryData,
} from "@/lib/conversation-api";
import {
  listKnowledgeBases,
  type KnowledgeBaseItem,
} from "@/lib/knowledge-base-api";
import {
  acceptMemorySuggestion,
  createUserMemory,
  deleteUserMemory,
  extractMemorySuggestions,
  ignoreMemorySuggestion,
  listMemorySuggestions,
  listUserMemories,
  type UserMemoryItem,
  type UserMemoryType,
} from "@/lib/memory-api";

const DEFAULT_PROMPT =
  "请结合当前会话和已绑定资料，概括 DocPilot 的核心能力，并说明回答参考了哪些来源。";
const SUGGESTED_PROMPTS = [
  "根据知识库总结这些资料的核心主题。",
  "这次回答参考了哪些会话记忆和资料来源？",
  "请从产品使用视角评价 DocPilot 的文档问答体验。",
];
const PENDING_STEPS = [
  "正在检索相关资料",
  "正在整理会话记忆与摘要",
  "正在准备回答上下文",
  "正在生成最终回答",
];
const MEMORY_TYPES: Array<{ value: UserMemoryType; label: string }> = [
  { value: "PREFERENCE", label: "偏好" },
  { value: "TASK_GOAL", label: "目标" },
  { value: "PROJECT_STATE", label: "项目状态" },
  { value: "TECH_CONTEXT", label: "技术上下文" },
  { value: "ANSWER_STYLE", label: "回答风格" },
  { value: "CUSTOM", label: "自定义" },
];

const CONTEXT_FLOW = [
  { label: "对话", detail: "最近轮次" },
  { label: "摘要", detail: "压缩历史" },
  { label: "记忆", detail: "长期偏好" },
  { label: "知识库", detail: "引用来源" },
  { label: "溯源", detail: "运行快照" },
];

function formatDateTime(input?: string | null): string {
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
  if (status === "ACTIVE" || status === "SUCCESS") {
    return "dp-badge dp-badge-success";
  }
  if (status === "DELETED" || status === "IGNORED" || status === "FAILED") {
    return "dp-badge dp-badge-danger";
  }
  if (status === "SUGGESTED" || status === "PENDING") {
    return "dp-badge dp-badge-warning";
  }
  return "dp-badge dp-badge-info";
}

function modeLabel(mode?: string): string {
  if (mode === "AGENT_MEMORY") {
    return "会话记忆";
  }
  if (mode === "RECENT_TURNS") {
    return "最近轮次";
  }
  return mode || "-";
}

function memoryTypeLabel(type?: string): string {
  return (
    MEMORY_TYPES.find((item) => item.value === type)?.label || type || "自定义"
  );
}

function formatBoolean(value?: boolean): string {
  return value ? "是" : "否";
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

function toPositiveNumber(value: string): number | undefined {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : undefined;
}

export default function ConversationsPage() {
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [statusMessage, setStatusMessage] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [inspectorOpen, setInspectorOpen] = useState(false);
  const [inspectorTab, setInspectorTab] = useState<
    "trace" | "memory" | "summary"
  >("trace");
  const [pendingStepIndex, setPendingStepIndex] = useState(0);

  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<
    number | null
  >(null);
  const [messages, setMessages] = useState<ConversationMessageItem[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [messageInput, setMessageInput] = useState(DEFAULT_PROMPT);

  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState("");
  const [binding, setBinding] = useState(false);

  const [summary, setSummary] = useState<ConversationSummaryData | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [trace, setTrace] = useState<ContextTraceData | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);
  const [selectedTraceMessageId, setSelectedTraceMessageId] = useState<
    number | null
  >(null);

  const [memories, setMemories] = useState<UserMemoryItem[]>([]);
  const [suggestions, setSuggestions] = useState<UserMemoryItem[]>([]);
  const [memoryLoading, setMemoryLoading] = useState(false);
  const [newMemoryType, setNewMemoryType] = useState<UserMemoryType>("CUSTOM");
  const [newMemoryContent, setNewMemoryContent] = useState("");
  const [newMemoryPriority, setNewMemoryPriority] = useState("50");

  const [newTitle, setNewTitle] = useState("会话记忆");
  const [newContextMode, setNewContextMode] =
    useState<ConversationContextMode>("AGENT_MEMORY");
  const [newBoundKnowledgeBaseId, setNewBoundKnowledgeBaseId] = useState("");
  const [creating, setCreating] = useState(false);

  const selectedConversation = useMemo(
    () =>
      conversations.find(
        (item) => item.conversationId === selectedConversationId,
      ) || null,
    [conversations, selectedConversationId],
  );

  const boundKnowledgeBaseLabel = useMemo(() => {
    if (!selectedConversation?.boundKnowledgeBaseId) {
      return "未绑定";
    }
    const knowledgeBase = knowledgeBases.find(
      (item) => item.id === selectedConversation.boundKnowledgeBaseId,
    );
    return `#${selectedConversation.boundKnowledgeBaseId} ${knowledgeBase?.name || "知识库"}`;
  }, [knowledgeBases, selectedConversation]);

  const traceSources = useMemo(() => {
    if (!trace) {
      return [];
    }
    return [
      trace.summaryUsed ? "Summary" : null,
      trace.memoryCount > 0 ? "会话记忆" : null,
      trace.evidenceCount > 0 ? "知识库来源" : null,
      trace.recentMessageCount > 0 ? "最近对话" : null,
    ].filter(Boolean) as string[];
  }, [trace]);

  const loadMemoryState = useCallback(async () => {
    setMemoryLoading(true);
    try {
      const [memoryResponse, suggestionResponse] = await Promise.all([
        listUserMemories({ limit: 30 }),
        listMemorySuggestions({ limit: 30 }),
      ]);
      setMemories(memoryResponse.data || []);
      setSuggestions(suggestionResponse.data || []);
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }, []);

  const loadWorkspace = useCallback(async () => {
    const token = getToken();
    if (!token) {
      setHasToken(false);
      setConversations([]);
      setKnowledgeBases([]);
      setMessages([]);
      setSummary(null);
      setTrace(null);
      setLoading(false);
      return;
    }

    setHasToken(true);
    setLoading(true);
    setErrorMessage("");
    try {
      const [
        conversationResponse,
        knowledgeBaseResponse,
        memoryResponse,
        suggestionResponse,
      ] = await Promise.all([
        listConversations(50),
        listKnowledgeBases(),
        listUserMemories({ limit: 30 }),
        listMemorySuggestions({ limit: 30 }),
      ]);
      const nextConversations = conversationResponse.data || [];
      setConversations(nextConversations);
      setKnowledgeBases(knowledgeBaseResponse.data || []);
      setMemories(memoryResponse.data || []);
      setSuggestions(suggestionResponse.data || []);
      setSelectedConversationId((current) => {
        if (
          current &&
          nextConversations.some((item) => item.conversationId === current)
        ) {
          return current;
        }
        return nextConversations[0]?.conversationId || null;
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "加载会话空间失败";
      setErrorMessage(message);
      setConversations([]);
      setKnowledgeBases([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadConversationState = useCallback(async (conversationId: number) => {
    setMessagesLoading(true);
    setErrorMessage("");
    setTrace(null);
    setSelectedTraceMessageId(null);
    try {
      const [detailResponse, messagesResponse, summaryResponse] =
        await Promise.all([
          getConversation(conversationId),
          listConversationMessages(conversationId, 80),
          getConversationSummary(conversationId),
        ]);
      const detail = detailResponse.data;
      if (detail) {
        setConversations((current) => {
          const exists = current.some(
            (item) => item.conversationId === detail.conversationId,
          );
          return exists
            ? current.map((item) =>
                item.conversationId === detail.conversationId ? detail : item,
              )
            : [detail, ...current];
        });
        setSelectedKnowledgeBaseId(
          detail.boundKnowledgeBaseId
            ? String(detail.boundKnowledgeBaseId)
            : "",
        );
      }
      setMessages(messagesResponse.data || []);
      setSummary(summaryResponse.data || null);
      const latestTrace = [...(messagesResponse.data || [])]
        .reverse()
        .find((item) => item.contextTrace);
      if (latestTrace?.contextTrace) {
        setTrace(latestTrace.contextTrace);
        setSelectedTraceMessageId(latestTrace.messageId);
      }
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "加载会话详情失败";
      setErrorMessage(message);
      setMessages([]);
      setSummary(null);
    } finally {
      setMessagesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadWorkspace();
  }, [loadWorkspace]);

  useEffect(() => {
    if (selectedConversationId) {
      void loadConversationState(selectedConversationId);
    } else {
      setMessages([]);
      setSummary(null);
      setTrace(null);
      setSelectedKnowledgeBaseId("");
    }
  }, [loadConversationState, selectedConversationId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ block: "end" });
  }, [messages, messagesLoading, sending, pendingStepIndex]);

  useEffect(() => {
    if (!sending) {
      setPendingStepIndex(0);
      return;
    }
    const timer = window.setInterval(() => {
      setPendingStepIndex((current) => (current + 1) % PENDING_STEPS.length);
    }, 1300);
    return () => window.clearInterval(timer);
  }, [sending]);

  async function handleCreateConversation() {
    const title = newTitle.trim();
    if (!title) {
      setErrorMessage("请输入会话标题");
      return;
    }

    setCreating(true);
    setErrorMessage("");
    setStatusMessage("");
    try {
      const boundKnowledgeBaseId = toPositiveNumber(newBoundKnowledgeBaseId);
      const response = await createConversation({
        title,
        contextMode: newContextMode,
        boundKnowledgeBaseId,
      });
      if (response.data) {
        setConversations((current) => [
          response.data as ConversationItem,
          ...current,
        ]);
        setSelectedConversationId(response.data.conversationId);
        setSelectedKnowledgeBaseId(
          response.data.boundKnowledgeBaseId
            ? String(response.data.boundKnowledgeBaseId)
            : "",
        );
        setStatusMessage("会话已创建。");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "创建会话失败";
      setErrorMessage(message);
    } finally {
      setCreating(false);
    }
  }

  async function handleSendMessage() {
    if (!selectedConversationId) {
      setErrorMessage("请先选择或创建会话");
      return;
    }
    const content = messageInput.trim();
    if (!content) {
      setErrorMessage("请输入消息内容");
      return;
    }

    const optimisticMessage: ConversationMessageItem = {
      messageId: -Date.now(),
      conversationId: selectedConversationId,
      role: "USER",
      content,
      createdAt: new Date().toISOString(),
    };

    setSending(true);
    setErrorMessage("");
    setStatusMessage("");
    setMessages((current) => [...current, optimisticMessage]);
    try {
      const response = await sendConversationMessage(
        selectedConversationId,
        content,
      );
      const assistantMessage = response.data;
      if (assistantMessage) {
        setMessageInput("");
        await loadConversationState(selectedConversationId);
        setTrace(assistantMessage.contextTrace || null);
        setSelectedTraceMessageId(
          assistantMessage.contextTrace ? assistantMessage.messageId : null,
        );
      } else {
        await loadConversationState(selectedConversationId);
      }
      void loadWorkspace();
    } catch (error) {
      const message = error instanceof Error ? error.message : "发送消息失败";
      setMessages((current) =>
        current.filter(
          (item) => item.messageId !== optimisticMessage.messageId,
        ),
      );
      setErrorMessage(message);
    } finally {
      setSending(false);
    }
  }

  async function handleBindKnowledgeBase() {
    if (!selectedConversationId) {
      return;
    }
    const knowledgeBaseId = toPositiveNumber(selectedKnowledgeBaseId);
    if (!knowledgeBaseId) {
      setErrorMessage("请选择要绑定的知识库");
      return;
    }
    setBinding(true);
    setErrorMessage("");
    setStatusMessage("");
    try {
      const response = await bindConversationKnowledgeBase(
        selectedConversationId,
        knowledgeBaseId,
      );
      if (response.data) {
        setConversations((current) =>
          current.map((item) =>
            item.conversationId === response.data?.conversationId
              ? response.data
              : item,
          ),
        );
        setStatusMessage("知识库已绑定到当前会话。");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "绑定知识库失败";
      setErrorMessage(message);
    } finally {
      setBinding(false);
    }
  }

  async function handleUnbindKnowledgeBase() {
    if (!selectedConversationId) {
      return;
    }
    setBinding(true);
    setErrorMessage("");
    setStatusMessage("");
    try {
      const response = await unbindConversationKnowledgeBase(
        selectedConversationId,
      );
      if (response.data) {
        setConversations((current) =>
          current.map((item) =>
            item.conversationId === response.data?.conversationId
              ? response.data
              : item,
          ),
        );
        setSelectedKnowledgeBaseId("");
        setStatusMessage("当前会话已解绑知识库。");
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "解绑知识库失败";
      setErrorMessage(message);
    } finally {
      setBinding(false);
    }
  }

  async function handleRefreshSummary() {
    if (!selectedConversationId) {
      return;
    }
    setSummaryLoading(true);
    setErrorMessage("");
    try {
      const response = await refreshConversationSummary(selectedConversationId);
      setSummary(response.data || null);
      setStatusMessage("摘要已刷新。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "刷新摘要失败";
      setErrorMessage(message);
    } finally {
      setSummaryLoading(false);
    }
  }

  async function handleDeleteSummary() {
    if (!selectedConversationId) {
      return;
    }
    setSummaryLoading(true);
    setErrorMessage("");
    try {
      const response = await deleteConversationSummary(selectedConversationId);
      setSummary(response.data || null);
      setStatusMessage("摘要已删除。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "删除摘要失败";
      setErrorMessage(message);
    } finally {
      setSummaryLoading(false);
    }
  }

  async function handleLoadTrace(messageId: number) {
    if (!selectedConversationId) {
      return;
    }
    setTraceLoading(true);
    setErrorMessage("");
    try {
      const response = await getConversationMessageTrace(
        selectedConversationId,
        messageId,
      );
      setTrace(response.data || null);
      setSelectedTraceMessageId(messageId);
      setInspectorTab("trace");
      setInspectorOpen(true);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "加载上下文溯源失败";
      setErrorMessage(message);
    } finally {
      setTraceLoading(false);
    }
  }

  async function handleCreateMemory() {
    const content = newMemoryContent.trim();
    if (!content) {
      setErrorMessage("请输入记忆内容");
      return;
    }
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await createUserMemory({
        memoryType: newMemoryType,
        content,
        priority: Number(newMemoryPriority) || 50,
        sourceConversationId: selectedConversationId || undefined,
      });
      setNewMemoryContent("");
      await loadMemoryState();
      setStatusMessage("长期记忆已创建。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "创建记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  async function handleExtractSuggestions() {
    if (!selectedConversationId) {
      setErrorMessage("请先选择会话");
      return;
    }
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      const response = await extractMemorySuggestions({
        conversationId: selectedConversationId,
        limit: 8,
      });
      setSuggestions(response.data || []);
      setStatusMessage("候选记忆已提取。接受后会用于后续回答。");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "提取候选记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  async function handleAcceptSuggestion(memoryId: number) {
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await acceptMemorySuggestion(memoryId);
      await loadMemoryState();
      setStatusMessage(
        "候选记忆已接受，后续回答会参考这条信息。",
      );
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "接受候选记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  async function handleIgnoreSuggestion(memoryId: number) {
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await ignoreMemorySuggestion(memoryId);
      await loadMemoryState();
      setStatusMessage("候选记忆已忽略。");
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "忽略候选记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  async function handleDeleteMemory(memoryId: number) {
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await deleteUserMemory(memoryId);
      await loadMemoryState();
      setStatusMessage("记忆已删除。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "删除记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      if (!sending && messageInput.trim()) {
        void handleSendMessage();
      }
    }
  }

  async function handleCopyMessage(content: string) {
    try {
      await navigator.clipboard.writeText(content);
      setStatusMessage("回答已复制。");
    } catch {
      setErrorMessage("当前浏览器不支持自动复制。");
    }
  }

  if (hasToken === false) {
    return (
      <main className="dp-chat-guest">
        <section className="dp-chat-guest-panel">
          <p className="dp-eyebrow">DocPilot Chat</p>
          <h1>AI 深度对话助手</h1>
          <p>
            支持会话记忆、知识库检索与回答溯源。登录后即可围绕文档资料持续提问，
            并查看回答参考的上下文来源。
          </p>
          <div className="dp-chat-guest-box">
            <span>Ask DocPilot anything...</span>
            <Link href="/login" className="dp-btn dp-btn-primary">
              登录开始对话
            </Link>
          </div>
          <div className="dp-chat-guest-tags">
            <span>会话记忆</span>
            <span>知识库问答</span>
            <span>上下文溯源</span>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="dp-chat-shell">
      {(sidebarOpen || inspectorOpen) && (
        <button
          type="button"
          aria-label="关闭浮层"
          className="dp-chat-overlay"
          onClick={() => {
            setSidebarOpen(false);
            setInspectorOpen(false);
          }}
        />
      )}

      <aside className={`dp-chat-sidebar ${sidebarOpen ? "is-open" : ""}`}>
        <div className="dp-chat-brand">
          <Link href="/" className="dp-chat-logo">
            DocPilot
          </Link>
          <button
            type="button"
            className="dp-chat-icon-btn md:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="关闭会话列表"
          >
            ×
          </button>
        </div>

        <section className="dp-chat-new-thread">
          <div className="flex items-center justify-between gap-2">
            <p className="text-sm font-semibold text-slate-100">新建对话</p>
            <button
              type="button"
              onClick={loadWorkspace}
              disabled={loading}
              className="dp-chat-side-link"
            >
              {loading ? "同步中" : "刷新"}
            </button>
          </div>
          <input
            className="dp-chat-dark-input"
            value={newTitle}
            onChange={(event) => setNewTitle(event.target.value)}
            placeholder="给这次对话起个名字"
          />
          <div className="grid grid-cols-2 gap-2">
            <select
              className="dp-chat-dark-input"
              value={newContextMode}
              onChange={(event) =>
                setNewContextMode(event.target.value as ConversationContextMode)
              }
            >
              <option value="AGENT_MEMORY">会话记忆</option>
              <option value="RECENT_TURNS">最近轮次</option>
            </select>
            <select
              className="dp-chat-dark-input"
              value={newBoundKnowledgeBaseId}
              onChange={(event) => setNewBoundKnowledgeBaseId(event.target.value)}
            >
              <option value="">不绑定 KB</option>
              {knowledgeBases.map((item) => (
                <option key={item.id} value={item.id}>
                  #{item.id} {item.name}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            onClick={handleCreateConversation}
            disabled={creating || !newTitle.trim()}
            className="dp-chat-new-btn"
          >
            {creating ? "创建中..." : "+ 新建对话"}
          </button>
        </section>

        <nav className="dp-chat-thread-list" aria-label="会话列表">
          {loading ? <p className="dp-chat-side-empty">正在加载会话...</p> : null}
          {!loading && conversations.length === 0 ? (
            <p className="dp-chat-side-empty">暂无会话，先创建一次深度对话。</p>
          ) : null}
          {conversations.map((item) => (
            <button
              type="button"
              key={item.conversationId}
              onClick={() => {
                setSelectedConversationId(item.conversationId);
                setSidebarOpen(false);
              }}
              className={`dp-chat-thread-item ${
                selectedConversationId === item.conversationId ? "is-active" : ""
              }`}
            >
              <span className="truncate text-sm font-semibold">
                {item.title || `会话 #${item.conversationId}`}
              </span>
              <span className="mt-1 flex items-center gap-2 text-xs text-slate-400">
                <span>{modeLabel(item.contextMode)}</span>
                {item.boundKnowledgeBaseId ? <span>KB #{item.boundKnowledgeBaseId}</span> : null}
              </span>
              <span className="mt-1 text-xs text-slate-500">
                {formatDateTime(item.lastMessageTime || item.createdAt)}
              </span>
            </button>
          ))}
        </nav>
      </aside>

      <section className="dp-chat-main">
        <header className="dp-chat-topbar">
          <button type="button" className="dp-chat-icon-btn md:hidden" onClick={() => setSidebarOpen(true)} aria-label="打开会话列表">
            ☰
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-slate-950">
              {selectedConversation?.title || "选择或创建一个对话"}
            </p>
            <div className="mt-1 flex flex-wrap gap-2">
              <span className="dp-chat-pill">{selectedConversation ? modeLabel(selectedConversation.contextMode) : "会话记忆"}</span>
              <span className="dp-chat-pill">{boundKnowledgeBaseLabel}</span>
              <span className="dp-chat-pill">精炼回答模式</span>
            </div>
          </div>
          <button
            type="button"
            className="dp-chat-icon-btn"
            onClick={() => {
              setInspectorOpen(true);
              setInspectorTab("trace");
            }}
            aria-label="打开上下文溯源"
          >
            ⌕
          </button>
        </header>

        {errorMessage ? <div className="dp-chat-alert dp-chat-alert-error">{errorMessage}</div> : null}
        {statusMessage ? <div className="dp-chat-alert dp-chat-alert-success">{statusMessage}</div> : null}

        <section className="dp-chat-thread" aria-label="聊天消息">
          {!selectedConversationId ? (
            <div className="dp-chat-welcome">
              <p className="dp-eyebrow">DocPilot Chat</p>
              <h1>让文档、知识库和记忆一起进入回答。</h1>
              <p>
                选择左侧会话，或新建一次带记忆的对话。摘要、记忆与引用来源
                会收纳在右侧溯源面板，聊天区只保留问题与回答。
              </p>
              <div className="dp-chat-flowline">
                {CONTEXT_FLOW.map((item) => (
                  <span key={item.label} title={item.detail}>{item.label}</span>
                ))}
              </div>
              <div className="dp-chat-suggestions">
                {SUGGESTED_PROMPTS.map((prompt) => (
                  <button key={prompt} type="button" onClick={() => setMessageInput(prompt)}>{prompt}</button>
                ))}
              </div>
            </div>
          ) : messagesLoading ? (
            <div className="dp-chat-loading">正在同步历史消息...</div>
          ) : messages.length === 0 ? (
            <div className="dp-chat-welcome">
              <p className="dp-eyebrow">New Chat</p>
              <h1>从一个问题开始。</h1>
              <p>
                你可以先绑定知识库，再询问“根据知识库”类问题；也可以直接让
                DocPilot 使用会话摘要与长期记忆回答。
              </p>
              <div className="dp-chat-suggestions">
                {SUGGESTED_PROMPTS.map((prompt) => (
                  <button key={prompt} type="button" onClick={() => setMessageInput(prompt)}>{prompt}</button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((message) => {
              const isAssistant = message.role === "ASSISTANT";
              return (
                <article key={message.messageId} className={`dp-chat-message ${isAssistant ? "is-assistant" : "is-user"}`}>
                  <div className="dp-chat-avatar" aria-hidden="true">{isAssistant ? "AI" : "你"}</div>
                  <div className="dp-chat-bubble">
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                      <span>{isAssistant ? "DocPilot" : "你"}</span>
                      <span>{formatDateTime(message.createdAt)}</span>
                    </div>
                    {isAssistant ? (
                      <MarkdownViewer markdown={message.content} showViewToggle={false} emptyText="暂无回答" variant="answer" mode="inline" />
                    ) : (
                      <p className="whitespace-pre-wrap break-words text-sm leading-7 text-slate-800">{message.content}</p>
                    )}

                    {isAssistant && message.citations && message.citations.length > 0 ? (
                      <div className="dp-chat-citations" aria-label="引用来源">
                        {message.citations.map((citation, index) => (
                          <button
                            type="button"
                            key={`${citation.documentId}-${citation.chunkId}-${index}`}
                            onClick={() => { void handleLoadTrace(message.messageId); }}
                            title={citation.snippet || "查看引用来源"}
                          >
                            [{index + 1}] {citation.documentTitle || `文档 #${citation.documentId}`}
                            <span>{typeof citation.score === "number" ? citation.score.toFixed(3) : "-"}</span>
                          </button>
                        ))}
                      </div>
                    ) : null}

                    {isAssistant ? (
                      <div className="dp-chat-message-actions">
                        <button type="button" onClick={() => void handleCopyMessage(message.content)}>复制</button>
                        <button type="button" onClick={() => handleLoadTrace(message.messageId)} disabled={traceLoading}>
                          {selectedTraceMessageId === message.messageId && traceLoading ? "加载溯源..." : "上下文溯源"}
                        </button>
                        <span>{message.citations?.length || 0} 条引用</span>
                      </div>
                    ) : null}
                  </div>
                </article>
              );
            })
          )}

          {sending ? (
            <article className="dp-chat-message is-assistant">
              <div className="dp-chat-avatar" aria-hidden="true">AI</div>
              <div className="dp-chat-bubble dp-chat-thinking">
                <p className="text-sm font-semibold text-slate-800">DocPilot 正在准备回答</p>
                <p className="mt-2 text-sm text-slate-500">{PENDING_STEPS[pendingStepIndex]}</p>
                <div className="mt-3 flex gap-1" aria-hidden="true"><span /><span /><span /></div>
              </div>
            </article>
          ) : null}
          <div ref={messagesEndRef} />
        </section>

        <footer className="dp-chat-composer-wrap">
          <div className="dp-chat-composer-meta">
            <select value={selectedKnowledgeBaseId} onChange={(event) => setSelectedKnowledgeBaseId(event.target.value)} disabled={!selectedConversationId || binding} aria-label="选择知识库">
              <option value="">不绑定知识库</option>
              {knowledgeBases.map((item) => (
                <option key={item.id} value={item.id}>#{item.id} {item.name}</option>
              ))}
            </select>
            <button type="button" onClick={handleBindKnowledgeBase} disabled={!selectedConversationId || !selectedKnowledgeBaseId || binding}>{binding ? "处理中" : "绑定"}</button>
            <button type="button" onClick={handleUnbindKnowledgeBase} disabled={!selectedConversationId || !selectedConversation?.boundKnowledgeBaseId || binding}>解绑</button>
            <button type="button" onClick={() => { setInspectorOpen(true); setInspectorTab("memory"); }}>记忆 {memories.length}</button>
          </div>
          <div className="dp-chat-composer">
            <textarea
              value={messageInput}
              onChange={(event) => setMessageInput(event.target.value)}
              onKeyDown={handleComposerKeyDown}
              placeholder="向 DocPilot 提问，Shift + Enter 换行"
              disabled={!selectedConversationId || sending}
              rows={1}
            />
            <div className="dp-chat-composer-actions">
              <button type="button" onClick={() => setMessageInput(DEFAULT_PROMPT)} disabled={sending}>模板</button>
              <button type="button" onClick={handleSendMessage} disabled={!selectedConversationId || sending || !messageInput.trim()} className="dp-chat-send">{sending ? "..." : "发送"}</button>
            </div>
          </div>
          <p className="dp-chat-composer-note">当前为精炼回答模式：DocPilot 会先整理上下文，再一次性返回回答；溯源面板只展示摘要级运行信息。</p>
        </footer>
      </section>

      <aside className={`dp-chat-inspector ${inspectorOpen ? "is-open" : ""}`}>
        <div className="dp-chat-inspector-head">
          <div><p className="dp-eyebrow">Context Inspector</p><h2>上下文溯源</h2></div>
          <button type="button" className="dp-chat-icon-btn" onClick={() => setInspectorOpen(false)} aria-label="关闭上下文溯源">×</button>
        </div>

        <div className="dp-chat-tabs" role="tablist" aria-label="上下文面板">
          <button type="button" className={inspectorTab === "trace" ? "is-active" : ""} onClick={() => setInspectorTab("trace")}>溯源</button>
          <button type="button" className={inspectorTab === "memory" ? "is-active" : ""} onClick={() => setInspectorTab("memory")}>记忆</button>
          <button type="button" className={inspectorTab === "summary" ? "is-active" : ""} onClick={() => setInspectorTab("summary")}>摘要</button>
        </div>

        {inspectorTab === "trace" ? (
          <section className="dp-chat-inspector-body">
            {!trace ? (
              <div className="dp-empty-state">发送消息后点击助手回答的“上下文溯源”，这里会展示本次回答参考的上下文来源、长度预算和引用来源摘要。</div>
            ) : (
              <div className="grid gap-4">
                <div className="dp-chat-trace-hero">
                  <p>本次回答来源</p>
                  <strong>{traceSources.join(" / ") || "最近轮次"}</strong>
                  <span>知识库 {trace.knowledgeBaseId ? `#${trace.knowledgeBaseId}` : "未使用"} · 来源 {trace.evidenceCount}</span>
                </div>
                <div className="dp-chat-metrics">
                  <div><span>模式</span><strong>{modeLabel(trace.contextMode)}</strong></div>
                  <div><span>上下文长度</span><strong>{trace.estimatedPromptTokens}/{trace.maxPromptTokens}</strong></div>
                  <div><span>记忆</span><strong>{trace.memoryCount}</strong></div>
                  <div><span>来源</span><strong>{trace.evidenceCount}</strong></div>
                </div>
                <dl className="dp-chat-trace-list">
                  <div><dt>摘要使用</dt><dd>{formatBoolean(trace.summaryUsed)}</dd></div>
                  <div><dt>最近消息</dt><dd>{trace.recentMessageCount} 条 / {trace.recentTurnCount} 轮</dd></div>
                  <div><dt>知识库检索</dt><dd>{formatBoolean(trace.ragTriggered)}</dd></div>
                  <div><dt>检索优先</dt><dd>{formatBoolean(trace.ragRequired)}</dd></div>
                  <div><dt>来源不足</dt><dd>{formatBoolean(trace.noEvidence)}</dd></div>
                  <div><dt>截断</dt><dd>{formatBoolean(trace.truncated)}</dd></div>
                  <div><dt>Fallback</dt><dd>{trace.fallbackReason || formatBoolean(trace.fallbackUsed)}</dd></div>
                  <div><dt>模型跳过</dt><dd>{formatBoolean(trace.modelCallSkipped)}</dd></div>
                </dl>
                <details className="dp-chat-detail-box">
                  <summary>来源文档分布与记忆类型</summary>
                  <p>来源文档: {formatHitCounts(trace.documentHitCounts)}</p>
                  <p>记忆类型: {trace.memoryTypes?.join(" / ") || "-"}</p>
                  <p>截断类型: {trace.truncatedTypes?.join(" / ") || "-"}</p>
                </details>
              </div>
            )}
          </section>
        ) : null}

        {inspectorTab === "memory" ? (
          <section className="dp-chat-inspector-body">
            <div className="dp-chat-memory-form">
              <select value={newMemoryType} onChange={(event) => setNewMemoryType(event.target.value as UserMemoryType)}>{MEMORY_TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select>
              <input value={newMemoryContent} onChange={(event) => setNewMemoryContent(event.target.value)} placeholder="添加一条长期记忆" />
              <input value={newMemoryPriority} onChange={(event) => setNewMemoryPriority(event.target.value.replace(/\D/g, "").slice(0, 3))} inputMode="numeric" aria-label="记忆优先级" />
              <button type="button" onClick={handleCreateMemory} disabled={memoryLoading || !newMemoryContent.trim()}>添加</button>
            </div>
            <div className="mt-4 flex items-center justify-between gap-2"><h3 className="text-sm font-bold text-slate-900">生效的长期记忆</h3><span className="dp-chat-pill">{memories.length} 条</span></div>
            <ul className="dp-chat-memory-list">
              {memories.length === 0 ? <li>暂无生效记忆。</li> : null}
              {memories.map((memory) => (
                <li key={memory.memoryId}>
                  <div className="flex items-center justify-between gap-2"><span>{memoryTypeLabel(memory.memoryType)}</span><button type="button" onClick={() => handleDeleteMemory(memory.memoryId)} disabled={memoryLoading}>删除</button></div>
                  <p>{memory.content}</p><small>priority {memory.priority ?? "-"}</small>
                </li>
              ))}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-2"><h3 className="text-sm font-bold text-slate-900">待确认的记忆候选</h3><button type="button" onClick={handleExtractSuggestions} disabled={!selectedConversationId || memoryLoading || messages.length === 0} className="dp-chat-small-btn">提取候选</button></div>
            <ul className="dp-chat-memory-list">
              {suggestions.length === 0 ? <li>暂无候选记忆。</li> : null}
              {suggestions.map((memory) => (
                <li key={memory.memoryId} className="is-suggestion">
                  <span>{memoryTypeLabel(memory.memoryType)} · confidence {memory.confidence ?? "-"}</span><p>{memory.content}</p>
                  <div className="mt-3 flex gap-2"><button type="button" onClick={() => handleAcceptSuggestion(memory.memoryId)} disabled={memoryLoading}>接受</button><button type="button" onClick={() => handleIgnoreSuggestion(memory.memoryId)} disabled={memoryLoading}>忽略</button></div>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        {inspectorTab === "summary" ? (
          <section className="dp-chat-inspector-body">
            <div className="flex items-center justify-between gap-2"><h3 className="text-sm font-bold text-slate-900">会话摘要</h3><span className={statusBadge(summary?.status)}>{summary?.status || "NOT_FOUND"}</span></div>
            <p className="dp-chat-summary-text">{summary?.summary || "暂无摘要。多轮对话后可以手动刷新，让历史信息以更短上下文参与后续回答。"}</p>
            <div className="dp-chat-summary-meta"><span>覆盖 {summary?.coveredStartSeq ?? "-"} - {summary?.coveredEndSeq ?? "-"}</span><span>Token {summary?.tokenCount ?? "-"}</span><span>版本 {summary?.summaryVersion ?? "-"}</span><span>更新 {formatDateTime(summary?.updatedAt)}</span></div>
            <div className="mt-4 flex gap-2"><button type="button" onClick={handleRefreshSummary} disabled={!selectedConversationId || summaryLoading} className="dp-btn dp-btn-primary">{summaryLoading ? "处理中..." : "刷新摘要"}</button><button type="button" onClick={handleDeleteSummary} disabled={!selectedConversationId || summaryLoading} className="dp-btn dp-btn-secondary">删除</button></div>
          </section>
        ) : null}
      </aside>
    </main>
  );
}
