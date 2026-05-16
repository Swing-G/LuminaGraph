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
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_18%,oklch(0.9_0.055_205/.85),transparent_24%),radial-gradient(circle_at_88%_10%,oklch(0.93_0.075_76/.72),transparent_25%),linear-gradient(140deg,oklch(0.98_0.005_250),oklch(0.94_0.012_250))]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.34] [background-image:linear-gradient(oklch(0.2_0.02_250/.06)_1px,transparent_1px),linear-gradient(90deg,oklch(0.2_0.02_250/.06)_1px,transparent_1px)] [background-size:42px_42px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute left-[8%] top-[15%] hidden h-48 w-48 rotate-12 border border-[oklch(0.75_0.055_225)]/50 lg:block"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute bottom-[11%] right-[7%] hidden h-32 w-32 rounded-full border border-[oklch(0.78_0.055_78)]/60 lg:block"
      />

      <div className="relative w-full max-w-[1040px]">
        <div className="grid items-end gap-8 lg:grid-cols-[1.04fr_0.96fr]">
          <div className="opacity-0 animate-fade-up" style={{ animationFillMode: "both" }}>
            <div className="inline-flex items-center gap-2 rounded-xl border border-[oklch(0.84_0.02_225)] bg-[oklch(0.99_0.004_250)]/70 px-3 py-2 text-xs font-semibold tracking-[0.14em] text-[oklch(0.48_0.13_245)] shadow-[0_12px_32px_rgba(31,41,55,0.06)] backdrop-blur-xl">
              <Orbit className="h-4 w-4" />
              KNOWLEDGE ORBIT
            </div>
            <h1 className="mt-6 max-w-[680px] text-balance font-display text-[clamp(3rem,7vw,6.6rem)] font-semibold leading-[0.9] tracking-[-0.065em] text-[oklch(0.2_0.02_250)]">
              让知识先抵达，再回答。
            </h1>
            <p className="mt-6 max-w-[58ch] text-base leading-7 text-[oklch(0.43_0.03_250)] sm:text-lg">
              Ragent 将检索、推理与表达收束到一次对话里。适合制度问答、资料总结、业务知识追问和复杂问题拆解。
            </p>
          </div>

          <div className="opacity-0 animate-fade-up" style={{ animationDelay: "90ms", animationFillMode: "both" }}>
            <div
              className={cn(
                "relative overflow-hidden rounded-[2rem] border bg-[oklch(0.995_0.004_250)]/82 p-2 shadow-[0_30px_80px_rgba(31,41,55,0.16)] backdrop-blur-xl transition duration-300 ease-[cubic-bezier(0.16,1,0.3,1)]",
                isFocused ? "border-[oklch(0.72_0.08_245)]" : "border-[oklch(0.88_0.012_250)]"
              )}
            >
              <div className="rounded-[1.55rem] border border-[oklch(0.9_0.01_250)] bg-[oklch(0.99_0.004_250)] p-4 shadow-[inset_0_1px_0_oklch(1_0_0/.8)]">
                <div className="relative">
                  <textarea
                    ref={textareaRef}
                    value={value}
                    onChange={(event) => setValue(event.target.value)}
                    placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "输入你的问题..."}
                    className="max-h-40 min-h-[104px] w-full resize-none border-0 bg-transparent px-1 py-1 text-base leading-7 text-[oklch(0.24_0.02_250)] placeholder:text-[oklch(0.62_0.025_250)] focus:outline-none"
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
                  <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-[16px] bg-gradient-to-b from-[oklch(0.99_0.004_250)]/0 to-[oklch(0.99_0.004_250)]" />
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-3 border-t border-[oklch(0.9_0.01_250)] pt-4">
                  <button
                    type="button"
                    onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                    disabled={isStreaming}
                    aria-pressed={deepThinkingEnabled}
                    className={cn(
                      "rounded-xl border px-3 py-2 text-xs font-semibold transition duration-200 active:scale-[0.98]",
                      deepThinkingEnabled
                        ? "border-[oklch(0.76_0.065_245)] bg-[oklch(0.92_0.04_225)] text-[oklch(0.42_0.13_245)]"
                        : "border-[oklch(0.88_0.012_250)] bg-[oklch(0.965_0.007_250)] text-[oklch(0.44_0.025_250)] hover:bg-[oklch(0.94_0.01_250)]",
                      isStreaming && "cursor-not-allowed opacity-60"
                    )}
                  >
                    <span className="inline-flex items-center gap-2">
                      <Brain className="h-4 w-4" />
                      深度思考
                      {deepThinkingEnabled ? <span className="h-2 w-2 rounded-full bg-[oklch(0.54_0.14_245)] animate-pulse" /> : null}
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
                        ? "bg-[oklch(0.93_0.05_25)] text-[oklch(0.58_0.18_25)] hover:bg-[oklch(0.89_0.07_25)]"
                        : hasContent
                          ? "bg-[oklch(0.25_0.045_250)] text-[oklch(0.98_0.004_250)] hover:bg-[oklch(0.31_0.05_250)]"
                          : "cursor-not-allowed bg-[oklch(0.92_0.01_250)] text-[oklch(0.68_0.02_250)]"
                    )}
                  >
                    {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
                    <span className="ml-2 hidden sm:inline">{isStreaming ? "停止" : "发送"}</span>
                  </button>
                </div>
              </div>
            </div>
            <p className="mt-3 text-center text-xs text-[oklch(0.56_0.025_250)]">
              <kbd className="rounded-md border border-[oklch(0.88_0.012_250)] bg-[oklch(0.99_0.004_250)] px-1.5 py-0.5 text-[oklch(0.42_0.03_250)]">Enter</kbd> 发送
              <span className="px-1.5">·</span>
              <kbd className="rounded-md border border-[oklch(0.88_0.012_250)] bg-[oklch(0.99_0.004_250)] px-1.5 py-0.5 text-[oklch(0.42_0.03_250)]">Shift + Enter</kbd> 换行
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
                    "group rounded-[1.45rem] border border-[oklch(0.88_0.012_250)] bg-[oklch(0.99_0.004_250)]/72 p-4 text-left shadow-[0_14px_36px_rgba(31,41,55,0.06)] backdrop-blur-xl transition duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] hover:-translate-y-1 hover:border-[oklch(0.76_0.065_245)] hover:bg-[oklch(0.995_0.004_250)]",
                    isStreaming && "cursor-not-allowed opacity-60"
                  )}
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-[0.9rem] bg-[oklch(0.92_0.035_225)] text-[oklch(0.42_0.13_245)]">
                      <Icon className="h-4 w-4" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-[oklch(0.24_0.02_250)]">{preset.title}</p>
                      <p className="truncate text-xs text-[oklch(0.5_0.025_250)]">{preset.description}</p>
                    </div>
                  </div>
                  <div className="mt-4 flex items-center gap-2 text-xs text-[oklch(0.56_0.025_250)]">
                    <span className="min-w-0 flex-1 truncate">{preset.prompt}</span>
                    <ArrowUpRight className="h-3.5 w-3.5 text-[oklch(0.65_0.06_245)] transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
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
