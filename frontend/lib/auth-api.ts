import { apiRequest, type ApiResponse } from "@/lib/api";

export interface SendCodePayload {
  phone: string;
}

export interface SendCodeData {
  phone: string;
  devCode: string;
}

export interface LoginPayload {
  phone: string;
  code: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  nickname?: string;
}

export interface PasswordLoginPayload {
  username: string;
  password: string;
}

export interface LoginData {
  token: string;
  userId: number;
  username?: string | null;
  phone?: string | null;
  nickname: string;
}

export function sendCode(payload: SendCodePayload): Promise<ApiResponse<SendCodeData>> {
  return apiRequest<SendCodeData>("/api/auth/code", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function login(payload: LoginPayload): Promise<ApiResponse<LoginData>> {
  return apiRequest<LoginData>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function register(payload: RegisterPayload): Promise<ApiResponse<LoginData>> {
  return apiRequest<LoginData>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function loginByPassword(payload: PasswordLoginPayload): Promise<ApiResponse<LoginData>> {
  return apiRequest<LoginData>("/api/auth/password/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

