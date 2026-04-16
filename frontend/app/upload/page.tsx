"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { getToken } from "@/lib/auth";
import { createDocument, getDocumentDetail, type DocumentDetailData } from "@/lib/document-api";
import { createParseTask, retryParseTask } from "@/lib/task-api";
import { uploadFileWithProgress, type FileUploadData } from "@/lib/upload-api";

type UploadStatus = "idle" | "uploading" | "success" | "failed";
type WorkflowStatus = "idle" | "creatingDocument" | "creatingTask" | "polling" | "ready" | "failed";

const TERMINAL_PARSE_STATUS = new Set(["SUCCESS", "FAILED"]);
const PARSE_POLLING_TIMEOUT_MS = 120_000;

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(2)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function isAuthError(message: string): boolean {
  return message.includes("登录") || message.includes("凭证") || message.includes("token");
}

function buildParseStatusText(detail: DocumentDetailData | null): string {
  if (!detail) {
    return "-";
  }
  const statusLabel = detail.parseStatusLabel || detail.parseStatus || "-";
  const statusDescription = detail.parseStatusDescription || "";
  return statusDescription ? `${statusLabel}（${statusDescription}）` : statusLabel;
}

function badgeByParseStatus(status: string | undefined): string {
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

export default function UploadPage() {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadResult, setUploadResult] = useState<FileUploadData | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [hasToken, setHasToken] = useState<boolean | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus, setUploadStatus] = useState<UploadStatus>("idle");
  const [workflowStatus, setWorkflowStatus] = useState<WorkflowStatus>("idle");
  const [documentId, setDocumentId] = useState<number | null>(null);
  const [parseTaskId, setParseTaskId] = useState<number | null>(null);
  const [detailSnapshot, setDetailSnapshot] = useState<DocumentDetailData | null>(null);
  const [pollingParseStatus, setPollingParseStatus] = useState(false);
  const [pollingStartedAt, setPollingStartedAt] = useState<number | null>(null);

  useEffect(() => {
    setHasToken(Boolean(getToken()));
  }, []);

  useEffect(() => {
    if (!pollingParseStatus || !documentId) {
      return;
    }

    let active = true;
    const timer = window.setInterval(async () => {
      try {
        if (pollingStartedAt && Date.now() - pollingStartedAt > PARSE_POLLING_TIMEOUT_MS) {
          setPollingParseStatus(false);
          setWorkflowStatus("failed");
          setErrorMessage("解析轮询超时，请检查 MQ 或消费者状态后重试。");
          return;
        }
        const response = await getDocumentDetail(documentId);
        const nextDetail = response.data;
        if (!active || !nextDetail) {
          return;
        }
        setDetailSnapshot(nextDetail);
        const status = nextDetail.parseStatus || "";
        if (TERMINAL_PARSE_STATUS.has(status)) {
          setPollingParseStatus(false);
          if (status === "SUCCESS") {
            setWorkflowStatus("ready");
            setSuccessMessage("主链路已打通：文件上传、文档创建、解析任务创建并完成解析。");
          } else {
            setWorkflowStatus("failed");
            setErrorMessage(`解析任务失败：${buildParseStatusText(nextDetail)}`);
          }
        }
      } catch (error) {
        if (!active) {
          return;
        }
        const message = error instanceof Error ? error.message : "查询解析状态失败";
        setPollingParseStatus(false);
        setWorkflowStatus("failed");
        setErrorMessage(message);
      }
    }, 2000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [documentId, pollingParseStatus, pollingStartedAt]);

  const selectedFileName = useMemo(() => {
    if (!selectedFile) {
      return "未选择文件";
    }
    return `${selectedFile.name} (${formatFileSize(selectedFile.size)})`;
  }, [selectedFile]);

  const uploadStatusText = useMemo(() => {
    if (uploadStatus === "uploading") {
      return `上传中：${uploadProgress}%`;
    }
    if (uploadStatus === "success") {
      return "上传完成";
    }
    if (uploadStatus === "failed") {
      return "上传失败";
    }
    return "等待上传";
  }, [uploadProgress, uploadStatus]);

  const workflowStatusText = useMemo(() => {
    if (workflowStatus === "creatingDocument") {
      return "正在创建文档记录（document/create）...";
    }
    if (workflowStatus === "creatingTask") {
      return "正在创建解析任务（task/parse/create）...";
    }
    if (workflowStatus === "polling") {
      return "解析进行中，正在轮询文档状态...";
    }
    if (workflowStatus === "ready") {
      return "解析完成，可直接进入文档详情继续问答。";
    }
    if (workflowStatus === "failed") {
      return "流程中断，请根据错误提示重试。";
    }
    return "等待开始";
  }, [workflowStatus]);

  const stepStates = useMemo(() => {
    const parseStatus = detailSnapshot?.parseStatus || "";
    return [
      {
        label: "上传文件",
        done: uploadStatus === "success",
        active: uploadStatus === "uploading"
      },
      {
        label: "创建文档",
        done: Boolean(documentId),
        active: workflowStatus === "creatingDocument"
      },
      {
        label: "创建解析任务",
        done: Boolean(parseTaskId),
        active: workflowStatus === "creatingTask"
      },
      {
        label: "解析中",
        done: parseStatus === "SUCCESS" || parseStatus === "FAILED",
        active: workflowStatus === "polling"
      }
    ];
  }, [detailSnapshot?.parseStatus, documentId, parseTaskId, uploadStatus, workflowStatus]);

  function resetWorkflowState() {
    setUploadResult(null);
    setDocumentId(null);
    setParseTaskId(null);
    setDetailSnapshot(null);
    setWorkflowStatus("idle");
    setPollingParseStatus(false);
    setPollingStartedAt(null);
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] || null;
    setSelectedFile(file);
    setErrorMessage("");
    setSuccessMessage("");
    setUploadProgress(0);
    setUploadStatus("idle");
    resetWorkflowState();
  }

  async function createAndDispatchParseTask(fileRecordId: number) {
    setWorkflowStatus("creatingDocument");
    const createDocumentResponse = await createDocument(fileRecordId);
    const createdDocumentId = createDocumentResponse.data?.id;
    if (!createdDocumentId) {
      throw new Error("文档创建成功但未返回 documentId");
    }
    setDocumentId(createdDocumentId);

    setWorkflowStatus("creatingTask");
    const createTaskResponse = await createParseTask(createdDocumentId);
    if (createTaskResponse.data?.taskId) {
      setParseTaskId(createTaskResponse.data.taskId);
    }

    const detailResponse = await getDocumentDetail(createdDocumentId);
    const detailData = detailResponse.data;
    if (detailData) {
      setDetailSnapshot(detailData);
      if (TERMINAL_PARSE_STATUS.has(detailData.parseStatus)) {
        if (detailData.parseStatus === "SUCCESS") {
          setWorkflowStatus("ready");
          setSuccessMessage("上传后自动完成文档创建与解析，可直接进入详情页问答。");
        } else {
          setWorkflowStatus("failed");
          setErrorMessage(`解析任务失败：${buildParseStatusText(detailData)}`);
        }
        return;
      }
    }

    setWorkflowStatus("polling");
    setPollingParseStatus(true);
    setPollingStartedAt(Date.now());
    setSuccessMessage("已自动创建文档与解析任务，正在等待解析完成。");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedFile) {
      setErrorMessage("请先选择一个文件");
      return;
    }

    if (!getToken()) {
      setHasToken(false);
      setErrorMessage("未检测到登录态，请先登录");
      return;
    }

    setSubmitting(true);
    setErrorMessage("");
    setSuccessMessage("");
    setUploadProgress(0);
    setUploadStatus("uploading");
    resetWorkflowState();
    let uploadCompleted = false;

    try {
      const uploadResponse = await uploadFileWithProgress(selectedFile, (percent) => {
        setUploadProgress(percent);
      });
      const uploadedRecord = uploadResponse.data;
      if (!uploadedRecord?.id) {
        throw new Error("上传成功但未返回 fileRecordId");
      }

      setUploadResult(uploadedRecord);
      setUploadStatus("success");
      setUploadProgress(100);
      setHasToken(true);
      uploadCompleted = true;
      setSuccessMessage("上传成功，正在自动创建文档与解析任务...");

      await createAndDispatchParseTask(uploadedRecord.id);
    } catch (error) {
      const message = error instanceof Error ? error.message : "上传失败";
      setErrorMessage(message);
      if (!uploadCompleted) {
        setUploadStatus("failed");
      }
      setWorkflowStatus("failed");
      if (isAuthError(message)) {
        setHasToken(false);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRetryCreateTask() {
    if (!documentId) {
      return;
    }
    setErrorMessage("");
    setSuccessMessage("");
    setSubmitting(true);
    try {
      setWorkflowStatus("creatingTask");
      const createTaskResponse = await retryParseTask(documentId);
      if (createTaskResponse.data?.taskId) {
        setParseTaskId(createTaskResponse.data.taskId);
      }
      setWorkflowStatus("polling");
      setPollingParseStatus(true);
      setPollingStartedAt(Date.now());
      setSuccessMessage("解析任务已重新创建，正在等待解析完成。");
    } catch (error) {
      const message = error instanceof Error ? error.message : "重试创建解析任务失败";
      setWorkflowStatus("failed");
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="dp-page max-w-4xl mx-auto py-8 px-4">
      <section className="bg-white rounded-2xl p-8 shadow-sm border border-slate-100 mb-8 text-center">
        <h1 className="text-3xl font-bold text-slate-900 mb-4">上传文档</h1>
        <p className="text-slate-600 max-w-2xl mx-auto">
          上传您的文件开始知识解析。支持 TXT、Markdown 和 PDF 格式，完成后可直接向文档提问。
        </p>
      </section>

      {hasToken === false ? (
        <section className="bg-red-50 text-red-600 p-4 rounded-xl mb-8 flex justify-between items-center">
          <span>未检测到有效登录态，请先前往登录页获取正确凭证。</span>
          <Link href="/login" className="dp-btn dp-btn-primary whitespace-nowrap px-4 py-2">
            前往登录
          </Link>
        </section>
      ) : null}

      <section className="grid gap-6 lg:grid-cols-2 lg:items-start disabled:opacity-50">
        <article className={`bg-white rounded-2xl p-6 shadow-sm border border-slate-100 ${hasToken === false ? "opacity-50 pointer-events-none" : ""}`}>
          <h2 className="text-xl font-bold text-slate-900 mb-2">选择文件</h2>
          <p className="text-sm text-slate-500 mb-6">支持 txt / md / pdf 文件。</p>

          <form className="space-y-4" onSubmit={handleSubmit}>
            <input
              id="upload-file-input"
              ref={fileInputRef}
              type="file"
              accept=".txt,.md,.pdf"
              onChange={handleFileChange}
              className="sr-only"
            />

            <div className="dp-card-soft">
              <p className="dp-meta">选择文件</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="dp-btn dp-btn-secondary"
                >
                  浏览本地文件
                </button>
                <span className="text-sm font-semibold text-slate-700">{selectedFileName}</span>
              </div>
            </div>

            <div className="space-y-1">
              <div className="flex items-center justify-between text-sm text-slate-600">
                <span>上传状态</span>
                <span>{uploadStatusText}</span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded bg-slate-200">
                <div className="h-full rounded bg-blue-600 transition-all" style={{ width: `${uploadProgress}%` }} />
              </div>
            </div>

            {errorMessage ? <p className="dp-alert dp-alert-error">{errorMessage}</p> : null}
            {successMessage ? <p className="dp-alert dp-alert-success">{successMessage}</p> : null}

            <div className="flex flex-wrap gap-2">
              <button type="submit" disabled={submitting} className="dp-btn dp-btn-primary">
                {submitting ? `处理中 ${uploadProgress}%` : "提交上传"}
              </button>
              {workflowStatus === "failed" && documentId ? (
                <button
                  type="button"
                  onClick={handleRetryCreateTask}
                  disabled={submitting}
                  className="dp-btn dp-btn-secondary"
                >
                  重试解析任务
                </button>
              ) : null}
              <Link href="/documents" className="dp-btn dp-btn-ghost">
                去文档列表
              </Link>
              {documentId ? (
                <Link href={`/documents/${documentId}`} className="dp-btn dp-btn-secondary">
                  去文档详情
                </Link>
              ) : null}
            </div>
          </form>
        </article>

        <article className="bg-white rounded-2xl p-6 shadow-sm border border-slate-100">
          <h2 className="text-xl font-bold text-slate-900 mb-2">处理状态追踪</h2>
          <p className="text-sm text-slate-500 mb-6">实时展示文档的上传与解析进度。</p>

          <div className="space-y-4">
            <ul className="mt-3 space-y-2">
              {stepStates.map((step, index) => (
                <li key={step.label} className="rounded-xl border border-slate-200 bg-white p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span
                        className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold ${
                          step.done
                            ? "bg-emerald-100 text-emerald-700"
                            : step.active
                              ? "bg-blue-100 text-blue-700"
                              : "bg-slate-100 text-slate-500"
                        }`}
                      >
                        {index + 1}
                      </span>
                      <span className="text-sm text-slate-700">{step.label}</span>
                    </div>
                    {step.done ? (
                      <span className="dp-badge dp-badge-success">已完成</span>
                    ) : step.active ? (
                      <span className="dp-badge dp-badge-info">进行中</span>
                    ) : (
                      <span className="dp-badge dp-badge-neutral">未开始</span>
                    )}
                  </div>
                </li>
              ))}
            </ul>

            <div className="mt-3 dp-card-soft">
              <p className="text-sm font-semibold">当前解析状态</p>
              <div className="mt-2 flex items-center gap-2">
                <span className={badgeByParseStatus(detailSnapshot?.parseStatus)}>{detailSnapshot?.parseStatusLabel || detailSnapshot?.parseStatus || "-"}</span>
                <span className="text-xs text-slate-500">{detailSnapshot?.parseStatusDescription || "等待触发"}</span>
              </div>
              <p className="mt-2 text-xs text-slate-500">流程提示：{workflowStatusText}</p>
            </div>

            {workflowStatus === "ready" ? (
              <div className="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 p-3">
                <p className="text-sm font-semibold text-emerald-800">下一步建议</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {documentId ? (
                    <Link href={`/documents/${documentId}`} className="dp-btn dp-btn-primary">
                      进入详情问答
                    </Link>
                  ) : null}
                  <Link href="/documents" className="dp-btn dp-btn-secondary">
                    返回文档列表
                  </Link>
                </div>
              </div>
            ) : null}

            {workflowStatus === "failed" ? (
              <div className="mt-3 rounded-xl border border-rose-200 bg-rose-50 p-3">
                <p className="text-sm font-semibold text-rose-800">流程中断</p>
                <p className="mt-1 text-xs text-rose-700">请查看错误提示并优先使用“重试解析任务”继续闭环。</p>
              </div>
            ) : null}

            <details className="mt-3 dp-card-soft">
              <summary className="cursor-pointer text-sm font-semibold text-slate-700">技术 ID 与调试信息</summary>
              <div className="mt-2 space-y-1 text-xs text-slate-600">
                <p>fileRecordId: {uploadResult?.id ?? "-"}</p>
                <p>documentId: {documentId ?? "-"}</p>
                <p>taskId: {parseTaskId ?? "-"}</p>
                <p>parseStatus: {buildParseStatusText(detailSnapshot)}</p>
              </div>
            </details>
          </div>
        </article>
      </section>
    </main>
  );
}
