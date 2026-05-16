import * as React from "react";
import { Menu, Network, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const { currentSessionId, sessions, isStreaming } = useChatStore();
  const currentSession = React.useMemo(
    () => sessions.find((session) => session.id === currentSessionId),
    [sessions, currentSessionId]
  );

  return (
    <header className="sticky top-0 z-20 border-b border-[oklch(0.88_0.012_250)]/70 bg-[oklch(0.975_0.006_250)]/88 backdrop-blur-xl">
      <div className="flex h-16 items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label="切换侧边栏"
            className="h-10 w-10 rounded-2xl text-[oklch(0.42_0.035_250)] transition hover:bg-[oklch(0.93_0.014_250)] lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <div className="hidden h-9 w-9 items-center justify-center rounded-2xl border border-[oklch(0.88_0.012_250)] bg-[oklch(0.99_0.004_250)] text-[oklch(0.48_0.13_245)] shadow-[0_10px_28px_rgba(31,41,55,0.06)] sm:flex">
            <Network className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold tracking-[-0.01em] text-[oklch(0.22_0.018_250)] sm:text-base">
              {currentSession?.title || "新的知识对话"}
            </p>
            <p className="mt-0.5 hidden text-xs text-[oklch(0.52_0.025_250)] sm:block">
              Ragent 会基于可检索知识给出可追溯回答
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2 rounded-2xl border border-[oklch(0.88_0.012_250)] bg-[oklch(0.99_0.004_250)] px-3 py-2 text-xs font-medium text-[oklch(0.42_0.035_250)] shadow-[0_10px_28px_rgba(31,41,55,0.05)]">
          <span
            className={
              isStreaming
                ? "h-2 w-2 rounded-full bg-[oklch(0.7_0.15_155)] animate-pulse-soft"
                : "h-2 w-2 rounded-full bg-[oklch(0.65_0.08_155)]"
            }
          />
          <ShieldCheck className="hidden h-4 w-4 text-[oklch(0.54_0.12_155)] sm:block" />
          <span>{isStreaming ? "生成中" : "安全会话"}</span>
        </div>
      </div>
    </header>
  );
}
