import * as React from "react";
import { ArrowUpRight, BookOpen, Brain, Check, Lightbulb, Orbit, Send, Square } from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
  icon: React.ComponentType<{ className?: string }>;
};

const PRESET_ICONS = [BookOpen, Check, Lightbulb];

const DEFAULT_PRESETS: PromptPreset[] = [
  {
    title: "快速归纳",
    description: "从长内容中抽取结论、风险与下一步",
    prompt: "请阅读以下内容，提炼核心结论、关键风险和下一步建议：",
    icon: BookOpen
  },
  {
    title: "方案推演",
    description: "把目标拆成路径、依赖与优先级",
    prompt: "请围绕下面目标给出可执行方案，包含步骤、依赖、优先级和验收标准：",
    icon: Check
  },
  {
    title: "知识追问",
    description: "基于已有资料继续延展与对比",
    prompt: "请基于相关知识库回答下面问题，并说明依据与不确定点：",
    icon: Lightbulb
  }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [promptPresets, setPromptPresets] = React.useState<PromptPreset[]>(DEFAULT_PRESETS);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const { sendMessage, isStreaming, cancelGeneration, deepThinkingEnabled, setDeepThinkingEnabled } =
    useChatStore();

  const focusInput = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    const next = Math.min(el.scrollHeight, 160);
    el.style.height = `${next}px`;
  }, []);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    let active = true;

    const loadPresets = async () => {
      const data = await listSampleQuestions().catch(() => null);
      if (!active || !data || data.length === 0) return;
      const mapped = data
        .filter((item) => item.question && item.question.trim())
        .slice(0, 3)
        .map((item, index) => {
          const question = item.question.trim();
          const title =
            item.title?.trim() ||
            (question.length > 12 ? `${question.slice(0, 12)}...` : question) ||
            `推荐问法 ${index + 1}`;
          const description = item.description?.trim() || "点选后可直接开始对话";
          return {
            id: item.id,
            title,
            description,
            prompt: question,
            icon: PRESET_ICONS[index % PRESET_ICONS.length]
          };
        });
      if (mapped.length > 0) setPromptPresets(mapped);
    };

    loadPresets();
    return () => {
      active = false;
    };
  }, []);

  const applyPreset = React.useCallback(
    (prompt: string) => {
      if (isStreaming) return;
      setValue(prompt);
      focusInput();
    },
    [isStreaming, focusInput]
  );

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    if (!value.trim()) return;
    const next = value;
    setValue("");
    focusInput();
    await sendMessage(next);
    focusInput();
  };

  const hasContent = value.trim().length > 0;

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden px-4 py-10 sm:px-6 lg:py-14">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_18%_18%,rgba(255,184,108,0.38),transparent_25%),radial-gradient(circle_at_86%_12%,rgba(139,124,255,0.3),transparent_24%),radial-gradient(circle_at_72%_78%,rgba(65,201,180,0.22),transparent_28%),linear-gradient(140deg,#FFF8EE,#F7F2EA_45%,#EEF2FF)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.26] [background-image:linear-gradient(rgba(39,36,51,0.07)_1px,transparent_1px),linear-gradient(90deg,rgba(39,36,51,0.07)_1px,transparent_1px)] [background-size:42px_42px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-[8%] top-[15%] hidden h-48 w-48 rotate-12 rounded-[3rem] border border-[#FFB86C]/38 bg-white/10 lg:block"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute bottom-[11%] right-[7%] hidden h-32 w-32 rounded-full border border-[#8B7CFF]/36 bg-white/10 lg:block"
      />

      <div className="relative w-full max-w-[1040px]">
        <div className="grid items-end gap-8 lg:grid-cols-[1.04fr_0.96fr]">
          <div className="opacity-0 animate-fade-up" style={{ animationFillMode: "both" }}>
            <div className="inline-flex items-center gap-2 rounded-xl border border-white/60 bg-white/58 px-3 py-2 text-xs font-semibold tracking-[0.18em] text-[#6E5D7B] shadow-[0_14px_34px_rgba(58,43,78,0.08)] backdrop-blur-xl">
              <Orbit className="h-4 w-4 text-[#FF8F70]" />
              LUMINAGRAPH
            </div>
            <h1 className="mt-6 max-w-[680px] text-balance font-display text-[clamp(3rem,7vw,6.6rem)] font-semibold leading-[0.9] tracking-[-0.065em] text-[#251C2D]">
              让灵感像光一样流动。
            </h1>
            <p className="mt-6 max-w-[58ch] text-base leading-7 text-[#5F5268] sm:text-lg">
              LuminaGraph 把检索、推理与表达融进一个轻盈的对话界面，帮你快速归纳资料、推演方案、延展思路，把零散问题整理成清晰答案。
            </p>
          </div>

          <div className="opacity-0 animate-fade-up" style={{ animationDelay: "90ms", animationFillMode: "both" }}>
            <div
              className={cn(
                "relative overflow-hidden rounded-[2rem] border bg-white/62 p-2 shadow-[0_34px_90px_rgba(58,43,78,0.18)] backdrop-blur-xl transition duration-300 ease-[cubic-bezier(0.16,1,0.3,1)]",
                isFocused ? "border-[#FFB86C]/70" : "border-white/62"
              )}
            >
              <div className="rounded-[1.55rem] border border-white/65 bg-[#FFFDF8]/82 p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.9)]">
                <div className="relative">
                  <textarea
                    ref={textareaRef}
                    value={value}
                    onChange={(event) => setValue(event.target.value)}
                    placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "输入你的问题..."}
                    className="max-h-40 min-h-[104px] w-full resize-none border-0 bg-transparent px-1 py-1 text-base leading-7 text-[#251C2D] placeholder:text-[#9A879B] focus:outline-none"
                    rows={3}
                    onFocus={() => setIsFocused(true)}
                    onBlur={() => setIsFocused(false)}
                    onCompositionStart={() => {
                      isComposingRef.current = true;
                    }}
                    onCompositionEnd={() => {
                      isComposingRef.current = false;
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" && !event.shiftKey) {
                        const nativeEvent = event.nativeEvent as KeyboardEvent;
                        if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229) return;
                        event.preventDefault();
                        handleSubmit();
                      }
                    }}
                    aria-label="发送消息"
                  />
                  <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-[16px] bg-gradient-to-b from-[#FFFDF8]/0 to-[#FFFDF8]" />
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-[#F0E6D8] pt-4">
                  <button
                    type="button"
                    onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                    disabled={isStreaming}
                    aria-pressed={deepThinkingEnabled}
                    className={cn(
                      "rounded-xl border px-3 py-2 text-xs font-semibold transition duration-200 active:scale-[0.98]",
                      deepThinkingEnabled
                        ? "border-[#FFB86C]/60 bg-[#FFF0D6] text-[#8B4B2E]"
                        : "border-[#EADFD2] bg-white/58 text-[#6E5D7B] hover:bg-white/80",
                      isStreaming && "cursor-not-allowed opacity-60"
                    )}
                  >
                    <span className="inline-flex items-center gap-2">
                      <Brain className="h-4 w-4" />
                      深度思考
                      {deepThinkingEnabled ? <span className="h-2 w-2 rounded-full bg-[#FF8F70] animate-pulse" /> : null}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={!hasContent && !isStreaming}
                    aria-label={isStreaming ? "停止生成" : "发送消息"}
                    className={cn(
                      "ml-auto inline-flex h-11 min-w-11 items-center justify-center rounded-xl px-4 text-sm font-semibold transition duration-200 ease-[cubic-bezier(0.16,1,0.3,1)] active:scale-[0.98]",
                      isStreaming
                        ? "bg-[#FFE7E0] text-[#B4533A] hover:bg-[#FFD7CB]"
                        : hasContent
                          ? "bg-[#251C2D] text-[#FFF8EE] shadow-[0_14px_28px_rgba(37,28,45,0.22)] hover:bg-[#3A2B4E]"
                          : "cursor-not-allowed bg-[#EFE7DC] text-[#B8A9B5]"
                    )}
                  >
                    {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
                    <span className="ml-2 hidden sm:inline">{isStreaming ? "停止" : "发送"}</span>
                  </button>
                </div>
              </div>
            </div>
            <p className="mt-3 text-center text-xs text-[#7B6B83]">
              <kbd className="rounded-md border border-white/70 bg-white/64 px-1.5 py-0.5 text-[#5C4A66]">Enter</kbd> 发送
              <span className="px-1.5">·</span>
              <kbd className="rounded-md border border-white/70 bg-white/64 px-1.5 py-0.5 text-[#5C4A66]">Shift + Enter</kbd> 换行
              {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
            </p>
          </div>
        </div>

        <div className="mt-10 opacity-0 animate-fade-up" style={{ animationDelay: "170ms", animationFillMode: "both" }}>
          <div className="grid gap-3 lg:grid-cols-3">
            {promptPresets.map((preset) => {
              const Icon = preset.icon;
              return (
                <button
                  key={preset.id ?? preset.title}
                  type="button"
                  onClick={() => applyPreset(preset.prompt)}
                  disabled={isStreaming}
                  className={cn(
                    "group rounded-[1.45rem] border border-white/60 bg-white/56 p-4 text-left shadow-[0_16px_42px_rgba(58,43,78,0.08)] backdrop-blur-xl transition duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] hover:-translate-y-1 hover:border-[#FFB86C]/60 hover:bg-white/78",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-[0.9rem] bg-[#FFF0D6] text-[#B4533A]">
                      <Icon className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-[#251C2D]">{preset.title}</p>
                      <p className="truncate text-xs text-[#6E5D7B]">{preset.description}</p>
                    </div>
                  </div>
                  <div className="mt-4 flex items-center gap-2 text-xs text-[#7B6B83]">
                    <span className="min-w-0 flex-1 truncate">{preset.prompt}</span>
                    <ArrowUpRight className="h-3.5 w-3.5 text-[#FF8F70] transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
