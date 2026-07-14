export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T | null;
}

const API_PREFIX = "/backend";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code?: number,
    public readonly status?: number,
    public readonly rawMessage?: string
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function normalizeApiErrorMessage(payload: ApiResponse<unknown> | null, fallback: string): string {
  if (!payload) {
    return fallback;
  }
  if (
    payload.code === 403 &&
    payload.message.toLowerCase().includes("quality console is disabled")
  ) {
    return "质量控制台未开启：请在本地内部验证环境设置 APP_QUALITY_CONSOLE_ENABLED=true 后重启后端。";
  }
  if (
    payload.code === 403 &&
    payload.message.toLowerCase().includes("quality console forbidden")
  ) {
    return "仅内部管理员可访问质量控制台。";
  }
  if (payload.code === 1021 || payload.code === 1022) {
    return "无权限访问该知识库或该资源不存在，请确认当前登录账号和资源归属。";
  }
  if (payload.code === 1009 || payload.code === 1010) {
    return "文档不存在或当前账号无权访问，请重新选择自己的文档。";
  }
  if (payload.code === 403 || payload.message.includes("无权")) {
    return "当前账号无权限执行该操作，请确认资源归属。";
  }
  if (payload.code === 404 || payload.message.includes("不存在")) {
    return "资源不存在或已被删除，请刷新后重试。";
  }
  return payload.message || fallback;
}

function isFormDataBody(body: RequestInit["body"]): body is FormData {
  return typeof FormData !== "undefined" && body instanceof FormData;
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {}
): Promise<ApiResponse<T>> {
  const headers = new Headers(init.headers || undefined);
  if (!isFormDataBody(init.body) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...init,
    headers,
    cache: "no-store"
  });

  let payload: ApiResponse<T> | null = null;
  const contentType = response.headers.get("Content-Type") || "";
  if (contentType.includes("application/json")) {
    try {
      payload = (await response.json()) as ApiResponse<T>;
    } catch {
      throw new Error("Invalid API response format");
    }
  }

  if (!response.ok) {
    throw new ApiError(
      normalizeApiErrorMessage(payload, "Request failed"),
      payload?.code,
      response.status,
      payload?.message
    );
  }

  if (!payload) {
    throw new Error("Invalid API response format");
  }

  if (payload.code !== 0) {
    throw new ApiError(
      normalizeApiErrorMessage(payload, "Request failed"),
      payload.code,
      response.status,
      payload.message
    );
  }

  return payload;
}
