import * as React from "react";
import { Brain, Lightbulb, Send, Square } from "lucide-react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { getAgentWorkflowChatOptions, type AgentWorkflowChatOption, type AgentWorkflowChatPromptPreset } from "@/services/workflowService";
import { useChatStore } from "@/stores/chatStore";

type ChatModeKey = "RAG" | "WORKFLOW";

type PromptPreset = {
  title: string;
  description: string;
  prompt: string;
};

const FLOW_PROMPT_PRESETS: Record<string, PromptPreset[]> = {
  ticket_triage_chat: [
    {
      title: "分析续费失败工单",
      description: "查询账号、订单和工单事实并给出处理建议",
      prompt:
        "帮我分析工单 T20260520001，客户说续费失败，账号 A10001，订单 PAY10001，看看当前状态并给出处理建议。"
    },
    {
      title: "生成客服处理回复",
      description: "判断原因并生成可发送给客户的话术",
      prompt:
        "客户反馈账号 A10001 无法续费，订单号 PAY10001，请查询账号和支付状态，判断原因并生成客服回复。"
    }
  ],
  ticket_quick_triage_chat: [
    {
      title: "客诉工单初筛",
      description: "判断问题类型、风险等级和责任团队",
      prompt:
        "客户反馈付款后权益没有到账，情绪比较激动。请帮我做工单初筛，判断问题类型、风险等级和应该分派给哪个团队。"
    },
    {
      title: "发票问题分派",
      description: "判断财务类工单优先级和处理建议",
      prompt:
        "客户说发票开错了，要求今天内重开，否则会投诉。请帮我初筛这个工单，给出分类、风险等级、负责团队和客服回复建议。"
    }
  ],
  customer_success_followup_chat: [
    {
      title: "续费失败回访建议",
      description: "生成风险判断、回访重点、话术和下一步动作",
      prompt:
        "客户 A10001 续费失败，订单 PAY10001，之前有工单 T20260520001。请帮我生成客户成功回访建议，包括风险判断、回访重点、建议话术和下一步动作。"
    },
    {
      title: "VIP 客户挽回方案",
      description: "面向客户成功团队生成跟进动作",
      prompt:
        "VIP 客户账号 A10001 最近反馈续费失败并多次催促，请结合账号和订单 PAY10001 给出回访方案。"
    }
  ],
  multi_agent_chat: [
    {
      title: "排查权限异常",
      description: "多Agent并行分析账号权限问题",
      prompt: "我是深圳矩阵科技有限公司（A10003），今天我们研发团队约30人突然无法访问 DevOps 模块，提示403无权限。账号明明还没过期，帮我查一下怎么回事。"
    },
    {
      title: "安全事件应急",
      description: "多Agent紧急分析安全入侵事件",
      prompt: "紧急！我是楚天信息安全（A10008），凌晨发现我们账号有大量异常 API 调用，怀疑被黑客入侵了。系统好像自动冻结了账号，请帮我确认当前状态和处理进度。"
    },
    {
      title: "试用升级咨询",
      description: "多Agent分析试用情况并推荐方案",
      prompt: "我是南京星辰网络科技（A10007），我们试用体验版快到期了，团队用下来觉得不错想升级。请问当前试用什么时候到期？升级到团队版和企业版分别多少钱？有没有优惠？"
    },
    {
      title: "账单争议处理",
      description: "多Agent分析扣费异常",
      prompt: "我是成都天域云计算的（A10005），我们的账号被暂停了但账单显示这个月还扣了2999元。服务都没有凭什么还扣费？我要一个解释和退款方案。"
    },
    {
      title: "SLA超时投诉",
      description: "多Agent处理VIP客户投诉",
      prompt: "我是厦门海丝金融（A10011），我3天前提交了一个工单到现在没人回复！我们是VIP客户，SLA承诺2小时响应，现在严重超时了。我需要赔偿和升级处理。"
    }
  ]
};

const MODE_PROMPT_PRESETS: Record<"RAG", PromptPreset[]> = {
  RAG: [
    {
      title: "查询公司制度",
      description: "从私域知识库检索制度、流程和规范",
      prompt: "请从知识库中查询公司请假制度，并总结申请流程、审批要求和注意事项。"
    },
    {
      title: "查询 IT 支持说明",
      description: "面向内部员工的问题检索",
      prompt: "请查询 IT 支持相关文档，说明账号无法登录时应该如何排查和提交工单。"
    }
  ]
};

function normalizePromptPresets(value: AgentWorkflowChatOption["promptPresets"]): PromptPreset[] {
  if (!value) return [];
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item): item is AgentWorkflowChatPromptPreset => Boolean(item?.title && item?.prompt))
      .map((item) => ({
        title: item.title,
        description: item.description || "自定义对话模板",
        prompt: item.prompt
      }));
  } catch {
    return [];
  }
}

