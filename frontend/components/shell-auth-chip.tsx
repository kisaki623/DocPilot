"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { clearToken, getToken } from "@/lib/auth";

export default function ShellAuthChip() {
  const pathname = usePathname();
  const [loggedIn, setLoggedIn] = useState<boolean | null>(null);

  useEffect(() => {
    setLoggedIn(Boolean(getToken()));

    const onStorage = () => {
      setLoggedIn(Boolean(getToken()));
    };

    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener("storage", onStorage);
    };
  }, [pathname]);

  if (!loggedIn) {
    return (
      <div className="flex items-center gap-2">
        <span className="dp-badge dp-badge-neutral">{loggedIn === null ? "检测中" : "未登录"}</span>
        <Link href="/login" className="dp-btn dp-btn-ghost">
          去登录
        </Link>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <span className="dp-badge dp-badge-success">已登录</span>
      <button
        type="button"
        onClick={() => {
          clearToken();
          setLoggedIn(false);
          window.location.href = "/login";
        }}
        className="dp-btn dp-btn-secondary"
      >
        退出
      </button>
    </div>
  );
}
