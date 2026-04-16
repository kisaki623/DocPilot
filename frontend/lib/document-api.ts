import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface DocumentListItem {
  documentId: number;
  fileRecordId: number;
  fileName: string;
  fileType: string;
  parseStatus: string;
  parseStatusLabel: string;
  parseStatusDescription?: string;
  summary: string;
  createTime: string;
  updateTime: string;
}

export interface DocumentListData {
  pageNo: number;
  pageSize: number;
  total: number;
  records: DocumentListItem[];
}

export interface DocumentListQuery {
  pageNo: number;
  pageSize: number;
}

export interface DocumentDetailData {
  documentId: number;
  fileRecordId: number;
  title: string;
  fileName: string;
  fileType: string;
  parseStatus: string;
  parseStatusLabel: string;
  parseStatusDescription?: string;
  summary: string;
  content: string;
  createTime: string;
  updateTime: string;
}

export interface DocumentCreateData {
  id: number;
  userId: number;
  fileRecordId: number;
  title: string;
  parseStatus: string;
  reused?: boolean;
}

export function listDocuments(
  query: DocumentListQuery
): Promise<ApiResponse<DocumentListData>> {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize)
  });

  return apiRequest<DocumentListData>(`/api/document/list?${params.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function getDocumentDetail(
  documentId: number
): Promise<ApiResponse<DocumentDetailData>> {
  const params = new URLSearchParams({
    documentId: String(documentId)
  });

  return apiRequest<DocumentDetailData>(`/api/document/detail?${params.toString()}`, {
    method: "GET",
    headers: {
      ...buildAuthorizationHeader()
    }
  });
}

export function createDocument(
  fileRecordId: number
): Promise<ApiResponse<DocumentCreateData>> {
  return apiRequest<DocumentCreateData>("/api/document/create", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ fileRecordId })
  });
}