function getPromptPresets(chatMode: ChatModeKey, workflowType: string, workflowOptions: AgentWorkflowChatOption[]) {
  if (chatMode === "WORKFLOW") {
    const optionPresets = normalizePromptPresets(workflowOptions.find((option) => option.optionKey === workflowType)?.promptPresets);
    return optionPresets.length > 0 ? optionPresets : FLOW_PROMPT_PRESETS[workflowType] || [];
  }
  return MODE_PROMPT_PRESETS.RAG;
}

function getInputPlaceholder(
  chatMode: ChatModeKey,
  workflowType: string,
  deepThinkingEnabled: boolean
) {
  const prefix = deepThinkingEnabled ? "输入需要深度分析的问题" : "输入你的问题";
  const modeHint =
    chatMode === "WORKFLOW"
      ? workflowType === "ticket_quick_triage_chat"
        ? "工单初筛"
        : workflowType === "customer_success_followup_chat"
          ? "客户回访"
          : "工单分析"
      : "知识库问答";
  return `${prefix}，输入 / 选择${modeHint}预设提示词`;
}

export function ChatInput() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const [activePresetIndex, setActivePresetIndex] = React.useState(0);
  const isComposingRef = React.useRef(false);
  const [workflowOptions, setWorkflowOptions] = React.useState<AgentWorkflowChatOption[]>([]);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled,
    chatMode,
    setChatMode,
    workflowType,
    setWorkflowType,
    inputFocusKey
  } = useChatStore();
  const selectedModeValue = chatMode === "RAG" ? "RAG" : workflowType;

  React.useEffect(() => {
    getAgentWorkflowChatOptions(true)
      .then((items) => {
        setWorkflowOptions(items);
      })
      .catch(() => null);
  }, []);

  function handleModeSelect(value: string) {
    if (value === "RAG") {
      setChatMode("RAG");
      return;
    }
    setChatMode("WORKFLOW");
    setWorkflowType(value);
  }

  const promptPresets = React.useMemo(
    () => getPromptPresets(chatMode as ChatModeKey, workflowType, workflowOptions),
    [chatMode, workflowType, workflowOptions]
  );
  const showPromptMenu = isFocused && value.trimStart().startsWith("/") && promptPresets.length > 0;

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

  const applyPreset = React.useCallback(
    (preset: PromptPreset) => {
      setValue(preset.prompt);
      setActivePresetIndex(0);
      requestAnimationFrame(() => {
        adjustHeight();
        focusInput();
      });
    },
    [adjustHeight, focusInput]
  );

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    if (!inputFocusKey) return;
    focusInput();
  }, [inputFocusKey, focusInput]);

  React.useEffect(() => {
    setActivePresetIndex(0);
  }, [chatMode, workflowType, value]);

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    if (!value.trim() || value.trim() === "/") return;
    const next = value;
    setValue("");
    focusInput();
    await sendMessage(next);
    focusInput();
  };

  const hasContent = value.trim().length > 0 && value.trim() !== "/";

  return (
    <div className="space-y-4">
      <div
        className={cn(
          "relative flex flex-col rounded-[1.35rem] border bg-white/72 px-4 pt-3 pb-2 shadow-[0_18px_46px_rgba(58,43,78,0.1)] backdrop-blur-xl transition-all duration-200",
          isFocused
            ? "border-[#FFB86C]/70 shadow-[0_20px_54px_rgba(255,143,112,0.16)]"
            : "border-white/70 hover:border-[#FFB86C]/45"
        )}
      >
        <div className="relative">
          <Textarea
            ref={textareaRef}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder={getInputPlaceholder(
              chatMode as ChatModeKey,
              workflowType,
              deepThinkingEnabled
            )}
            className="max-h-40 min-h-[44px] w-full resize-none border-0 bg-transparent px-2 pt-2 pb-2 pr-2 text-[15px] text-[#251C2D] shadow-none placeholder:text-[#9A879B] focus-visible:ring-0"
            rows={1}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            onCompositionStart={() => {
              isComposingRef.current = true;
            }}
            onCompositionEnd={() => {
              isComposingRef.current = false;
            }}
            onKeyDown={(event) => {
              if (showPromptMenu) {
                if (event.key === "ArrowDown") {
                  event.preventDefault();
                  setActivePresetIndex((index) => (index + 1) % promptPresets.length);
                  return;
                }
                if (event.key === "ArrowUp") {
                  event.preventDefault();
                  setActivePresetIndex((index) =>
                    index === 0 ? promptPresets.length - 1 : index - 1
                  );
                  return;
                }
                if (event.key === "Tab") {
                  event.preventDefault();
                  applyPreset(promptPresets[activePresetIndex]);
                  return;
                }
              }
              if (event.key === "Enter" && !event.shiftKey) {
                const nativeEvent = event.nativeEvent as KeyboardEvent;
                if (
                  nativeEvent.isComposing ||
                  isComposingRef.current ||
                  nativeEvent.keyCode === 229
                ) {
                  return;
                }
                if (showPromptMenu) {
                  event.preventDefault();
                  applyPreset(promptPresets[activePresetIndex]);
                  return;
                }
                event.preventDefault();
                handleSubmit();
              }
            }}
            aria-label="聊天输入框"
          />
          {showPromptMenu ? (
            <div className="absolute bottom-[calc(100%+10px)] left-0 right-0 z-20 overflow-hidden rounded-2xl border border-[#EADFD2] bg-[#FFFDF8] shadow-[0_22px_54px_rgba(58,43,78,0.16)]">
              <div className="border-b border-[#F0E4D7] px-4 py-3">
                <p className="text-xs font-medium text-[#5C4A66]">选择一个预设提示词</p>
                <p className="mt-1 text-[11px] text-[#9A879B]">
                  按 Enter 或 Tab 填入，也可以直接点击
                </p>
              </div>
              <div className="max-h-72 overflow-auto p-2">
                {promptPresets.map((preset, index) => (
                  <button
                    key={`${preset.title}-${index}`}
                    type="button"
                    onMouseDown={(event) => {
                      event.preventDefault();
                      applyPreset(preset);
                    }}
                    onMouseEnter={() => setActivePresetIndex(index)}
                    className={cn(
                      "w-full rounded-xl px-3 py-3 text-left transition-all duration-150",
                      index === activePresetIndex
                        ? "bg-[#FFF0D6] text-[#251C2D]"
                        : "text-[#5C4A66] hover:bg-[#F8EFE4]"
                    )}
                  >
                    <span className="block text-sm font-semibold">{preset.title}</span>
                    <span className="mt-1 block text-xs text-[#7B6B83]">{preset.description}</span>
                    <span className="mt-2 line-clamp-2 block text-xs leading-5 text-[#9A5A44]">
                      {preset.prompt}
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : null}
          <div className="pointer-events-none absolute bottom-0 left-0 right-0 h-[10px] bg-gradient-to-b from-white/0 via-white/40 to-white/90" />
        </div>
        <div className="relative mt-2 flex flex-wrap items-center gap-2">
          <Select value={selectedModeValue} onValueChange={handleModeSelect} disabled={isStreaming}>
            <SelectTrigger className="h-8 w-[210px] rounded-xl border-[#EADFD2] bg-[#FFF8EE]/72 text-xs text-[#5C4A66] shadow-none">
              <SelectValue placeholder="选择模式" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="RAG">私域问答-RAG</SelectItem>
              {workflowOptions.map((option) => (
                <SelectItem key={option.id} value={option.optionKey}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <button
            type="button"
            onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
            disabled={isStreaming}
            aria-pressed={deepThinkingEnabled}
            className={cn(
              "rounded-lg border px-3 py-1.5 text-xs font-medium transition-all",
              deepThinkingEnabled
                ? "border-[#FFB86C]/60 bg-[#FFF0D6] text-[#8B4B2E]"
                : "border-transparent bg-[#F4EADF] text-[#7B6B83] hover:bg-[#EEE0D2]",
              isStreaming && "cursor-not-allowed opacity-60"
            )}
          >
            <span className="inline-flex items-center gap-2">
              <Brain className={cn("h-3.5 w-3.5", deepThinkingEnabled && "text-[#FF8F70]")} />
              深度思考
              {deepThinkingEnabled ? (
                <span className="h-2 w-2 rounded-full bg-[#FF8F70] animate-pulse" />
              ) : null}
            </span>
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!hasContent && !isStreaming}
            aria-label={isStreaming ? "停止生成" : "发送消息"}
            className={cn(
              "ml-auto rounded-full p-2.5 transition-all duration-200",
              isStreaming
                ? "bg-[#FEE2E2] text-[#EF4444] hover:bg-[#FECACA]"
                : hasContent
                  ? "bg-[#251C2D] text-[#FFF8EE] shadow-[0_10px_24px_rgba(37,28,45,0.2)] hover:bg-[#3A2B4E]"
                  : "cursor-not-allowed bg-[#EFE7DC] text-[#B8A9B5]"
            )}
          >
            {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
          </button>
        </div>
      </div>
      {deepThinkingEnabled ? (
        <p className="text-xs text-[#B4533A]">
          <span className="inline-flex items-center gap-1.5">
            <Lightbulb className="h-3.5 w-3.5" />
            深度思考模式已开启，AI将进行更深入的分析推理
          </span>
        </p>
      ) : null}
      <p className="text-center text-xs text-[#7B6B83]">
        <kbd className="rounded bg-white/64 px-1.5 py-0.5 text-[#5C4A66]">/</kbd> 选择预设提示词
        <span className="px-1.5">·</span>
        <kbd className="rounded bg-white/64 px-1.5 py-0.5 text-[#5C4A66]">Enter</kbd> 发送
        <span className="px-1.5">·</span>
        <kbd className="rounded bg-white/64 px-1.5 py-0.5 text-[#5C4A66]">Shift + Enter</kbd> 换行
        {isStreaming ? <span className="ml-2 animate-pulse-soft">生成中...</span> : null}
      </p>
    </div>
  );
}
