import { apiRequest, type ApiResponse } from "@/lib/api";
import { buildAuthorizationHeader } from "@/lib/auth";

export interface FileUploadData {
  id: number;
  userId: number;
  fileName: string;
  fileExt: string;
  contentType: string;
  fileSize: number;
  storagePath: string;
}

interface ChunkUploadInitData {
  uploadId: string;
  totalChunks: number;
  uploadedChunkIndexes: number[];
}

interface ChunkUploadStatusData {
  uploadId: string;
  totalChunks: number;
  uploadedCount: number;
  uploadedChunkIndexes: number[];
}

const CHUNK_UPLOAD_THRESHOLD_BYTES = 2 * 1024 * 1024;
const CHUNK_SIZE_BYTES = 512 * 1024;

export function uploadFile(file: File): Promise<ApiResponse<FileUploadData>> {
  const formData = new FormData();
  formData.append("file", file);

  return apiRequest<FileUploadData>("/api/file/upload", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: formData
  });
}

export function uploadFileWithProgress(
  file: File,
  onProgress?: (percent: number) => void
): Promise<ApiResponse<FileUploadData>> {
  if (file.size > CHUNK_UPLOAD_THRESHOLD_BYTES) {
    return uploadFileByChunks(file, onProgress);
  }

  const formData = new FormData();
  formData.append("file", file);

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/backend/api/file/upload");

    const authHeaders = buildAuthorizationHeader();
    Object.entries(authHeaders).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value);
    });

    if (onProgress) {
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable) {
          return;
        }
        const percent = Math.min(100, Math.round((event.loaded / event.total) * 100));
        onProgress(percent);
      };
    }

    xhr.onerror = () => {
      reject(new Error("上传失败，请检查网络后重试"));
    };

    xhr.onload = () => {
      let payload: ApiResponse<FileUploadData> | null = null;
      try {
        payload = JSON.parse(xhr.responseText) as ApiResponse<FileUploadData>;
      } catch (error) {
        reject(new Error("接口返回格式异常"));
        return;
      }

      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new Error(payload?.message || "上传失败"));
        return;
      }
      if (!payload || payload.code !== 0) {
        reject(new Error(payload?.message || "上传失败"));
        return;
      }

      onProgress?.(100);
      resolve(payload);
    };

    xhr.send(formData);
  });
}

async function uploadFileByChunks(
  file: File,
  onProgress?: (percent: number) => void
): Promise<ApiResponse<FileUploadData>> {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE_BYTES);
  const fileHash = await calculateFileHash(file);

  const initResponse = await apiRequest<ChunkUploadInitData>("/api/file/upload/chunk/init", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({
      fileName: file.name,
      fileSize: file.size,
      chunkSize: CHUNK_SIZE_BYTES,
      totalChunks,
      fileHash,
      contentType: file.type || "application/octet-stream"
    })
  });

  const uploadId = initResponse.data?.uploadId;
  if (!uploadId) {
    throw new Error("分片上传初始化失败");
  }

  const statusResponse = await apiRequest<ChunkUploadStatusData>(
    `/api/file/upload/chunk/status?uploadId=${encodeURIComponent(uploadId)}`,
    {
      method: "GET",
      headers: {
        ...buildAuthorizationHeader()
      }
    }
  );
  const uploadedIndexes = new Set<number>(statusResponse.data?.uploadedChunkIndexes || []);

  let completedChunks = uploadedIndexes.size;
  for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex += 1) {
    if (uploadedIndexes.has(chunkIndex)) {
      continue;
    }

    const start = chunkIndex * CHUNK_SIZE_BYTES;
    const end = Math.min(file.size, start + CHUNK_SIZE_BYTES);
    const chunk = file.slice(start, end);

    await uploadSingleChunk(uploadId, chunkIndex, chunk, (chunkPercent) => {
      const overall = ((completedChunks + chunkPercent / 100) / totalChunks) * 100;
      onProgress?.(Math.min(99, Math.round(overall)));
    });

    completedChunks += 1;
    const overallPercent = Math.round((completedChunks / totalChunks) * 100);
    onProgress?.(Math.min(99, overallPercent));
  }

  const completeResponse = await apiRequest<FileUploadData>("/api/file/upload/chunk/complete", {
    method: "POST",
    headers: {
      ...buildAuthorizationHeader()
    },
    body: JSON.stringify({ uploadId })
  });

  onProgress?.(100);
  return completeResponse;
}

function uploadSingleChunk(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  onProgress?: (percent: number) => void
): Promise<void> {
  const formData = new FormData();
  formData.append("uploadId", uploadId);
  formData.append("chunkIndex", String(chunkIndex));
  formData.append("chunk", chunk);

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/backend/api/file/upload/chunk");
    const authHeaders = buildAuthorizationHeader();
    Object.entries(authHeaders).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value);
    });

    if (onProgress) {
      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable) {
          return;
        }
        onProgress(Math.round((event.loaded / event.total) * 100));
      };
    }

    xhr.onerror = () => reject(new Error("分片上传失败，请稍后重试"));
    xhr.onload = () => {
      let payload: ApiResponse<ChunkUploadStatusData> | null = null;
      try {
        payload = JSON.parse(xhr.responseText) as ApiResponse<ChunkUploadStatusData>;
      } catch (error) {
        reject(new Error("分片响应格式异常"));
        return;
      }
      if (xhr.status < 200 || xhr.status >= 300 || !payload || payload.code !== 0) {
        reject(new Error(payload?.message || "分片上传失败"));
        return;
      }
      resolve();
    };

    xhr.send(formData);
  });
}

async function calculateFileHash(file: File): Promise<string> {
  if (!crypto?.subtle) {
    throw new Error("当前浏览器不支持文件哈希计算");
  }
  const buffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((value) => value.toString(16).padStart(2, "0")).join("");
}

