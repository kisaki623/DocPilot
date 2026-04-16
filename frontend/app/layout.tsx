import type { Metadata } from "next";
import Link from "next/link";
import ShellAuthChip from "@/components/shell-auth-chip";
import "./globals.css";

export const metadata: Metadata = {
  title: "DocPilot | 智能文档解析与问答平台",
  description: "DocPilot：一站式智能文档管理、解析与安全溯源问答系统。"
};

const navItems = [
  { href: "/", label: "首页" },
  { href: "/dashboard", label: "控制台" },
  { href: "/upload", label: "上传" },
  { href: "/documents", label: "文档库" },
  { href: "/login", label: "登录" }
];

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>
        <div className="dp-app">
          <header className="dp-shell">
            <div className="dp-shell-inner">
              <Link href="/" className="dp-brand" aria-label="DocPilot 首页">
                <span className="dp-brand-mark">DP</span>
                <span className="dp-brand-text">
                  <span className="dp-brand-title">DocPilot</span>
                  <span className="dp-brand-subtitle">AI Document Intelligence</span>
                </span>
              </Link>

              <nav className="dp-shell-nav" aria-label="主导航">
                {navItems.map((item) => (
                  <Link key={item.href} href={item.href} className="dp-shell-link">
                    {item.label}
                  </Link>
                ))}
              </nav>

              <ShellAuthChip />
            </div>
          </header>

          <div className="dp-content">{children}</div>
        </div>
      </body>
    </html>
  );
}
