"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { loginByPassword, register } from "@/lib/auth-api";
import { getToken, saveToken } from "@/lib/auth";

type AuthMode = "register" | "login";

type FieldErrors = {
  username?: string;
  password?: string;
  confirmPassword?: string;
};

function isValidUsername(username: string): boolean {
  return /^[A-Za-z0-9_.-]{4,32}$/.test(username);
}

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<AuthMode>("register");
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (getToken()) {
      router.replace("/dashboard");
    }
  }, [router]);

  const submitLabel = useMemo(() => {
    if (submitting) {
      return mode === "register" ? "创建中..." : "进入中...";
    }
    return mode === "register" ? "创建并进入空间" : "进入工作空间";
  }, [mode, submitting]);

  function switchMode(nextMode: AuthMode) {
    if (mode === nextMode) {
      return;
    }
    setMode(nextMode);
    setErrorMessage("");
    setSuccessMessage("");
    setFieldErrors({});
    setPassword("");
    setConfirmPassword("");
  }

  function validateForm(): boolean {
    const nextErrors: FieldErrors = {};
    const normalizedUsername = username.trim();
    const normalizedPassword = password;

    if (!normalizedUsername) {
      nextErrors.username = "请输入用户名";
    } else if (!isValidUsername(normalizedUsername)) {
      nextErrors.username = "用户名需为 4-32 位，仅支持字母、数字、点、下划线或连字符";
    }

    if (!normalizedPassword) {
      nextErrors.password = "请输入密码";
    } else if (normalizedPassword.length < 8) {
      nextErrors.password = "密码长度至少 8 位";
    }

    if (mode === "register") {
      if (!confirmPassword) {
        nextErrors.confirmPassword = "请再次输入密码";
      } else if (confirmPassword !== normalizedPassword) {
        nextErrors.confirmPassword = "两次输入的密码不一致";
      }
    }

    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setSuccessMessage("");

    if (!validateForm()) {
      return;
    }

    setSubmitting(true);
    try {
      const normalizedUsername = username.trim();
      if (mode === "register") {
        const registerResponse = await register({
          username: normalizedUsername,
          password,
          nickname: nickname.trim() || undefined
        });
        const token = registerResponse.data?.token;
        if (!token) {
          throw new Error("账号已创建，但暂时无法进入工作空间，请稍后重试。");
        }
        saveToken(token);
        setSuccessMessage("账号已创建，正在初始化工作空间...");
      } else {
        const loginResponse = await loginByPassword({
          username: normalizedUsername,
          password
        });
        const token = loginResponse.data?.token;
        if (!token) {
          throw new Error("登录成功，但暂时无法进入工作空间，请稍后重试。");
        }
        saveToken(token);
        setSuccessMessage("登录成功，正在载入工作空间...");
      }

      window.setTimeout(() => {
        router.push("/dashboard");
      }, 450);
    } catch (error) {
      const message = error instanceof Error ? error.message : "无法进入工作空间，请检查账号信息后重试。";
      setErrorMessage(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="dp-page">
      <section className="grid gap-4 lg:grid-cols-[1.05fr_0.95fr]">
        <article className="dp-hero">
          <p className="dp-eyebrow">Workspace Access</p>
          <h1 className="dp-title">进入 DocPilot 工作空间</h1>
          <p className="dp-subtitle">
            创建或登录账号，管理你的文档、解析任务、知识库问答和会话记录。
            每个账号的数据相互隔离，便于持续追踪处理结果。
          </p>

          <div className="mt-4 grid gap-3 sm:grid-cols-3">
            <div className="dp-card-soft">
              <p className="dp-meta">1. 准备账号</p>
              <p className="mt-1 text-sm font-semibold">用于隔离你的文档与问答记录</p>
            </div>
            <div className="dp-card-soft">
              <p className="dp-meta">2. 进入空间</p>
              <p className="mt-1 text-sm font-semibold">查看文档状态与最近处理结果</p>
            </div>
            <div className="dp-card-soft">
              <p className="dp-meta">3. 开始处理</p>
              <p className="mt-1 text-sm font-semibold">上传 {"->"} 解析 {"->"} 提问 {"->"} 溯源</p>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap gap-2 text-sm">
            <Link href="/" className="dp-btn dp-btn-secondary">
              返回首页
            </Link>
            <Link href="/documents" className="dp-btn dp-btn-ghost">
              查看文档库
            </Link>
          </div>
        </article>

        <section className="dp-card">
          <div className="inline-flex w-full rounded-md border border-slate-200 bg-slate-50 p-1">
            <button
              type="button"
              onClick={() => switchMode("register")}
              className={`flex-1 rounded-md px-3 py-2 text-sm font-semibold ${
                mode === "register" ? "bg-blue-600 text-white" : "text-slate-700"
              }`}
            >
              创建账号
            </button>
            <button
              type="button"
              onClick={() => switchMode("login")}
              className={`flex-1 rounded-md px-3 py-2 text-sm font-semibold ${
                mode === "login" ? "bg-blue-600 text-white" : "text-slate-700"
              }`}
            >
              登录
            </button>
          </div>

          <h2 className="dp-section-title mt-4">{mode === "register" ? "创建账号" : "进入已有账号"}</h2>
          <p className="dp-subtitle">
            {mode === "register"
              ? "首次使用请先创建账号，成功后会自动进入工作空间。"
              : "已有账号可直接登录，继续查看文档处理和问答结果。"}
          </p>

          <form className="mt-4 space-y-3" onSubmit={handleSubmit}>
            <label className="block text-sm" htmlFor="auth-username-input">
              <span className="mb-1 block text-slate-700">用户名</span>
              <input
                id="auth-username-input"
                className="dp-input"
                placeholder="4-32 位，支持字母、数字、点、下划线或连字符"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
              />
              {fieldErrors.username ? <p className="mt-1 text-xs text-rose-600">{fieldErrors.username}</p> : null}
            </label>

            {mode === "register" ? (
              <label className="block text-sm" htmlFor="auth-nickname-input">
                <span className="mb-1 block text-slate-700">昵称（可选）</span>
                <input
                  id="auth-nickname-input"
                  className="dp-input"
                  placeholder="不填写则默认使用用户名"
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  maxLength={64}
                />
              </label>
            ) : null}

            <label className="block text-sm" htmlFor="auth-password-input">
              <span className="mb-1 block text-slate-700">密码</span>
              <input
                id="auth-password-input"
                type="password"
                className="dp-input"
                placeholder="至少 8 位"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete={mode === "register" ? "new-password" : "current-password"}
              />
              {fieldErrors.password ? <p className="mt-1 text-xs text-rose-600">{fieldErrors.password}</p> : null}
            </label>

            {mode === "register" ? (
              <label className="block text-sm" htmlFor="auth-confirm-password-input">
                <span className="mb-1 block text-slate-700">确认密码</span>
                <input
                  id="auth-confirm-password-input"
                  type="password"
                  className="dp-input"
                  placeholder="再次输入密码"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  autoComplete="new-password"
                />
                {fieldErrors.confirmPassword ? (
                  <p className="mt-1 text-xs text-rose-600">{fieldErrors.confirmPassword}</p>
                ) : null}
              </label>
            ) : null}

            {errorMessage ? <p className="dp-alert dp-alert-error">{errorMessage}</p> : null}
            {successMessage ? <p className="dp-alert dp-alert-success">{successMessage}</p> : null}

            <button type="submit" disabled={submitting} className="dp-btn dp-btn-primary w-full">
              {submitLabel}
            </button>
          </form>
        </section>
      </section>
    </main>
  );
}
