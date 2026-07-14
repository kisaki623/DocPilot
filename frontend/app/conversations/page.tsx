"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import MarkdownViewer from "@/components/markdown-viewer";
import { getToken } from "@/lib/auth";
import {
  citationChunkLabel,
  citationLocatorLabel,
  citationSourceTitle,
  citationStructureLabel,
} from "@/lib/citation-display";
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
  type GroundingPolicy,
} from "@/lib/conversation-api";
import {
  listKnowledgeBases,
  type KnowledgeBaseCitationItem,
  type KnowledgeBaseItem,
} from "@/lib/knowledge-base-api";
import {
  acceptMemorySuggestion,
  createUserMemory,
  disableUserMemory,
  deleteUserMemory,
  extractMemorySuggestions,
  ignoreMemorySuggestion,
  listDisabledUserMemories,
  listMemorySuggestions,
  listUserMemories,
  restoreUserMemory,
  resolveMemorySuggestion,
  updateUserMemory,
  type MemorySuggestionResolveAction,
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
  "正在判断是否需要知识库",
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
  if (status === "SUGGESTED" || status === "PENDING" || status === "ARCHIVED") {
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

function groundingPolicyLabel(policy?: string | null): string {
  if (policy === "MODEL_ONLY") {
    return "普通模型";
  }
  if (policy === "AUTO_RAG") {
    return "自动知识库";
  }
  if (policy === "STRICT_KB") {
    return "仅基于知识库";
  }
  return policy || "自动策略";
}

function routeDecisionLabel(routeDecision?: string | null): string {
  if (!routeDecision) {
    return "未记录路由";
  }
  const labels: Record<string, string> = {
    MODEL_ONLY: "直接模型回答",
    AUTO_NO_KB_MODEL: "未绑定知识库，模型回答",
    AUTO_INTENT_NOT_TRIGGERED_MODEL: "未触发知识库，模型回答",
    AUTO_RAG_EVIDENCE: "知识库命中",
    AUTO_NO_EVIDENCE_MODEL: "无证据，模型回答",
    AUTO_REQUIRED_NO_EVIDENCE_FALLBACK: "要求证据但资料不足",
    STRICT_NO_KB_FALLBACK: "严格模式未绑定知识库",
    STRICT_KB_EVIDENCE: "严格知识库命中",
    STRICT_NO_EVIDENCE_FALLBACK: "严格模式资料不足",
  };
  return labels[routeDecision] || routeDecision;
}

function isModelSkipped(trace?: ContextTraceData | null): boolean {
  return Boolean(trace?.modelSkipped ?? trace?.modelCallSkipped);
}

function assistantSourceLabel(message: ConversationMessageItem): string | null {
  if (message.role !== "ASSISTANT") {
    return null;
  }
  const trace = message.contextTrace;
  const citationStats = conversationCitationStats(message);
  const citationCount = citationStats.returnedCitationCount;
  const evidenceCount = trace?.evidenceCount || 0;
  const hitDocumentCount = positiveDocumentHitCount(trace?.documentHitCounts)
    || citationDocumentCount(message.citations || []);
  if (citationStats.actualCitationCount > 0) {
    return `实际引用 ${citationStats.actualCitationCount} · 召回证据 ${evidenceCount || citationCount} · 命中文档 ${hitDocumentCount}`;
  }
  if (citationCount > 0 || evidenceCount > 0) {
    return `召回证据 ${evidenceCount || citationCount} · 返回引用 ${citationCount} · 命中文档 ${hitDocumentCount}`;
  }
  if (
    isModelSkipped(trace) ||
    trace?.fallbackUsed ||
    trace?.routeDecision === "AUTO_REQUIRED_NO_EVIDENCE_FALLBACK" ||
    trace?.routeDecision === "STRICT_NO_EVIDENCE_FALLBACK" ||
    trace?.routeDecision === "STRICT_NO_KB_FALLBACK"
  ) {
    return "资料不足";
  }
  if (trace) {
    return "未使用知识库";
  }
  return null;
}

function memoryTypeLabel(type?: string): string {
  return (
    MEMORY_TYPES.find((item) => item.value === type)?.label || type || "自定义"
  );
}

function memorySourceLabel(sourceType?: string | null): string {
  if (sourceType === "SYSTEM_EXTRACTED") {
    return "系统候选";
  }
  if (sourceType === "MANUAL") {
    return "手动添加";
  }
  return sourceType || "未知来源";
}

function citationQuote(citation: { quoteText?: string; snippet?: string }): string {
  return citation.quoteText?.trim() || citation.snippet?.trim() || "查看引用来源";
}

function citationDisplayIndex(citation: KnowledgeBaseCitationItem, fallbackIndex: number): number {
  return typeof citation.index === "number" && citation.index > 0 ? citation.index : fallbackIndex + 1;
}

function citationAnchorId(messageId: number, citationIndex: number): string {
  return `citation-${messageId}-${citationIndex}`;
}

function citationAnchorHref(messageId: number, citationIndex: number): string {
  return `#${citationAnchorId(messageId, citationIndex)}`;
}

function citationScoreLabel(citation: KnowledgeBaseCitationItem): string {
  if (typeof citation.rerankScore === "number") {
    return `重排 ${citation.rerankScore.toFixed(3)}`;
  }
  if (typeof citation.fusedScore === "number") {
    return `融合 ${citation.fusedScore.toFixed(3)}`;
  }
  if (typeof citation.score === "number") {
    return `相似度 ${citation.score.toFixed(3)}`;
  }
  return "相似度 -";
}

function positiveDocumentHitCount(counts?: Record<string, number>): number {
  return Object.values(counts || {}).filter((count) => Number(count) > 0).length;
}

function citationDocumentCount(citations: KnowledgeBaseCitationItem[] = []): number {
  const keys = citations
    .map((citation) => citation.documentId?.toString() || citation.documentTitle?.trim() || "")
    .filter(Boolean);
  return new Set(keys).size;
}

function transformCitationMarkers(
  text: string,
  transform: (citationIndex: number, marker: string) => string,
): string {
  let inFence = false;
  return text
    .split("\n")
    .map((line) => {
      if (line.trimStart().startsWith("```")) {
        inFence = !inFence;
        return line;
      }
      if (inFence) {
        return line;
      }
      return line
        .split(/(`[^`]*`)/g)
        .map((part) => {
          if (part.startsWith("`") && part.endsWith("`")) {
            return part;
          }
          return part.replace(/\[(\d{1,3})\](?!\()/g, (marker, rawIndex, offset, source) => {
            if (source[offset - 1] === "!") {
              return marker;
            }
            return transform(Number(rawIndex), marker);
          });
        })
        .join("");
    })
    .join("\n");
}

function extractUsedCitationIndexes(
  markdown: string,
  citations: KnowledgeBaseCitationItem[] = [],
): number[] {
  const availableIndexes = new Set(citations.map((citation, index) => citationDisplayIndex(citation, index)));
  const usedIndexes: number[] = [];
  transformCitationMarkers(markdown || "", (citationIndex, marker) => {
    if (availableIndexes.has(citationIndex) && !usedIndexes.includes(citationIndex)) {
      usedIndexes.push(citationIndex);
    }
    return marker;
  });
  return usedIndexes;
}

function markdownWithCitationLinks(
  markdown: string,
  messageId: number,
  citations: KnowledgeBaseCitationItem[] = [],
): string {
  const availableIndexes = new Set(citations.map((citation, index) => citationDisplayIndex(citation, index)));
  if (availableIndexes.size === 0) {
    return markdown;
  }
  return transformCitationMarkers(markdown || "", (citationIndex, marker) => {
    if (!availableIndexes.has(citationIndex)) {
      return marker;
    }
    return `[${marker}](${citationAnchorHref(messageId, citationIndex)})`;
  });
}

function conversationCitationStats(message: ConversationMessageItem): {
  actualCitationCount: number;
  returnedCitationCount: number;
} {
  const citations = message.citations || [];
  return {
    actualCitationCount: extractUsedCitationIndexes(message.content || "", citations).length,
    returnedCitationCount: citations.length,
  };
}

function formatConfidence(value?: number | null): string {
  if (value === undefined || value === null) {
    return "-";
  }
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "-";
  }
  return numeric <= 1 ? `${Math.round(numeric * 100)}%` : String(numeric);
}

function normalizeMemoryContent(content?: string | null): string {
  return (content || "").trim().replace(/\s+/g, " ").toLowerCase();
}

function memorySourceText(memory: UserMemoryItem): string {
  const parts = [memorySourceLabel(memory.sourceType)];
  if (memory.sourceConversationId) {
    parts.push(`会话 #${memory.sourceConversationId}`);
  }
  if (memory.sourceMessageId) {
    parts.push(`消息 #${memory.sourceMessageId}`);
  }
  return parts.join(" · ");
}

function memoryGovernanceText(memory: UserMemoryItem): string {
  if (memory.conflictWithId) {
    return `与 #${memory.conflictWithId} 存在偏好冲突，接受前请先处理旧记忆。`;
  }
  if (memory.duplicateOfId) {
    const score = formatConfidence(memory.similarityScore);
    return `疑似重复 #${memory.duplicateOfId}${score === "-" ? "" : `，相似度 ${score}`}。`;
  }
  if (memory.governanceHint === "conflict_active_memory") {
    return "存在记忆冲突，接受前需要人工确认。";
  }
  if (memory.governanceHint === "similar_active_memory") {
    return "与现有记忆高度相似，建议合并或忽略。";
  }
  if (memory.governanceHint === "duplicate_active_memory") {
    return "与现有记忆重复，建议保留一条。";
  }
  return "";
}

function memoryGovernanceTargetId(memory: UserMemoryItem): number | null {
  return memory.conflictWithId ?? memory.duplicateOfId ?? null;
}

function formatBoolean(value?: boolean): string {
  return value ? "是" : "否";
}

function formatHitCounts(counts?: Record<string, number>): string {
  const entries = Object.entries(counts || {}).filter(([, count]) => Number(count) > 0);
  if (entries.length === 0) {
    return "-";
  }
  return entries
    .map(([documentId, count]) => `#${documentId}: ${count}`)
    .join(" / ");
}

function formatMs(value?: number | null): string {
  if (value === undefined || value === null || !Number.isFinite(Number(value))) {
    return "-";
  }
  return `${Math.max(0, Math.round(Number(value)))}ms`;
}

function formatNumberValue(value?: number | null): string {
  if (value === undefined || value === null || !Number.isFinite(Number(value))) {
    return "-";
  }
  return String(value);
}

function formatScoreValue(value?: number | null): string {
  if (value === undefined || value === null || !Number.isFinite(Number(value))) {
    return "-";
  }
  return Number(value).toFixed(3);
}

function stageLabel(stage: string): string {
  const labels: Record<string, string> = {
    conversationLoad: "会话加载",
    summary: "摘要读取",
    memory: "记忆选择",
    recentTurns: "最近轮次",
    retrieval: "知识库检索",
    permissionFilter: "权限过滤",
    tokenBudget: "Token 预算",
    promptRender: "Prompt 渲染",
    contextAssembly: "上下文装配",
    modelCall: "模型调用",
  };
  return labels[stage] || stage;
}

function evidenceGateLabel(status?: string | null): string {
  if (status === "PASSED") {
    return "通过";
  }
  if (status === "FAILED") {
    return "未通过";
  }
  if (status === "NOT_RUN") {
    return "未运行";
  }
  return status || "-";
}

function TraceTechnicalDetails({ trace }: { trace: ContextTraceData }) {
  const details = trace.technicalDetails;
  if (!details?.available) {
    return (
      <div className="dp-empty-state">
        这条回答暂无技术详情。历史 trace 仍会保留摘要信息，新生成的回答会记录安全的技术明细。
      </div>
    );
  }

  const route = details.route;
  const retrieval = details.retrieval;
  const tokenBudget = details.tokenBudget;
  const contextUsage = details.contextUsage;
  const fallback = details.fallback;
  const timings = Object.entries(details.timingsMs || {});
  const scoreRows = (retrieval?.scoreRows || []).slice(0, 10);

  return (
    <div className="dp-chat-tech-stack">
      <section className="dp-chat-tech-card">
        <h3>Trace 绑定</h3>
        <dl className="dp-chat-trace-list">
          <div><dt>traceId</dt><dd>{details.traceId || "-"}</dd></div>
          <div><dt>messageId</dt><dd>{details.messageId ?? trace.messageId ?? "-"}</dd></div>
          <div><dt>route</dt><dd>{routeDecisionLabel(route?.routeDecision || trace.routeDecision)}</dd></div>
          <div><dt>route reason</dt><dd>{route?.routeReason || "-"}</dd></div>
        </dl>
      </section>

      <section className="dp-chat-tech-card">
        <h3>路由与 Evidence Gate</h3>
        <dl className="dp-chat-trace-list">
          <div><dt>groundingPolicy</dt><dd>{groundingPolicyLabel(route?.groundingPolicy || trace.groundingPolicy)}</dd></div>
          <div><dt>ragTriggered</dt><dd>{formatBoolean(route?.ragTriggered ?? trace.ragTriggered)}</dd></div>
          <div><dt>ragRequired</dt><dd>{formatBoolean(route?.ragRequired ?? trace.ragRequired)}</dd></div>
          <div><dt>noEvidence</dt><dd>{formatBoolean(route?.noEvidence ?? trace.noEvidence)}</dd></div>
          <div><dt>llmCalled</dt><dd>{formatBoolean(Boolean(route?.llmCalled ?? trace.llmCalled))}</dd></div>
          <div><dt>modelSkipped</dt><dd>{formatBoolean(route?.modelSkipped ?? isModelSkipped(trace))}</dd></div>
          <div><dt>gate</dt><dd>{evidenceGateLabel(retrieval?.evidenceGate?.status)}</dd></div>
          <div><dt>gate reason</dt><dd>{retrieval?.evidenceGate?.reason || "-"}</dd></div>
        </dl>
      </section>

      <section className="dp-chat-tech-card">
        <h3>阶段耗时</h3>
        {timings.length === 0 ? (
          <p className="dp-chat-tech-muted">暂无耗时明细。</p>
        ) : (
          <div className="dp-chat-tech-pills">
            {timings.map(([stage, value]) => (
              <span key={stage}>{stageLabel(stage)} {formatMs(value)}</span>
            ))}
          </div>
        )}
      </section>

      <section className="dp-chat-tech-card">
        <h3>检索分数</h3>
        <dl className="dp-chat-trace-list">
          <div><dt>mode</dt><dd>{retrieval?.retrievalMode || "-"}</dd></div>
          <div><dt>provider</dt><dd>{retrieval?.provider || "-"}</dd></div>
          <div><dt>topK</dt><dd>{retrieval?.topK ?? "-"}</dd></div>
          <div><dt>evidenceCount</dt><dd>{retrieval?.evidenceCount ?? trace.evidenceCount}</dd></div>
          <div><dt>hit docs</dt><dd>{formatHitCounts(retrieval?.documentHitCounts || trace.documentHitCounts)}</dd></div>
          <div><dt>rerank</dt><dd>{formatBoolean(Boolean(retrieval?.rerankApplied))} {retrieval?.rerankModel || ""}</dd></div>
          <div><dt>multiQuery</dt><dd>{formatBoolean(Boolean(retrieval?.multiQueryApplied))} · variants {retrieval?.queryVariantCount ?? "-"}</dd></div>
          <div><dt>dedupe</dt><dd>{retrieval?.queryDedupeCount ?? "-"}</dd></div>
          {retrieval?.rerankFailureReason ? <div><dt>rerank failure</dt><dd>{retrieval.rerankFailureReason}</dd></div> : null}
        </dl>
        {scoreRows.length > 0 ? (
          <div className="dp-chat-score-table" role="table" aria-label="检索分数明细">
            <div role="row" className="dp-chat-score-head">
              <span>引用</span><span>文档 / chunk</span><span>分数</span>
            </div>
            {scoreRows.map((row, index) => (
              <div role="row" key={`${row.documentId || "doc"}-${row.chunkId || row.chunkIndex || index}`}>
                <span>{row.citationIndex ? `[${row.citationIndex}]` : "-"}{row.selectedAsCitation ? " 已引用" : ""}</span>
                <span>
                  <strong>{row.documentTitle || `文档 #${row.documentId || "-"}`}</strong>
                  <em>{row.locator || `chunk #${row.chunkIndex ?? "-"}`}</em>
                </span>
                <span>
                  final {formatScoreValue(row.finalScore)}
                  {" · "}vec {formatScoreValue(row.vectorScore)}
                  {" · "}kw {formatScoreValue(row.keywordScore)}
                  {" · "}fused {formatScoreValue(row.fusedScore)}
                  {" · "}rerank {formatScoreValue(row.rerankScore)}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="dp-chat-tech-muted">无检索分数行。</p>
        )}
        {(retrieval?.scoreRows || []).length > scoreRows.length ? (
          <p className="dp-chat-tech-muted">仅默认展示前 {scoreRows.length} 条安全分数元数据。</p>
        ) : null}
      </section>

      <section className="dp-chat-tech-card">
        <h3>Token 分配与丢弃</h3>
        <dl className="dp-chat-trace-list">
          <div><dt>budget</dt><dd>{tokenBudget?.estimatedPromptTokens ?? trace.estimatedPromptTokens}/{tokenBudget?.maxPromptTokens ?? trace.maxPromptTokens}</dd></div>
          <div><dt>truncated</dt><dd>{formatBoolean(tokenBudget?.truncated ?? trace.truncated)}</dd></div>
        </dl>
        <div className="dp-chat-token-grid">
          {(tokenBudget?.byType || []).map((item) => (
            <span key={item.type}>
              {item.type}: used {item.usedCount}/{formatNumberValue(item.usedTokens)}t · dropped {item.droppedCount}/{formatNumberValue(item.droppedTokens)}t
            </span>
          ))}
        </div>
        {(tokenBudget?.droppedReasons || []).length > 0 ? (
          <div className="dp-chat-tech-pills">
            {tokenBudget?.droppedReasons?.map((item) => (
              <span key={`${item.type}-${item.reason}`}>{item.type} × {item.count}: {item.reason}</span>
            ))}
          </div>
        ) : (
          <p className="dp-chat-tech-muted">无 dropped context。</p>
        )}
      </section>

      <section className="dp-chat-tech-card">
        <h3>Memory / Summary / Fallback</h3>
        <dl className="dp-chat-trace-list">
          <div><dt>summary</dt><dd>{formatBoolean(contextUsage?.summary?.used ?? trace.summaryUsed)}</dd></div>
          <div><dt>memory</dt><dd>{contextUsage?.memory?.count ?? trace.memoryCount} 条 · {(contextUsage?.memory?.types || trace.memoryTypes || []).join(" / ") || "-"}</dd></div>
          <div><dt>recent</dt><dd>{contextUsage?.recent?.messageCount ?? trace.recentMessageCount} 条 / {contextUsage?.recent?.turnCount ?? trace.recentTurnCount} 轮</dd></div>
          <div><dt>fallback</dt><dd>{formatBoolean(fallback?.used ?? trace.fallbackUsed)} {fallback?.reason || trace.fallbackReason || ""}</dd></div>
          <div><dt>safeError</dt><dd>{fallback?.safeError || "-"}</dd></div>
        </dl>
      </section>
    </div>
  );
}

function toPositiveNumber(value: string): number | undefined {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : undefined;
}

type CitationCardRefSetter = (key: string, node: HTMLButtonElement | null) => void;

function CitationEvidenceCard({
  citation,
  fallbackIndex,
  messageId,
  active,
  usedInAnswer,
  onFocus,
  registerRef,
}: {
  citation: KnowledgeBaseCitationItem;
  fallbackIndex: number;
  messageId: number;
  active: boolean;
  usedInAnswer: boolean;
  onFocus: (messageId: number, citationIndex: number) => void;
  registerRef: CitationCardRefSetter;
}) {
  const displayIndex = citationDisplayIndex(citation, fallbackIndex);
  const anchorId = citationAnchorId(messageId, displayIndex);
  const locator = citationLocatorLabel(citation);
  const structure = citationStructureLabel(citation);
  const chunk = citationChunkLabel(citation);
  const metaParts = [locator, structure, chunk, citation.documentId ? `文档 #${citation.documentId}` : ""]
    .filter(Boolean);
  const quote = citationQuote(citation);
  const hasExtraSnippet = Boolean(
    citation.quoteText?.trim()
      && citation.snippet?.trim()
      && citation.quoteText.trim() !== citation.snippet.trim(),
  );

  return (
    <button
      id={anchorId}
      ref={(node) => registerRef(anchorId, node)}
      type="button"
      className={`dp-chat-citation-card ${active ? "is-active" : ""}`}
      onClick={() => onFocus(messageId, displayIndex)}
      aria-label={`查看引用 ${displayIndex} 的证据片段`}
    >
      <span className="dp-chat-citation-card-head">
        <span className="dp-chat-citation-index">[{displayIndex}]</span>
        <strong>{citationSourceTitle(citation)}</strong>
        {usedInAnswer ? <em>正文引用</em> : null}
      </span>
      {metaParts.length > 0 ? <span className="dp-chat-citation-locator">{metaParts.join(" · ")}</span> : null}
      <span className="dp-chat-citation-quote">{quote}</span>
      {active && hasExtraSnippet ? (
        <span className="dp-chat-citation-context">上下文：{citation.snippet}</span>
      ) : null}
      <span className="dp-chat-citation-meta">
        <span>{citationScoreLabel(citation)}</span>
        {typeof citation.chunkIndex === "number" ? <span>chunk #{citation.chunkIndex}</span> : null}
      </span>
    </button>
  );
}

function ConversationCitationPanel({
  message,
  expanded,
  activeCitationKey,
  onToggleExpanded,
  onFocusCitation,
  registerRef,
}: {
  message: ConversationMessageItem;
  expanded: boolean;
  activeCitationKey: string;
  onToggleExpanded: (messageId: number) => void;
  onFocusCitation: (messageId: number, citationIndex: number) => void;
  registerRef: CitationCardRefSetter;
}) {
  const citations = message.citations || [];
  if (citations.length === 0) {
    return null;
  }

  const trace = message.contextTrace;
  const usedIndexes = extractUsedCitationIndexes(message.content || "", citations);
  const usedIndexSet = new Set(usedIndexes);
  const usedCitations = usedIndexes
    .map((usedIndex) => citations.find((citation, index) => citationDisplayIndex(citation, index) === usedIndex))
    .filter((citation): citation is KnowledgeBaseCitationItem => Boolean(citation));
  const defaultCitations = usedCitations.length > 0 ? usedCitations : citations.slice(0, Math.min(3, citations.length));
  const defaultCitationKeys = new Set(
    defaultCitations.map((citation, index) => {
      const sourceIndex = citations.indexOf(citation);
      return `${citation.documentId || ""}:${citation.chunkId || ""}:${citationDisplayIndex(citation, sourceIndex >= 0 ? sourceIndex : index)}`;
    }),
  );
  const supplementalCitations = citations.filter((citation, index) => {
    const key = `${citation.documentId || ""}:${citation.chunkId || ""}:${citationDisplayIndex(citation, index)}`;
    return !defaultCitationKeys.has(key);
  });
  const retrievalEvidenceCount = trace?.evidenceCount || citations.length;
  const hitDocumentCount = positiveDocumentHitCount(trace?.documentHitCounts)
    || citationDocumentCount(citations);
  const hasHiddenEvidence = supplementalCitations.length > 0;
  const unnumberedAnswer = usedCitations.length === 0;

  return (
    <section className="dp-chat-citation-panel" aria-label="回答引用来源">
      <div className="dp-chat-citation-summary">
        <span><strong>{usedCitations.length}</strong> 实际引用</span>
        <span><strong>{retrievalEvidenceCount}</strong> 召回证据</span>
        <span><strong>{hitDocumentCount}</strong> 命中文档</span>
      </div>
      {unnumberedAnswer ? (
        <p className="dp-chat-citation-hint">
          回答正文未标出引用编号，先展示前 {defaultCitations.length} 条返回证据；可展开查看全部。
        </p>
      ) : (
        <p className="dp-chat-citation-hint">默认展示正文中实际出现的引用编号，展开可查看全部返回证据。</p>
      )}
      <div className="dp-chat-citation-grid">
        {defaultCitations.map((citation, index) => {
          const displayIndex = citationDisplayIndex(citation, citations.indexOf(citation));
          const key = citationAnchorId(message.messageId, displayIndex);
          return (
            <CitationEvidenceCard
              key={`${citation.documentId}-${citation.chunkId}-${displayIndex}-${index}`}
              citation={citation}
              fallbackIndex={citations.indexOf(citation)}
              messageId={message.messageId}
              active={activeCitationKey === key}
              usedInAnswer={usedIndexSet.has(displayIndex)}
              onFocus={onFocusCitation}
              registerRef={registerRef}
            />
          );
        })}
      </div>
      {hasHiddenEvidence ? (
        <button
          type="button"
          className="dp-chat-citation-toggle"
          onClick={() => onToggleExpanded(message.messageId)}
          aria-expanded={expanded}
        >
          {expanded ? "收起全部返回证据" : `查看全部返回证据（${citations.length}）`}
        </button>
      ) : null}
      {expanded ? (
        <div className="dp-chat-citation-all" aria-label="补充返回证据">
          {supplementalCitations.map((citation, index) => {
            const sourceIndex = citations.indexOf(citation);
            const displayIndex = citationDisplayIndex(citation, sourceIndex >= 0 ? sourceIndex : index);
            const key = citationAnchorId(message.messageId, displayIndex);
            return (
              <CitationEvidenceCard
                key={`all-${citation.documentId}-${citation.chunkId}-${displayIndex}-${index}`}
                citation={citation}
                fallbackIndex={sourceIndex >= 0 ? sourceIndex : index}
                messageId={message.messageId}
                active={activeCitationKey === key}
                usedInAnswer={usedIndexSet.has(displayIndex)}
                onFocus={onFocusCitation}
                registerRef={registerRef}
              />
            );
          })}
        </div>
      ) : null}
    </section>
  );
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
  const [traceLayer, setTraceLayer] = useState<"summary" | "technical">("summary");
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
  const [selectedGroundingPolicy, setSelectedGroundingPolicy] =
    useState<GroundingPolicy>("AUTO_RAG");
  const [groundingPolicyManuallySelected, setGroundingPolicyManuallySelected] =
    useState(false);
  const [binding, setBinding] = useState(false);

  const [summary, setSummary] = useState<ConversationSummaryData | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [trace, setTrace] = useState<ContextTraceData | null>(null);
  const [traceLoading, setTraceLoading] = useState(false);
  const [selectedTraceMessageId, setSelectedTraceMessageId] = useState<
    number | null
  >(null);
  const citationCardRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const [activeCitationKey, setActiveCitationKey] = useState("");
  const [expandedCitationMessageIds, setExpandedCitationMessageIds] = useState<Set<number>>(
    () => new Set(),
  );

  const [memories, setMemories] = useState<UserMemoryItem[]>([]);
  const [disabledMemories, setDisabledMemories] = useState<UserMemoryItem[]>([]);
  const [suggestions, setSuggestions] = useState<UserMemoryItem[]>([]);
  const [memoryLoading, setMemoryLoading] = useState(false);
  const [newMemoryType, setNewMemoryType] = useState<UserMemoryType>("CUSTOM");
  const [newMemoryContent, setNewMemoryContent] = useState("");
  const [newMemoryPriority, setNewMemoryPriority] = useState("50");
  const [editingMemoryId, setEditingMemoryId] = useState<number | null>(null);
  const [editingMemoryContent, setEditingMemoryContent] = useState("");
  const [editingMemoryPriority, setEditingMemoryPriority] = useState("50");
  const [mergeSuggestionId, setMergeSuggestionId] = useState<number | null>(null);
  const [mergeContent, setMergeContent] = useState("");

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

  const selectedTraceMessage = useMemo(
    () =>
      messages.find((item) => item.messageId === selectedTraceMessageId)
      || null,
    [messages, selectedTraceMessageId],
  );

  const latestAssistantMessage = useMemo(
    () =>
      [...messages]
        .reverse()
        .find((item) => item.role === "ASSISTANT")
      || null,
    [messages],
  );

  const selectedTraceCitationStats = useMemo(
    () =>
      selectedTraceMessage
        ? conversationCitationStats(selectedTraceMessage)
        : { actualCitationCount: 0, returnedCitationCount: 0 },
    [selectedTraceMessage],
  );

  const resolvedGroundingPolicy = useMemo<GroundingPolicy>(() => {
    if (!selectedConversation?.boundKnowledgeBaseId) {
      return "MODEL_ONLY";
    }
    if (!groundingPolicyManuallySelected) {
      return "AUTO_RAG";
    }
    return selectedGroundingPolicy;
  }, [groundingPolicyManuallySelected, selectedConversation, selectedGroundingPolicy]);

  const sortedMemories = useMemo(() => {
    return [...memories].sort((left, right) => {
      const priorityDelta = (right.priority ?? 0) - (left.priority ?? 0);
      if (priorityDelta !== 0) {
        return priorityDelta;
      }
      return new Date(right.updatedAt || right.createdAt || 0).getTime()
        - new Date(left.updatedAt || left.createdAt || 0).getTime();
    });
  }, [memories]);

  const sortedDisabledMemories = useMemo(() => {
    return [...disabledMemories].sort((left, right) =>
      new Date(right.updatedAt || right.createdAt || 0).getTime()
      - new Date(left.updatedAt || left.createdAt || 0).getTime(),
    );
  }, [disabledMemories]);

  const sortedSuggestions = useMemo(() => {
    return [...suggestions].sort((left, right) => {
      const priorityDelta = (right.priority ?? 0) - (left.priority ?? 0);
      if (priorityDelta !== 0) {
        return priorityDelta;
      }
      return Number(right.confidence ?? 0) - Number(left.confidence ?? 0);
    });
  }, [suggestions]);

  const memoryTypeSummary = useMemo(() => {
    return MEMORY_TYPES.map((item) => ({
      ...item,
      count: memories.filter((memory) => memory.memoryType === item.value).length,
    })).filter((item) => item.count > 0);
  }, [memories]);

  const duplicateActiveMemoryIds = useMemo(() => {
    const byContent = new Map<string, UserMemoryItem[]>();
    memories.forEach((memory) => {
      const key = normalizeMemoryContent(memory.content);
      if (!key) {
        return;
      }
      byContent.set(key, [...(byContent.get(key) || []), memory]);
    });
    const ids = new Set<number>();
    byContent.forEach((items) => {
      if (items.length > 1) {
        items.forEach((item) => ids.add(item.memoryId));
      }
    });
    return ids;
  }, [memories]);

  const suggestionAlreadyActiveIds = useMemo(() => {
    const activeKeys = new Set(memories.map((memory) => normalizeMemoryContent(memory.content)).filter(Boolean));
    return new Set(
      suggestions
        .filter((memory) => activeKeys.has(normalizeMemoryContent(memory.content)) || Boolean(memory.duplicateOfId))
        .map((memory) => memory.memoryId),
    );
  }, [memories, suggestions]);

  const loadMemoryState = useCallback(async () => {
    setMemoryLoading(true);
    try {
      const [memoryResponse, disabledMemoryResponse, suggestionResponse] = await Promise.all([
        listUserMemories({ limit: 30 }),
        listDisabledUserMemories({ limit: 30 }).catch(() => ({ code: 0, message: "", data: [] })),
        listMemorySuggestions({ limit: 30 }),
      ]);
      setMemories(memoryResponse.data || []);
      setDisabledMemories(disabledMemoryResponse.data || []);
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
      setDisabledMemories([]);
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
        disabledMemoryResponse,
        suggestionResponse,
      ] = await Promise.all([
        listConversations(50),
        listKnowledgeBases(),
        listUserMemories({ limit: 30 }),
        listDisabledUserMemories({ limit: 30 }).catch(() => ({ code: 0, message: "", data: [] })),
        listMemorySuggestions({ limit: 30 }),
      ]);
      const nextConversations = conversationResponse.data || [];
      setConversations(nextConversations);
      setKnowledgeBases(knowledgeBaseResponse.data || []);
      setMemories(memoryResponse.data || []);
      setDisabledMemories(disabledMemoryResponse.data || []);
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
      setDisabledMemories([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadConversationState = useCallback(async (conversationId: number) => {
    setMessagesLoading(true);
    setErrorMessage("");
    setTrace(null);
    setSelectedTraceMessageId(null);
    setTraceLayer("summary");
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
      let nextMessages = messagesResponse.data || [];
      setSummary(summaryResponse.data || null);
      const latestTrace = [...nextMessages]
        .reverse()
        .find((item) => item.contextTrace);
      if (latestTrace?.contextTrace) {
        setTrace(latestTrace.contextTrace);
        setSelectedTraceMessageId(latestTrace.messageId);
      } else {
        const latestAssistant = [...nextMessages]
          .reverse()
          .find((item) => item.role === "ASSISTANT");
        if (latestAssistant) {
          try {
            const traceResponse = await getConversationMessageTrace(
              conversationId,
              latestAssistant.messageId,
            );
            if (traceResponse.data) {
              nextMessages = nextMessages.map((item) =>
                item.messageId === latestAssistant.messageId
                  ? { ...item, contextTrace: traceResponse.data }
                  : item,
              );
              setTrace(traceResponse.data);
              setSelectedTraceMessageId(latestAssistant.messageId);
            }
          } catch {
            // Trace is best-effort for historical messages; the chat should still render.
          }
        }
      }
      setMessages(nextMessages);
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
      setSelectedTraceMessageId(null);
      setTraceLayer("summary");
      setSelectedKnowledgeBaseId("");
    }
  }, [loadConversationState, selectedConversationId]);

  useEffect(() => {
    if (!selectedConversation?.boundKnowledgeBaseId) {
      setSelectedGroundingPolicy("MODEL_ONLY");
      setGroundingPolicyManuallySelected(false);
      return;
    }
    setSelectedGroundingPolicy("AUTO_RAG");
    setGroundingPolicyManuallySelected(false);
  }, [selectedConversation?.conversationId, selectedConversation?.boundKnowledgeBaseId]);

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
        setSelectedGroundingPolicy(response.data.boundKnowledgeBaseId ? "AUTO_RAG" : "MODEL_ONLY");
        setGroundingPolicyManuallySelected(false);
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
        resolvedGroundingPolicy,
      );
      const assistantMessage = response.data;
      if (assistantMessage) {
        setMessageInput("");
        await loadConversationState(selectedConversationId);
        setTrace(assistantMessage.contextTrace || null);
        setSelectedTraceMessageId(
          assistantMessage.contextTrace ? assistantMessage.messageId : null,
        );
        setTraceLayer("summary");
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
        setSelectedGroundingPolicy("AUTO_RAG");
        setGroundingPolicyManuallySelected(false);
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
        setSelectedGroundingPolicy("MODEL_ONLY");
        setGroundingPolicyManuallySelected(false);
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
      setTraceLayer("summary");
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

  async function handleOpenTraceInspector(messageId?: number) {
    setInspectorTab("trace");
    setTraceLayer("summary");
    setInspectorOpen(true);
    const targetMessageId =
      messageId
      ?? selectedTraceMessageId
      ?? latestAssistantMessage?.messageId
      ?? null;
    if (!targetMessageId) {
      setTrace(null);
      setSelectedTraceMessageId(null);
      return;
    }
    const targetMessage = messages.find((item) => item.messageId === targetMessageId);
    if (targetMessage?.contextTrace) {
      setTrace(targetMessage.contextTrace);
      setSelectedTraceMessageId(targetMessageId);
      return;
    }
    if (trace && selectedTraceMessageId === targetMessageId) {
      return;
    }
    await handleLoadTrace(targetMessageId);
  }

  const registerCitationCardRef = useCallback<CitationCardRefSetter>((key, node) => {
    if (node) {
      citationCardRefs.current[key] = node;
    } else {
      delete citationCardRefs.current[key];
    }
  }, []);

  const handleToggleCitationEvidence = useCallback((messageId: number) => {
    setExpandedCitationMessageIds((current) => {
      const next = new Set(current);
      if (next.has(messageId)) {
        next.delete(messageId);
      } else {
        next.add(messageId);
      }
      return next;
    });
  }, []);

  const handleFocusCitation = useCallback((messageId: number, citationIndex: number) => {
    const key = citationAnchorId(messageId, citationIndex);
    setActiveCitationKey(key);
    setExpandedCitationMessageIds((current) => {
      if (current.has(messageId)) {
        return current;
      }
      const next = new Set(current);
      next.add(messageId);
      return next;
    });
    window.requestAnimationFrame(() => {
      const target = citationCardRefs.current[key];
      target?.scrollIntoView({ block: "nearest", behavior: "smooth" });
      target?.focus();
    });
  }, []);

  const handleMarkdownAnchorClick = useCallback((messageId: number, href: string) => {
    const match = href.match(new RegExp(`^#citation-${messageId}-(\\d+)$`));
    if (!match) {
      return;
    }
    handleFocusCitation(messageId, Number(match[1]));
  }, [handleFocusCitation]);

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

  function handleStartEditMemory(memory: UserMemoryItem) {
    setEditingMemoryId(memory.memoryId);
    setEditingMemoryContent(memory.content || "");
    setEditingMemoryPriority(String(memory.priority ?? 50));
  }

  async function handleSaveMemory(memoryId: number) {
    const content = editingMemoryContent.trim();
    if (!content) {
      setErrorMessage("请输入记忆内容");
      return;
    }
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await updateUserMemory(memoryId, {
        content,
        priority: Number(editingMemoryPriority) || 50,
      });
      setEditingMemoryId(null);
      setEditingMemoryContent("");
      await loadMemoryState();
      setStatusMessage("长期记忆已更新。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "更新记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  function handleStartMergeSuggestion(memory: UserMemoryItem) {
    const activeMemoryId = memoryGovernanceTargetId(memory);
    const activeMemory = memories.find((item) => item.memoryId === activeMemoryId);
    setMergeSuggestionId(memory.memoryId);
    setMergeContent([activeMemory?.content, memory.content].filter(Boolean).join("\n"));
  }

  async function handleResolveSuggestion(
    memory: UserMemoryItem,
    action: MemorySuggestionResolveAction,
  ) {
    const activeMemoryId = memoryGovernanceTargetId(memory);
    if (!activeMemoryId) {
      setErrorMessage("缺少需要处理的生效记忆");
      return;
    }
    const mergedText = mergeContent.trim();
    if (action === "MERGE_WITH_ACTIVE" && !mergedText) {
      setErrorMessage("请输入合并后的记忆内容");
      return;
    }
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await resolveMemorySuggestion(memory.memoryId, {
        action,
        activeMemoryId,
        mergedContent: action === "MERGE_WITH_ACTIVE" ? mergedText : undefined,
      });
      setMergeSuggestionId(null);
      setMergeContent("");
      await loadMemoryState();
      setStatusMessage(action === "KEEP_ACTIVE" ? "已保留旧记忆并忽略候选。" : "记忆冲突已处理。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "处理候选记忆失败";
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

  async function handleDisableMemory(memoryId: number) {
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await disableUserMemory(memoryId);
      await loadMemoryState();
      setStatusMessage("记忆已停用，后续回答不会再使用它。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "停用记忆失败";
      setErrorMessage(message);
    } finally {
      setMemoryLoading(false);
    }
  }

  async function handleRestoreMemory(memoryId: number) {
    setMemoryLoading(true);
    setErrorMessage("");
    try {
      await restoreUserMemory(memoryId);
      await loadMemoryState();
      setStatusMessage("记忆已恢复，后续回答可以再次使用它。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "恢复记忆失败";
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
              <span className="dp-chat-pill">{groundingPolicyLabel(resolvedGroundingPolicy)}</span>
            </div>
          </div>
          <button
            type="button"
            className="dp-chat-icon-btn"
            onClick={() => void handleOpenTraceInspector()}
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
              const sourceLabel = assistantSourceLabel(message);
              return (
                <article key={message.messageId} className={`dp-chat-message ${isAssistant ? "is-assistant" : "is-user"}`}>
                  <div className="dp-chat-avatar" aria-hidden="true">{isAssistant ? "AI" : "你"}</div>
                  <div className="dp-chat-bubble">
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                      <span>{isAssistant ? "DocPilot" : "你"}</span>
                      <span>{formatDateTime(message.createdAt)}</span>
                    </div>
                    {isAssistant ? (
                      <MarkdownViewer
                        markdown={markdownWithCitationLinks(message.content, message.messageId, message.citations || [])}
                        showViewToggle={false}
                        emptyText="暂无回答"
                        variant="answer"
                        mode="inline"
                        onInternalAnchorClick={(href) => handleMarkdownAnchorClick(message.messageId, href)}
                      />
                    ) : (
                      <p className="whitespace-pre-wrap break-words text-sm leading-7 text-slate-800">{message.content}</p>
                    )}

                    {isAssistant ? (
                      <ConversationCitationPanel
                        message={message}
                        expanded={expandedCitationMessageIds.has(message.messageId)}
                        activeCitationKey={activeCitationKey}
                        onToggleExpanded={handleToggleCitationEvidence}
                        onFocusCitation={handleFocusCitation}
                        registerRef={registerCitationCardRef}
                      />
                    ) : null}

                    {isAssistant ? (
                      <div className="dp-chat-message-actions">
                        <button type="button" onClick={() => void handleCopyMessage(message.content)}>复制</button>
                        <button type="button" onClick={() => void handleOpenTraceInspector(message.messageId)} disabled={traceLoading}>
                          {selectedTraceMessageId === message.messageId && traceLoading ? "加载溯源..." : "上下文溯源"}
                        </button>
                        {sourceLabel ? <span>{sourceLabel}</span> : null}
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
            <select
              value={resolvedGroundingPolicy}
              onChange={(event) => {
                setSelectedGroundingPolicy(event.target.value as GroundingPolicy);
                setGroundingPolicyManuallySelected(true);
              }}
              disabled={!selectedConversationId || sending || !selectedConversation?.boundKnowledgeBaseId}
              aria-label="回答依据策略"
            >
              <option value="MODEL_ONLY">普通模型</option>
              {selectedConversation?.boundKnowledgeBaseId ? (
                <>
                  <option value="AUTO_RAG">自动知识库</option>
                  <option value="STRICT_KB">仅基于知识库</option>
                </>
              ) : null}
            </select>
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
          <p className="dp-chat-composer-note">
            精炼回答只控制篇幅和风格；回答依据由“普通模型 / 自动知识库 / 仅基于知识库”单独控制。未绑定知识库时不会触发资料不足拒答。
          </p>
        </footer>
      </section>

      <aside className={`dp-chat-inspector ${inspectorOpen ? "is-open" : ""}`}>
        <div className="dp-chat-inspector-head">
          <div>
            <p className="dp-eyebrow">Context Inspector</p>
            <h2>上下文溯源</h2>
            <span className="dp-chat-inspector-target">
              {selectedTraceMessage
                ? `绑定回答 #${selectedTraceMessage.messageId}`
                : "尚未绑定回答"}
            </span>
          </div>
          <button type="button" className="dp-chat-icon-btn" onClick={() => setInspectorOpen(false)} aria-label="关闭上下文溯源">×</button>
        </div>

        <div className="dp-chat-tabs" role="tablist" aria-label="上下文面板">
          <button type="button" className={inspectorTab === "trace" ? "is-active" : ""} onClick={() => setInspectorTab("trace")}>上下文</button>
          <button type="button" className={inspectorTab === "memory" ? "is-active" : ""} onClick={() => setInspectorTab("memory")}>记忆</button>
          <button type="button" className={inspectorTab === "summary" ? "is-active" : ""} onClick={() => setInspectorTab("summary")}>会话摘要</button>
        </div>

        {inspectorTab === "trace" ? (
          <section className="dp-chat-inspector-body">
            {!trace ? (
              <div className="dp-empty-state">发送消息后点击助手回答的“上下文溯源”，或使用顶部按钮打开最近一条回答。这里会展示绑定回答的摘要与安全技术详情。</div>
            ) : (
              <div className="grid gap-4">
                <div className="dp-chat-layer-tabs" role="tablist" aria-label="上下文溯源层级">
                  <button type="button" className={traceLayer === "summary" ? "is-active" : ""} onClick={() => setTraceLayer("summary")}>摘要</button>
                  <button type="button" className={traceLayer === "technical" ? "is-active" : ""} onClick={() => setTraceLayer("technical")}>技术详情</button>
                </div>
                {traceLayer === "summary" ? (
                  <>
                    <div className="dp-chat-trace-hero">
                      <p>本次回答来源</p>
                      <strong>{traceSources.join(" / ") || "最近轮次"}</strong>
                      <span>{groundingPolicyLabel(trace.groundingPolicy)} · {routeDecisionLabel(trace.routeDecision)}</span>
                      <span>
                        知识库 {trace.knowledgeBaseId ? `#${trace.knowledgeBaseId}` : "未使用"}
                        {" · "}召回证据 {trace.evidenceCount}
                        {" · "}实际引用 {selectedTraceCitationStats.actualCitationCount}
                        {" · "}命中文档 {positiveDocumentHitCount(trace.documentHitCounts)}
                      </span>
                    </div>
                    <div className="dp-chat-metrics">
                      <div><span>模式</span><strong>{modeLabel(trace.contextMode)}</strong></div>
                      <div><span>依据</span><strong>{groundingPolicyLabel(trace.groundingPolicy)}</strong></div>
                      <div><span>上下文长度</span><strong>{trace.estimatedPromptTokens}/{trace.maxPromptTokens}</strong></div>
                      <div><span>记忆</span><strong>{trace.memoryCount}</strong></div>
                      <div><span>召回证据</span><strong>{trace.evidenceCount}</strong></div>
                      <div><span>实际引用</span><strong>{selectedTraceCitationStats.actualCitationCount}</strong></div>
                    </div>
                    {trace.contextSourceCounts ? (
                      <div className="dp-chat-metrics">
                        <div><span>会话摘要</span><strong>{trace.contextSourceCounts.conversationSummary ?? 0}</strong></div>
                        <div><span>最近消息</span><strong>{trace.contextSourceCounts.recentMessages ?? 0}</strong></div>
                        <div><span>长期记忆</span><strong>{trace.contextSourceCounts.userMemory ?? 0}</strong></div>
                        <div><span>RAG 证据</span><strong>{trace.contextSourceCounts.ragEvidence ?? 0}</strong></div>
                      </div>
                    ) : null}
                    <dl className="dp-chat-trace-list">
                      <div><dt>绑定回答</dt><dd>#{selectedTraceMessage?.messageId ?? trace.messageId ?? "-"}</dd></div>
                      <div><dt>摘要使用</dt><dd>{formatBoolean(trace.summaryUsed)}</dd></div>
                      <div><dt>最近消息</dt><dd>{trace.recentMessageCount} 条 / {trace.recentTurnCount} 轮</dd></div>
                      <div><dt>知识库检索</dt><dd>{formatBoolean(trace.ragTriggered)}</dd></div>
                      <div><dt>检索优先</dt><dd>{formatBoolean(trace.ragRequired)}</dd></div>
                      <div><dt>路由决策</dt><dd>{routeDecisionLabel(trace.routeDecision)}</dd></div>
                      <div><dt>调用模型</dt><dd>{formatBoolean(Boolean(trace.llmCalled))}</dd></div>
                      <div><dt>来源不足</dt><dd>{formatBoolean(trace.noEvidence)}</dd></div>
                      <div><dt>截断</dt><dd>{formatBoolean(trace.truncated)}</dd></div>
                      <div><dt>Fallback</dt><dd>{trace.fallbackReason || formatBoolean(trace.fallbackUsed)}</dd></div>
                      <div><dt>模型跳过</dt><dd>{formatBoolean(isModelSkipped(trace))}</dd></div>
                    </dl>
                    <details className="dp-chat-detail-box">
                      <summary>命中文档分布与记忆类型</summary>
                      <p>命中文档: {formatHitCounts(trace.documentHitCounts)}</p>
                      <p>记忆类型: {trace.memoryTypes?.join(" / ") || "-"}</p>
                      <p>截断类型: {trace.truncatedTypes?.join(" / ") || "-"}</p>
                    </details>
                  </>
                ) : (
                  <TraceTechnicalDetails trace={trace} />
                )}
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
            <div className="dp-chat-memory-kpis">
              <span>生效 {memories.length}</span>
              <span>停用 {disabledMemories.length}</span>
              <span>候选 {suggestions.length}</span>
              <span>重复提示 {duplicateActiveMemoryIds.size + suggestionAlreadyActiveIds.size}</span>
            </div>
            {memoryTypeSummary.length > 0 ? (
              <div className="dp-chat-memory-type-row">
                {memoryTypeSummary.map((item) => <span key={item.value}>{item.label} {item.count}</span>)}
              </div>
            ) : null}
            <div className="mt-4 flex items-center justify-between gap-2"><h3 className="text-sm font-bold text-slate-900">生效的长期记忆</h3><span className="dp-chat-pill">{memories.length} 条</span></div>
            <ul className="dp-chat-memory-list">
              {memories.length === 0 ? <li>暂无生效记忆。</li> : null}
              {sortedMemories.map((memory) => {
                const governanceText = memoryGovernanceText(memory);
                const isEditing = editingMemoryId === memory.memoryId;
                return (
                <li key={memory.memoryId} className={duplicateActiveMemoryIds.has(memory.memoryId) || governanceText ? "has-warning" : ""}>
                  <div className="dp-chat-memory-card-head">
                    <span>{memoryTypeLabel(memory.memoryType)}</span>
                    <div className="dp-chat-memory-actions">
                      {isEditing ? (
                        <>
                          <button type="button" onClick={() => handleSaveMemory(memory.memoryId)} disabled={memoryLoading || !editingMemoryContent.trim()}>保存</button>
                          <button type="button" onClick={() => setEditingMemoryId(null)} disabled={memoryLoading}>取消</button>
                        </>
                      ) : (
                        <>
                          <button type="button" onClick={() => handleStartEditMemory(memory)} disabled={memoryLoading}>编辑</button>
                          <button type="button" onClick={() => handleDisableMemory(memory.memoryId)} disabled={memoryLoading}>停用</button>
                          <button type="button" onClick={() => handleDeleteMemory(memory.memoryId)} disabled={memoryLoading}>删除</button>
                        </>
                      )}
                    </div>
                  </div>
                  {isEditing ? (
                    <div className="dp-chat-memory-edit">
                      <textarea value={editingMemoryContent} onChange={(event) => setEditingMemoryContent(event.target.value)} />
                      <input value={editingMemoryPriority} onChange={(event) => setEditingMemoryPriority(event.target.value.replace(/\D/g, "").slice(0, 3))} inputMode="numeric" aria-label="编辑记忆优先级" />
                    </div>
                  ) : (
                    <p>{memory.content}</p>
                  )}
                  <small>priority {memory.priority ?? "-"}</small>
                  <div className="dp-chat-memory-meta">
                    <span>{memorySourceText(memory)}</span>
                    <span>confidence {formatConfidence(memory.confidence)}</span>
                    <span>更新 {formatDateTime(memory.updatedAt || memory.createdAt)}</span>
                  </div>
                  {duplicateActiveMemoryIds.has(memory.memoryId) ? <small className="dp-chat-memory-warning">内容与另一条生效记忆重复，后续可合并。</small> : null}
                  {governanceText ? <small className="dp-chat-memory-warning">{governanceText}</small> : null}
                </li>
                );
              })}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-2">
              <h3 className="text-sm font-bold text-slate-900">已停用的长期记忆</h3>
              <span className="dp-chat-pill">{disabledMemories.length} 条</span>
            </div>
            <p className="mt-1 text-xs leading-5 text-slate-500">停用后，后续回答不会使用这些记忆；恢复前会重新检查冲突和敏感内容。</p>
            <ul className="dp-chat-memory-list">
              {disabledMemories.length === 0 ? <li>暂无停用记忆。</li> : null}
              {sortedDisabledMemories.map((memory) => (
                <li key={memory.memoryId} className="is-suggestion">
                  <div className="dp-chat-memory-card-head">
                    <span>{memoryTypeLabel(memory.memoryType)}</span>
                    <div className="dp-chat-memory-actions">
                      <button type="button" onClick={() => handleRestoreMemory(memory.memoryId)} disabled={memoryLoading}>恢复</button>
                      <button type="button" onClick={() => handleDeleteMemory(memory.memoryId)} disabled={memoryLoading}>删除</button>
                    </div>
                  </div>
                  <span className={statusBadge(memory.status)}>{memory.status === "ARCHIVED" ? "已停用" : memory.status}</span>
                  <p>{memory.content}</p>
                  <small>priority {memory.priority ?? "-"}</small>
                  <div className="dp-chat-memory-meta">
                    <span>{memorySourceText(memory)}</span>
                    <span>confidence {formatConfidence(memory.confidence)}</span>
                    <span>更新 {formatDateTime(memory.updatedAt || memory.createdAt)}</span>
                  </div>
                </li>
              ))}
            </ul>
            <div className="mt-5 flex items-center justify-between gap-2"><h3 className="text-sm font-bold text-slate-900">待确认的记忆候选</h3><button type="button" onClick={handleExtractSuggestions} disabled={!selectedConversationId || memoryLoading || messages.length === 0} className="dp-chat-small-btn">提取候选</button></div>
            <ul className="dp-chat-memory-list">
              {suggestions.length === 0 ? <li>暂无候选记忆。</li> : null}
              {sortedSuggestions.map((memory) => {
                const alreadyActive = suggestionAlreadyActiveIds.has(memory.memoryId);
                const governanceText = memoryGovernanceText(memory);
                const targetMemoryId = memoryGovernanceTargetId(memory);
                const isMerging = mergeSuggestionId === memory.memoryId;
                return (
                <li key={memory.memoryId} className={`is-suggestion ${alreadyActive || governanceText ? "has-warning" : ""}`}>
                  <div className="dp-chat-memory-card-head">
                    <span>{memoryTypeLabel(memory.memoryType)}</span>
                    <span className={statusBadge(memory.status)}>{memory.status || "SUGGESTED"}</span>
                  </div>
                  <p>{memory.content}</p>
                  <div className="dp-chat-memory-meta">
                    <span>{memorySourceText(memory)}</span>
                    <span>priority {memory.priority ?? "-"}</span>
                    <span>confidence {formatConfidence(memory.confidence)}</span>
                    <span>更新 {formatDateTime(memory.updatedAt || memory.createdAt)}</span>
                  </div>
                  {alreadyActive ? <small className="dp-chat-memory-warning">与生效记忆内容相同，接受前建议先确认是否需要保留两条。</small> : null}
                  {governanceText ? <small className="dp-chat-memory-warning">{governanceText}</small> : null}
                  {isMerging ? (
                    <div className="dp-chat-memory-edit">
                      <textarea value={mergeContent} onChange={(event) => setMergeContent(event.target.value)} />
                      <div className="dp-chat-memory-actions">
                        <button type="button" onClick={() => handleResolveSuggestion(memory, "MERGE_WITH_ACTIVE")} disabled={memoryLoading || !mergeContent.trim()}>确认合并</button>
                        <button type="button" onClick={() => setMergeSuggestionId(null)} disabled={memoryLoading}>取消</button>
                      </div>
                    </div>
                  ) : null}
                  <div className="mt-3 flex flex-wrap gap-2">
                    {targetMemoryId ? (
                      <>
                        <button type="button" onClick={() => handleResolveSuggestion(memory, "KEEP_ACTIVE")} disabled={memoryLoading}>保留旧记忆</button>
                        <button type="button" onClick={() => handleResolveSuggestion(memory, "REPLACE_ACTIVE")} disabled={memoryLoading}>替换旧记忆</button>
                        <button type="button" onClick={() => handleStartMergeSuggestion(memory)} disabled={memoryLoading}>合并</button>
                      </>
                    ) : (
                      <>
                        <button type="button" onClick={() => handleAcceptSuggestion(memory.memoryId)} disabled={memoryLoading}>接受</button>
                        <button type="button" onClick={() => handleIgnoreSuggestion(memory.memoryId)} disabled={memoryLoading}>忽略</button>
                      </>
                    )}
                  </div>
                </li>
                );
              })}
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
