import Link from "next/link";

export const dynamic = "force-static";

const capabilityItems = [
  {
    title: "文档上传与异步解析",
    description: "围绕上传、任务创建、解析状态追踪和异常重试组织主链路，便于演示后端工程闭环。"
  },
  {
    title: "问答与引用证据",
    description: "基于已解析文档发起问答，回答区域同步展示引用片段，让生成结果有可检查的依据。"
  },
  {
    title: "SSE 流式输出",
    description: "支持普通问答与流式问答两种体验，并保留 Markdown 渲染、降级提示和历史记录。"
  },
  {
    title: "Agent 工作流展示",
    description: "展示工具选择、执行步骤、最终回答、引用证据和 Trace 视图，帮助理解系统设计。"
  }
];

export default function HomePage() {
  return (
    <main className="dp-page max-w-5xl mx-auto py-12 px-6">
      <section className="text-center py-20">
        <h1 className="text-5xl font-extrabold text-slate-900 tracking-tight mb-6">DocPilot</h1>
        <p className="text-xl text-slate-600 max-w-2xl mx-auto mb-10 leading-relaxed">
          AI 文档解析与问答工程化平台：串联文档上传、异步解析、状态追踪、
          SSE 问答、引用证据和 Agent 工具工作流。
        </p>

        <div className="flex justify-center gap-4">
          <Link href="/login" className="dp-btn dp-btn-primary px-8 py-3 text-lg">
            进入演示工作台
          </Link>
          <Link href="/dashboard" className="dp-btn dp-btn-secondary px-8 py-3 text-lg">
            查看工作台
          </Link>
        </div>
      </section>

      <section className="mt-16">
        <h2 className="text-2xl font-bold text-slate-800 text-center mb-10">展示重点</h2>
        <div className="grid gap-6 md:grid-cols-2">
          {capabilityItems.map((item) => (
            <article key={item.title} className="p-8 bg-white rounded-2xl shadow-sm border border-slate-100 hover:shadow-md transition-shadow">
              <h3 className="text-lg font-bold text-slate-900 mb-3">{item.title}</h3>
              <p className="text-slate-600 leading-relaxed">{item.description}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
