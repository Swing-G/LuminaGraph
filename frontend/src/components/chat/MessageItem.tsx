import * as React from "react";
import { Brain, ChevronDown } from "lucide-react";

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
import type { Message } from "@/types";

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const isUser = message.role === "user";
  const showFeedback =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    message.id &&
    !message.id.startsWith("assistant-");
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking && message.thinking.trim().length > 0);

  // 从持久化内容中解析 statusLogs（格式：{"statusLogs":[...]}\n真实内容）
  const { statusLogs: parsedLogs, content: cleanContent } = React.useMemo(() => {
    const raw = message.content;
    if (raw.startsWith("{\"statusLogs\":")) {
      const nl = raw.indexOf("\n");
      if (nl > 0) {
        try {
          const parsed = JSON.parse(raw.substring(0, nl));
          return { statusLogs: parsed.statusLogs as string[], content: raw.substring(nl + 1) };
        } catch { /* fall through */ }
      }
    }
    return { statusLogs: message.statusLogs, content: raw };
  }, [message.content, message.statusLogs]);
  // 优先用持久化的，其次用流式过程中实时推送的
  const displayLogs = parsedLogs?.length ? parsedLogs : message.statusLogs;

  const hasContent = cleanContent.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[78%] rounded-[1.35rem] bg-[#21182B] px-4 py-3 text-[15px] leading-7 text-[#FFF8EF] shadow-[0_12px_28px_rgba(31,24,38,0.16)]">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    );
  }

  const thinkingDuration = message.thinkingDuration ? `${message.thinkingDuration}秒` : "";
  return (
    <div className="group flex">
      <div className="min-w-0 flex-1 space-y-4 rounded-[1.35rem] bg-white/58 px-5 py-4 shadow-[0_12px_30px_rgba(31,24,38,0.06)] ring-1 ring-[#E8DDCF] overflow-hidden break-words">
        {isThinking ? (
          <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
        ) : null}
        {!isThinking && hasThinking ? (
          <div className="overflow-hidden rounded-[1.15rem] border border-[#F1D8BD] bg-[#FFF5E8] shadow-[inset_0_1px_0_rgba(255,255,255,0.82)]">
            <button
              type="button"
              onClick={() => setThinkingExpanded((prev) => !prev)}
              className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-[#FFF0D6]"
            >
              <div className="flex flex-1 items-center gap-2">
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#FFF0D6] ring-1 ring-[#F1D8BD]">
                  <Brain className="h-4 w-4 text-[#B4533A]" />
                </div>
                <span className="text-sm font-medium text-[#8B4B2E]">深度思考</span>
                {thinkingDuration ? (
                  <span className="rounded-full bg-[#21182B] px-2 py-0.5 text-xs text-[#FFF8EF]">
                    {thinkingDuration}
                  </span>
                ) : null}
              </div>
              <ChevronDown
                className={cn(
                  "h-4 w-4 text-[#8B4B2E] transition-transform",
                  thinkingExpanded && "rotate-180"
                )}
              />
            </button>
            {thinkingExpanded ? (
              <div className="border-t border-[#F1D8BD] px-4 pb-4">
                <div className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-[#5C4A66]">
                  {message.thinking}
                </div>
              </div>
            ) : null}
          </div>
        ) : null}
        <div className="space-y-2">
          {(displayLogs && displayLogs.length > 0) && (
            <details className="mb-3" open={message.status === "streaming"}>
              <summary className="text-xs text-slate-400 cursor-pointer select-none">
                思考过程 ({displayLogs.length})
              </summary>
              <div className="mt-2 space-y-1 pl-2 border-l-2 border-slate-200 max-h-48 overflow-auto">
                {displayLogs.map((log, i) => (
                  <div key={i} className="text-xs text-slate-500 font-mono">{log}</div>
                ))}
              </div>
            </details>
          )}
          {isWaiting ? (
            <div className="ai-wait" aria-label="思考中">
              <span className="ai-wait-dots" aria-hidden="true">
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
                <span className="ai-wait-dot" />
              </span>
            </div>
          ) : null}
          {hasContent ? <MarkdownRenderer content={cleanContent} /> : null}
          {message.status === "error" ? (
            <p className="text-xs text-rose-500">生成已中断。</p>
          ) : null}
          {showFeedback ? (
            <FeedbackButtons
              messageId={message.id}
              feedback={message.feedback ?? null}
              content={message.content}
              alwaysVisible={Boolean(isLast)}
            />
          ) : null}
        </div>
      </div>
    </div>
  );
});
