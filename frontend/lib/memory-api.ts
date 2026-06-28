import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export type UserMemoryStatus = "ACTIVE" | "SUGGESTED" | "IGNORED" | "DELETED" | string;
export type UserMemoryType =
  | "PREFERENCE"
  | "PROJECT_STATE"
  | "TASK_GOAL"
  | "TECH_CONTEXT"
  | "ANSWER_STYLE"
  | "CUSTOM"
  | string;

export interface UserMemoryItem {
  memoryId: number;
  memoryType: UserMemoryType;
  content: string;
  sourceType?: string | null;
  sourceConversationId?: number | null;
  sourceMessageId?: number | null;
  status: UserMemoryStatus;
  priority?: number | null;
  confidence?: number | null;
  duplicateOfId?: number | null;
  conflictWithId?: number | null;
  governanceHint?: string | null;
  similarityScore?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

function buildQuery(params: { memoryType?: string; limit?: number } = {}): string {
  const searchParams = new URLSearchParams();
  if (params.memoryType) {
    searchParams.set("memoryType", params.memoryType);
  }
  if (params.limit) {
    searchParams.set("limit", String(params.limit));
  }
  const query = searchParams.toString();
  return query ? `?${query}` : "";
}

export function listUserMemories(params: {
  memoryType?: string;
  limit?: number;
} = {}): Promise<ApiResponse<UserMemoryItem[]>> {
  return apiRequest<UserMemoryItem[]>(`/api/memories${buildQuery(params)}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function createUserMemory(payload: {
  memoryType?: UserMemoryType;
  content: string;
  priority?: number;
  sourceConversationId?: number;
  sourceMessageId?: number;
}): Promise<ApiResponse<UserMemoryItem>> {
  return apiRequest<UserMemoryItem>("/api/memories", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function listMemorySuggestions(params: {
  memoryType?: string;
  limit?: number;
} = {}): Promise<ApiResponse<UserMemoryItem[]>> {
  return apiRequest<UserMemoryItem[]>(`/api/memories/suggestions${buildQuery(params)}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function extractMemorySuggestions(payload: {
  conversationId?: number;
  limit?: number;
}): Promise<ApiResponse<UserMemoryItem[]>> {
  return apiRequest<UserMemoryItem[]>("/api/memories/suggestions/extract", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify(payload)
  });
}

export function acceptMemorySuggestion(memoryId: number): Promise<ApiResponse<UserMemoryItem>> {
  return apiRequest<UserMemoryItem>(`/api/memories/suggestions/${memoryId}/accept`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function ignoreMemorySuggestion(memoryId: number): Promise<ApiResponse<UserMemoryItem>> {
  return apiRequest<UserMemoryItem>(`/api/memories/suggestions/${memoryId}/ignore`, {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function deleteUserMemory(memoryId: number): Promise<ApiResponse<UserMemoryItem>> {
  return apiRequest<UserMemoryItem>(`/api/memories/${memoryId}`, {
    method: "DELETE",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}
