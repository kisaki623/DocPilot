import Link from "next/link";

export const dynamic = "force-static";

const flowSteps = [
  { label: "Upload", detail: "文件上传 / 文档记录" },
  { label: "Parse", detail: "RocketMQ + Outbox" },
  { label: "Index", detail: "Chunk + Embedding" },
  { label: "Retrieve", detail: "Qdrant + metadata filter" },
  { label: "Answer", detail: "回答 / 引用 / 流式输出" },
  { label: "Memory", detail: "摘要 + 会话记忆" },
];

const demoCards = [
  {
    href: "/dashboard",
    title: "文档工作空间",
    meta: "集中查看上传、解析、检索与回答状态",
    signal: "Start",
  },
  {
    href: "/knowledge-bases",
    title: "知识库检索",
    meta: "跨文档资料集的检索、回答与引用来源",
    signal: "RAG",
  },
  {
    href: "/conversations",
    title: "会话记忆",
    meta: "在多轮对话中保留摘要、偏好与引用上下文",
    signal: "Ctx",
  },
  {
    href: "/agent",
    title: "Agent 工具链",
    meta: "查看工具选择、执行记录与回答依据",
    signal: "Tool",
  },
];

const proofPoints = [
  ["检索", "基于文档片段召回相关内容，并在回答中保留引用来源"],
  ["资料集", "支持跨文档资料集提问，并呈现命中文档分布"],
  ["工具链", "Agent 可按任务选择文档工具，并保留执行记录"],
  ["记忆", "会话可结合摘要、长期记忆与知识库上下文回答"],
];

const boundaries = [
  "系统以受控配置运行，不在页面中暴露密钥、连接串或完整提示词。",
  "模型、向量检索与存储能力由当前运行环境配置决定。",
  "页面聚焦核心工作流与可追溯结果，详细运行说明保留在项目文档中。",
];

export default function HomePage() {
  return (
    <main className="dp-page max-w-7xl mx-auto px-4 py-8">
      <section className="dp-hero dp-hero-product">
        <div className="grid gap-8 lg:grid-cols-[0.95fr_1.05fr] lg:items-center">
          <div>
            <p className="dp-eyebrow">AI Document System</p>
            <h1 className="mt-3 max-w-4xl text-4xl font-extrabold leading-tight text-slate-950 md:text-6xl">
              DocPilot
            </h1>
            <p className="mt-5 max-w-3xl text-lg leading-8 text-slate-600">
              面向文档解析、语义检索与上下文问答的 AI 工作空间。DocPilot
              将上传解析、知识库检索、引用来源、工具调用和会话记忆整合在同一条清晰的工作流中。
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link href="/dashboard" className="dp-btn dp-btn-primary px-6">
                进入工作空间
              </Link>
              <Link
              href="/conversations"
              className="dp-btn dp-btn-secondary px-6"
            >
                打开会话记忆
              </Link>
              <Link href="/knowledge-bases" className="dp-btn dp-btn-ghost px-6">
                知识库检索
              </Link>
            </div>
          </div>

          <div className="dp-ai-panel">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-blue-700">
                  Live System Map
                </p>
                <p className="mt-1 text-sm text-slate-500">
                  文档进入系统后的核心数据流
                </p>
              </div>
              <span className="dp-badge dp-badge-success">verified</span>
            </div>
            <div className="dp-flow-grid">
              {flowSteps.map((item, index) => (
                <div key={item.label} className="dp-flow-node">
                  <span className="dp-flow-index">
                    {(index + 1).toString().padStart(2, "0")}
                  </span>
                  <p className="font-bold text-slate-950">{item.label}</p>
                  <p className="mt-1 text-xs leading-5 text-slate-500">
                    {item.detail}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {demoCards.map((item) => (
          <Link key={item.href} href={item.href} className="dp-command-card">
            <span className="dp-command-signal">{item.signal}</span>
            <h2 className="mt-4 text-lg font-bold text-slate-950">
              {item.title}
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              {item.meta}
            </p>
          </Link>
        ))}
      </section>

      <section className="grid gap-5 lg:grid-cols-[1fr_0.75fr]">
        <article className="dp-card">
          <div className="mb-5 flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
            <div>
              <p className="dp-eyebrow">Verified Capabilities</p>
          <h2 className="mt-2 text-2xl font-bold text-slate-950">
                当前可用的核心能力
              </h2>
            </div>
            <Link href="/dashboard" className="dp-btn dp-btn-secondary">
              查看推荐流程
            </Link>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            {proofPoints.map(([tag, text]) => (
              <div key={tag} className="dp-proof-row">
                <span className="dp-proof-tag">{tag}</span>
                <p className="text-sm leading-6 text-slate-700">{text}</p>
              </div>
            ))}
          </div>
        </article>

        <article className="dp-card dp-dark-card">
          <p className="text-xs font-bold uppercase tracking-wide text-blue-200">
            Notes
          </p>
          <h2 className="mt-2 text-xl font-bold text-white">运行说明</h2>
          <ul className="mt-4 grid gap-3">
            {boundaries.map((item) => (
              <li key={item} className="text-sm leading-6 text-slate-200">
                {item}
              </li>
            ))}
          </ul>
        </article>
      </section>
    </main>
  );
}
