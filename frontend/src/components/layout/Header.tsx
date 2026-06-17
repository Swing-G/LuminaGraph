import * as React from "react";
import { Menu, PanelLeftClose, PanelLeftOpen, ShieldCheck, Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  isSidebarCollapsed: boolean;
  onToggleSidebar: () => void;
}

export function Header({ isSidebarCollapsed, onToggleSidebar }: HeaderProps) {
  const { currentSessionId, sessions, isStreaming } = useChatStore();
  const currentSession = React.useMemo(
    () => sessions.find((session) => session.id === currentSessionId),
    [sessions, currentSessionId]
  );
  const DesktopIcon = isSidebarCollapsed ? PanelLeftOpen : PanelLeftClose;

  return (
    <header className="sticky top-0 z-20 border-b border-white/45 bg-[#FFF9F0]/64 backdrop-blur-2xl">
      <div className="flex h-16 items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label="切换侧边栏"
            className="h-10 w-10 rounded-2xl text-[#5C4A66] transition hover:bg-white/58 lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label={isSidebarCollapsed ? "展开侧边栏" : "隐藏侧边栏"}
            className="hidden h-10 w-10 rounded-2xl text-[#5C4A66] transition hover:bg-white/58 lg:inline-flex"
          >
            <DesktopIcon className="h-5 w-5" />
          </Button>
          <div className="hidden h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FFE0A3] via-[#FF8F70] to-[#8B7CFF] text-[#251C2D] shadow-[0_12px_28px_rgba(255,143,112,0.22)] sm:flex">
            <Sparkles className="h-4 w-4" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold tracking-[-0.02em] text-[#251C2D] sm:text-base">
              {currentSession?.title || "LuminaGraph 对话"}
            </p>
            <p className="mt-0.5 hidden text-xs text-[#7B6B83] sm:block">
              捕捉问题中的微光，整理成清晰可用的答案
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2 rounded-2xl border border-white/60 bg-white/58 px-3 py-2 text-xs font-medium text-[#5C4A66] shadow-[0_14px_32px_rgba(58,43,78,0.08)]">
          <span
            className={cn(
              "h-2 w-2 rounded-full",
              isStreaming ? "animate-pulse-soft bg-[#FF8F70]" : "bg-[#41C9B4]"
            )}
          />
          <ShieldCheck className="hidden h-4 w-4 text-[#41A99B] sm:block" />
          <span>{isStreaming ? "灵感生成中" : "专注会话"}</span>
        </div>
      </div>
    </header>
  );
}
