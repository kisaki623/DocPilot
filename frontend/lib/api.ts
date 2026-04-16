export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T | null;
}

const API_PREFIX = "/backend";

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
    throw new Error(payload?.message || "Request failed");
  }

  if (!payload) {
    throw new Error("Invalid API response format");
  }

  if (payload.code !== 0) {
    throw new Error(payload?.message || "Request failed");
  }

  return payload;
}
