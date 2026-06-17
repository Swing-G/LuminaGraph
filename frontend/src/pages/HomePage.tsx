import { ArrowRight, Boxes, BrainCircuit, GitBranch, Github, Layers3, Route, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";

const capabilityCards = [
  {
    name: "Workflow",
    title: "状态驱动执行链路",
    description: "将长工单与运维任务拆为可控节点，承接条件分支、中间结果和状态回滚。",
    icon: GitBranch,
    className: "left-2 top-12 w-[300px] rotate-[-4deg] bg-[#21182B] text-[#FFF8EF] shadow-[0_28px_70px_rgba(31,24,38,0.28)]",
    accent: "bg-[#F4A261] text-[#21182B]"
  },
  {
    name: "RAG",
    title: "私域知识检索增强",
    description: "融合 pgvector、上下文压缩与异步摘要，让问答能追溯，也能持续吸收资料。",
    icon: Layers3,
    className: "right-2 top-8 w-[292px] rotate-[5deg] bg-[#FFF8EF] text-[#21182B] shadow-[0_26px_64px_rgba(31,24,38,0.14)] ring-1 ring-[#E8DDCF]",
    accent: "bg-[#EDE6FF] text-[#5B4AB8]"
  },
  {
    name: "ReAct & PaE",
    title: "推理与行动闭环",
    description: "在工具调用前后保留观察与反思，降低多步执行里的发散和误触发。",
    icon: BrainCircuit,
    className: "left-24 top-[190px] w-[318px] rotate-[2deg] bg-[#3B2C46] text-[#FFF8EF] shadow-[0_30px_72px_rgba(31,24,38,0.24)]",
    accent: "bg-[#9DD6CB] text-[#173A35]"
  },
  {
    name: "Harness",
    title: "生成与验收分离",
    description: "独立 Evaluator 负责校验，配合 Reflection 纠错，给长链路任务加上安全边界。",
    icon: Route,
    className: "right-1 top-[280px] w-[300px] rotate-[-5deg] bg-[#F2D6B6] text-[#21182B] shadow-[0_24px_60px_rgba(129,76,42,0.2)]",
    accent: "bg-[#21182B] text-[#FFF8EF]"
  },
  {
    name: "More",
    title: "开放工具与未来引擎",
    description: "MCP 网关、结构化 Skill、多模型降级和全链路观测，为更多 Agent 形态预留扩展位。",
    icon: Boxes,
    className: "left-4 bottom-4 w-[336px] rotate-[4deg] bg-[#EEF2EA] text-[#21182B] shadow-[0_26px_64px_rgba(31,24,38,0.13)] ring-1 ring-[#DDE7D8]",
    accent: "bg-[#DDECC8] text-[#315533]"
  }
];

export function HomePage() {
  const navigate = useNavigate();

  return (
    <main className="relative min-h-[100dvh] overflow-hidden bg-[#F8F1E7] text-[#1F1826]">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_16%_14%,rgba(244,154,95,0.22),transparent_30%),radial-gradient(circle_at_82%_20%,rgba(105,91,205,0.16),transparent_28%),radial-gradient(circle_at_64%_86%,rgba(51,154,137,0.14),transparent_30%),linear-gradient(135deg,#FFF8EF_0%,#F8F1E7_54%,#ECEBFA_100%)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(31,24,38,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(31,24,38,0.08)_1px,transparent_1px)] [background-size:48px_48px]"
      />
      <div className="relative mx-auto flex min-h-[100dvh] w-full max-w-7xl flex-col px-6 py-6 sm:px-8 lg:px-10">
        <nav className="flex items-center justify-between lg:-mx-20 xl:-mx-28 2xl:-mx-36">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#1F1826] text-[#F8F1E7] shadow-[0_16px_34px_rgba(31,24,38,0.18)]">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <p className="text-base font-semibold tracking-[-0.03em]">LuminaGraph</p>
              <p className="text-xs text-[#756676]">AI Agent 编排系统</p>
            </div>
          </div>
          <a
            href="https://github.com/Swing-G/LuminaGraph"
            target="_blank"
            rel="noreferrer"
            className="hidden items-center gap-2 rounded-full border border-[#1F1826]/14 bg-white/70 px-4 py-2.5 text-sm font-medium text-[#1F1826] shadow-[0_12px_28px_rgba(31,24,38,0.08)] transition hover:-translate-y-0.5 hover:bg-white sm:inline-flex"
            aria-label="打开 LuminaGraph GitHub 仓库"
          >
            <Github className="h-4 w-4" />
            GitHub
          </a>
        </nav>

        <section className="grid flex-1 items-center gap-12 py-14 lg:grid-cols-[0.92fr_1.08fr] lg:py-8">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full border border-[#1F1826]/10 bg-white/62 px-3 py-2 text-xs font-semibold tracking-[0.18em] text-[#6E5B78] shadow-[0_10px_24px_rgba(31,24,38,0.06)]">
              <span className="h-2 w-2 rounded-full bg-[#E96F4B]" />
              AGENT WORKFLOW RUNTIME
            </div>
            <h1 className="mt-7 max-w-[700px] text-balance text-[clamp(3rem,7vw,6rem)] font-semibold leading-[0.88] tracking-[-0.078em] text-[#21182B]">
              把复杂任务编排成光。
            </h1>
            <p className="mt-7 max-w-[62ch] text-lg leading-8 text-[#51465A]">
              LuminaGraph 面向企业复杂任务场景，为大模型提供可控执行链路。通过任务拆解、状态驱动执行、多阶段校验与工具编排，支撑私域问答、长工单处理和智能运维。
            </p>
            <div className="mt-10 flex flex-col gap-4 sm:flex-row sm:items-center">
              <button
                type="button"
                onClick={() => navigate("/chat")}
                className="group inline-flex h-14 items-center justify-center gap-3 rounded-2xl bg-[#21182B] px-7 text-base font-semibold text-[#FFF8EF] shadow-[0_22px_44px_rgba(31,24,38,0.22)] transition hover:-translate-y-0.5 hover:bg-[#33243F] active:scale-[0.98]"
              >
                进入对话
                <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#F4A261] text-[#21182B] transition group-hover:translate-x-0.5">
                  <ArrowRight className="h-4 w-4" />
                </span>
              </button>
            </div>
          </div>

          <div className="relative hidden min-h-[560px] lg:block">
            <div className="absolute inset-x-8 top-14 h-[420px] rounded-[3.25rem] border border-[#1F1826]/10 bg-white/34 shadow-[inset_0_1px_0_rgba(255,255,255,0.8)]" />
            <div className="absolute right-6 top-0 h-40 w-40 rounded-full bg-[#7467D9]/20 blur-3xl" />
            <div className="absolute bottom-12 left-8 h-44 w-44 rounded-full bg-[#F4A261]/24 blur-3xl" />
            {capabilityCards.map((card) => {
              const Icon = card.icon;
              return (
                <article
                  key={card.name}
                  className={`absolute rounded-[2rem] p-5 transition duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] hover:z-20 hover:-translate-y-2 hover:rotate-0 ${card.className}`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className={`flex h-11 w-11 items-center justify-center rounded-2xl ${card.accent}`}>
                      <Icon className="h-5 w-5" />
                    </div>
                    <span className="rounded-full bg-current/10 px-3 py-1 text-[11px] font-semibold tracking-[0.14em] opacity-70">
                      {card.name}
                    </span>
                  </div>
                  <h2 className="mt-5 text-xl font-semibold tracking-[-0.04em]">{card.title}</h2>
                  <p className="mt-3 text-sm leading-6 opacity-72">{card.description}</p>
                </article>
              );
            })}
          </div>
        </section>
      </div>
    </main>
  );
}
