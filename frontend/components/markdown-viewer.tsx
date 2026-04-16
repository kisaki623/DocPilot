"use client";

import { useState } from "react";
import ReactMarkdown, { type UrlTransform } from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import rehypeHighlight from "rehype-highlight";
import styles from "./markdown-viewer.module.css";

type MarkdownViewMode = "rendered" | "raw";
type MarkdownVariant = "document" | "summary" | "answer" | "history";

type MarkdownViewerProps = {
  markdown?: string | null;
  showViewToggle?: boolean;
  defaultView?: MarkdownViewMode;
  emptyText?: string;
  className?: string;
  variant?: MarkdownVariant;
};

const ALLOWED_PROTOCOLS = new Set(["http:", "https:", "mailto:", "tel:"]);
const sanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    code: [...(defaultSchema.attributes?.code || []), ["className"]],
    span: [...(defaultSchema.attributes?.span || []), ["className"]]
  }
};

const safeUrlTransform: UrlTransform = (url) => {
  const normalizedUrl = (url || "").trim();
  if (!normalizedUrl) {
    return "";
  }

  if (
    normalizedUrl.startsWith("#") ||
    normalizedUrl.startsWith("/") ||
    normalizedUrl.startsWith("./") ||
    normalizedUrl.startsWith("../")
  ) {
    return normalizedUrl;
  }

  try {
    const parsed = new URL(normalizedUrl, "https://docpilot.local");
    if (ALLOWED_PROTOCOLS.has(parsed.protocol.toLowerCase())) {
      return normalizedUrl;
    }
  } catch {
    return "#";
  }

  return "#";
};

export default function MarkdownViewer({
  markdown,
  showViewToggle = true,
  defaultView = "rendered",
  emptyText = "-",
  className,
  variant = "document"
}: MarkdownViewerProps) {
  const [viewMode, setViewMode] = useState<MarkdownViewMode>(defaultView);
  const content = markdown || "";
  const hasContent = content.trim().length > 0;
  const activeViewMode: MarkdownViewMode = showViewToggle ? viewMode : "rendered";
  const viewerVariantClass = variant === "summary"
    ? styles.viewerSummary
    : variant === "answer"
      ? styles.viewerAnswer
      : variant === "history"
        ? styles.viewerHistory
        : styles.viewerDocument;
  const panelVariantClass = variant === "summary"
    ? styles.panelSummary
    : variant === "answer"
      ? styles.panelAnswer
      : variant === "history"
        ? styles.panelHistory
        : styles.panelDocument;
  const markdownVariantClass = variant === "summary"
    ? styles.markdownSummary
    : variant === "answer"
      ? styles.markdownAnswer
      : variant === "history"
        ? styles.markdownHistory
        : styles.markdownDocument;
  const rawVariantClass = variant === "summary"
    ? styles.rawTextSummary
    : variant === "answer"
      ? styles.rawTextAnswer
      : variant === "history"
        ? styles.rawTextHistory
        : styles.rawTextDocument;
  const viewerClassName = [styles.viewer, viewerVariantClass, className].filter(Boolean).join(" ");

  return (
    <div className={viewerClassName}>
      {showViewToggle ? (
        <div className={styles.toolbar} role="tablist" aria-label="Markdown display mode">
          <button
            type="button"
            role="tab"
            aria-selected={activeViewMode === "rendered"}
            onClick={() => setViewMode("rendered")}
            className={`${styles.toggleButton} ${activeViewMode === "rendered" ? styles.toggleButtonActive : ""}`}
          >
            渲染
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeViewMode === "raw"}
            onClick={() => setViewMode("raw")}
            className={`${styles.toggleButton} ${activeViewMode === "raw" ? styles.toggleButtonActive : ""}`}
          >
            原文
          </button>
        </div>
      ) : null}

      <div className={[styles.panel, panelVariantClass].join(" ")}>
        {!hasContent ? (
          <p className={styles.emptyText}>{emptyText}</p>
        ) : activeViewMode === "raw" ? (
          <pre className={[styles.rawText, rawVariantClass].join(" ")}>{content}</pre>
        ) : (
          <div className={[styles.markdown, markdownVariantClass].join(" ")}>
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              rehypePlugins={[rehypeHighlight, [rehypeSanitize, sanitizeSchema]]}
              skipHtml
              urlTransform={safeUrlTransform}
              components={{
                a: ({ children, ...props }) => (
                  <a {...props} rel="noopener noreferrer nofollow" target="_blank">
                    {children}
                  </a>
                )
              }}
            >
              {content}
            </ReactMarkdown>
          </div>
        )}
      </div>
    </div>
  );
}
