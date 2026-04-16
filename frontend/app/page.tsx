import Link from "next/link";

export const dynamic = "force-static";

const capabilityItems = [
  {
    title: "智能文档解析",
    description: "上传文档后自动完成解析与特征提取，轻松管理您的知识库。"
  },
  {
    title: "精准溯源问答",
    description: "问答结果基于文档内容生成，并提供精确的原文引用片段，确保信息可靠性。"
  },
  {
    title: "多模式对话体验",
    description: "支持流式与普通问答，实时输出内容并保留完整的 Markdown 排版。"
  },
  {
    title: "完整的文档追踪",
    description: "随时查看文档解析状态、历史问答记录，支持一键重试异常任务。"
  }
];

export default function HomePage() {
  return (
    <main className="dp-page max-w-5xl mx-auto py-12 px-6">
      <section className="text-center py-20">
        <h1 className="text-5xl font-extrabold text-slate-900 tracking-tight mb-6">DocPilot</h1>
        <p className="text-xl text-slate-600 max-w-2xl mx-auto mb-10 leading-relaxed">
          您的智能文档助手。快速上传文档，精准提问，自动生成带有可靠引用的回答，让知识获取更高效。
        </p>

        <div className="flex justify-center gap-4">
          <Link href="/login" className="dp-btn dp-btn-primary px-8 py-3 text-lg">
            即刻开始使用
          </Link>
          <Link href="/dashboard" className="dp-btn dp-btn-secondary px-8 py-3 text-lg">
            进入控制台
          </Link>
        </div>
      </section>

      <section className="mt-16">
        <h2 className="text-2xl font-bold text-slate-800 text-center mb-10">核心特色</h2>
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

