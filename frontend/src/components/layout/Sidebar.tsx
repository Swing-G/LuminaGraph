import * as React from "react";
import { differenceInCalendarDays, isValid } from "date-fns";
import { LogOut, MessageSquare, MoreHorizontal, Pencil, Plus, Search, Sparkles, Trash2 } from "lucide-react";
import { useNavigate } from "react-router-dom";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Loading } from "@/components/common/Loading";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const {
    sessions,
    currentSessionId,
    isLoading,
    sessionsLoaded,
    createSession,
    deleteSession,
    renameSession,
    selectSession,
    fetchSessions
  } = useChatStore();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [query, setQuery] = React.useState("");
  const [renamingId, setRenamingId] = React.useState<string | null>(null);
  const [renameValue, setRenameValue] = React.useState("");
  const [deleteTarget, setDeleteTarget] = React.useState<{
    id: string;
    title: string;
  } | null>(null);
  const [avatarFailed, setAvatarFailed] = React.useState(false);
  const renameInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (sessions.length === 0) {
      fetchSessions().catch(() => null);
    }
  }, [fetchSessions, sessions.length]);

  const filteredSessions = React.useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return sessions;
    return sessions.filter((session) => {
      const title = (session.title || "新对话").toLowerCase();
      return title.includes(keyword) || session.id.toLowerCase().includes(keyword);
    });
  }, [query, sessions]);

  const groupedSessions = React.useMemo(() => {
    const now = new Date();
    const groups = new Map<string, typeof filteredSessions>();
    const order: string[] = [];

    const resolveLabel = (value?: string) => {
      const parsed = value ? new Date(value) : now;
      const date = isValid(parsed) ? parsed : now;
      const diff = Math.max(0, differenceInCalendarDays(now, date));
      if (diff === 0) return "今天";
      if (diff <= 7) return "7天内";
      if (diff <= 30) return "30天内";
      return "更早";
    };

    filteredSessions.forEach((session) => {
      const label = resolveLabel(session.lastTime);
      if (!groups.has(label)) {
        groups.set(label, []);
        order.push(label);
      }
      groups.get(label)?.push(session);
    });

    return order.map((label) => ({
      label,
      items: groups.get(label) || []
    }));
  }, [filteredSessions]);

  React.useEffect(() => {
    if (renamingId) {
      renameInputRef.current?.focus();
      renameInputRef.current?.select();
    }
  }, [renamingId]);

  React.useEffect(() => {
    setAvatarFailed(false);
  }, [user?.avatar, user?.userId]);

  const avatarUrl = user?.avatar?.trim();
  const showAvatar = Boolean(avatarUrl) && !avatarFailed;
  const avatarFallback = (user?.username || user?.userId || "用户").slice(0, 1).toUpperCase();
  const sessionTitleFont =
    "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"PingFang SC\", \"Hiragino Sans GB\", \"Microsoft YaHei\", \"Helvetica Neue\", Arial, sans-serif";

  const startRename = (id: string, title: string) => {
    setRenamingId(id);
    setRenameValue(title || "新对话");
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameValue("");
  };

  const commitRename = async () => {
    if (!renamingId) return;
    const nextTitle = renameValue.trim();
    if (!nextTitle) {
      cancelRename();
      return;
    }
    const currentTitle = sessions.find((session) => session.id === renamingId)?.title || "新对话";
    if (nextTitle === currentTitle) {
      cancelRename();
      return;
    }
    await renameSession(renamingId, nextTitle);
    cancelRename();
  };

  return (
    <>
      <div
        className={cn(
          "fixed inset-0 z-30 bg-slate-900/30 backdrop-blur-sm transition-opacity lg:hidden",
          isOpen ? "opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={onClose}
      />
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 flex h-screen w-[302px] flex-shrink-0 flex-col border-r border-[#E7DCCA] bg-[#2A2230] p-3 text-[#FFF8EF] shadow-[24px_0_70px_rgba(31,24,38,0.18)] transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] lg:static lg:h-screen lg:translate-x-0 lg:transition-none",
          isOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="border-b border-white/10 pb-3">
          <div className="relative overflow-hidden rounded-[1.35rem] border border-white/12 bg-white/[0.07] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.14)]">
            <span aria-hidden="true" className="absolute -right-8 -top-8 h-20 w-20 rounded-full bg-[#FFB86C]/35 blur-2xl" />
            <span aria-hidden="true" className="absolute -bottom-10 left-6 h-24 w-24 rounded-full bg-[#8B7CFF]/35 blur-2xl" />
            <div className="relative flex items-center gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FFE0A3] via-[#FF8F70] to-[#8B7CFF] text-[#251C2D] shadow-[0_12px_28px_rgba(255,143,112,0.28)]">
                <Sparkles className="h-5 w-5" />
              </div>
              <div style={{ fontFamily: sessionTitleFont }}>
                <p className="text-lg font-semibold tracking-[-0.03em] text-white">LuminaGraph</p>
                <p className="text-xs text-white/52">让灵感在对话里成形</p>
              </div>
            </div>
          </div>
        </div>
        <div className="py-3 space-y-4">
          <div className="relative overflow-hidden rounded-[1.35rem] border border-white/12 bg-gradient-to-br from-white/[0.16] via-white/[0.08] to-[#FFD6A5]/[0.13] p-3 shadow-[0_18px_42px_rgba(0,0,0,0.18)]">
            <span
              aria-hidden="true"
              className="absolute -right-10 -top-10 h-24 w-24 rounded-full bg-[#8B7CFF]/45 blur-2xl"
            />
            <span
              aria-hidden="true"
              className="absolute -left-12 -bottom-10 h-28 w-28 rounded-full bg-[#FFB86C]/45 blur-2xl"
            />
            <div className="relative">
              <div className="flex items-center justify-between px-1">
                <span className="text-[11px] font-semibold tracking-[0.16em] text-white/45">NEW CHAT</span>
                <span className="rounded-full bg-white/12 px-2 py-0.5 text-[10px] font-semibold text-[#FFE0A3] ring-1 ring-white/10">
                  LuminaGraph 模式
                </span>
              </div>
              <button
                type="button"
                className="mt-2 flex w-full items-center gap-3 rounded-2xl bg-white/[0.12] px-4 py-3 text-left shadow-[inset_0_1px_0_rgba(255,255,255,0.16)] ring-1 ring-white/10 transition-colors duration-200 hover:bg-white/[0.18]"
                onClick={() => {
                  createSession().catch(() => null);
                  navigate("/chat");
                  onClose();
                }}
              >
                <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FFE0A3] via-[#FF8F70] to-[#8B7CFF] text-[#251C2D] shadow-[0_8px_20px_rgba(255,143,112,0.25)]">
                  <Plus className="h-4 w-4" />
                </span>
                <span className="flex-1">
                  <span className="block text-sm font-semibold text-white">开启一束新对话</span>
                  <span className="block text-xs text-white/48">捕捉想法，继续创作</span>
                </span>
              </button>
            </div>
          </div>
          <div className="rounded-[1.35rem] border border-white/10 bg-white/[0.08] p-3 shadow-[inset_0_1px_0_rgba(255,255,255,0.1)]">
            <div className="flex items-center justify-between px-1">
              <span className="text-[11px] font-semibold tracking-[0.16em] text-white/45">SEARCH</span>
              <span className="text-[10px] text-white/28">Ctrl / Cmd + K</span>
            </div>
            <div className="mt-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-white/38" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="搜索对话..."
                  className="h-10 w-full rounded-xl border border-white/10 bg-black/10 pl-9 pr-3 text-sm text-white placeholder:text-white/32 transition-colors focus:border-[#FFE0A3]/60 focus:bg-black/15 focus:outline-none"
                />
              </div>
            </div>
          </div>
        </div>
        <div className="relative flex-1 min-h-0">
          <div className="h-full overflow-y-auto sidebar-scroll">
            {sessions.length === 0 && (!sessionsLoaded || isLoading) ? (
              <div
                className="flex h-full items-center justify-center text-white/45"
                style={{ fontFamily: sessionTitleFont }}
              >
                <Loading label="加载会话中" />
              </div>
            ) : filteredSessions.length === 0 ? (
              <div
                className="flex h-full flex-col items-center justify-center text-white/36"
                style={{ fontFamily: sessionTitleFont }}
              >
                <MessageSquare className="h-16 w-16" />
                <p className="mt-2 text-[14px]">暂无对话记录</p>
              </div>
            ) : (
              <div>
                {groupedSessions.map((group, index) => (
                  <div key={group.label} className={cn("flex flex-col", index === 0 ? "mt-0" : "mt-4")}>
                    <p className="mb-1.5 pl-3 text-[12px] font-normal leading-[18px] text-white/36">
                      {group.label}
                    </p>
                    {group.items.map((session) => (
                      <div
                        key={session.id}
                        className={cn(
                          "group my-[1px] flex min-h-[40px] cursor-pointer items-center justify-between gap-2 rounded-xl px-3 py-2 text-[14px] leading-[22px] transition-all duration-200",
                          currentSessionId === session.id
                            ? "bg-white/[0.16] text-[#FFE0A3] shadow-[inset_0_1px_0_rgba(255,255,255,0.12)] ring-1 ring-white/10"
                            : "text-white/72 hover:bg-white/[0.1] hover:text-white"
                        )}
                        role="button"
                        tabIndex={0}
                        onClick={() => {
                          if (renamingId === session.id) return;
                          if (renamingId) {
                            cancelRename();
                          }
                          selectSession(session.id).catch(() => null);
                          navigate(`/chat/${session.id}`);
                          onClose();
                        }}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            selectSession(session.id).catch(() => null);
                            navigate(`/chat/${session.id}`);
                            onClose();
                          }
                        }}
                      >
                        {renamingId === session.id ? (
                          <input
                            ref={renameInputRef}
                            value={renameValue}
                            onChange={(event) => setRenameValue(event.target.value)}
                            onClick={(event) => event.stopPropagation()}
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                commitRename().catch(() => null);
                              }
                              if (event.key === "Escape") {
                                event.preventDefault();
                                cancelRename();
                              }
                            }}
                            onBlur={() => {
                              commitRename().catch(() => null);
                            }}
                            className="h-6 flex-1 rounded-md border border-white/15 bg-black/20 px-2 text-[14px] leading-[22px] text-white focus:border-[#FFE0A3]/70 focus:outline-none"
                          />
                        ) : (
                          <span className="min-w-0 flex-1 truncate font-normal">
                            {session.title || "新对话"}
                          </span>
                        )}
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              className={cn(
                                "flex h-6 w-6 items-center justify-center rounded text-white/55 transition-opacity duration-150 hover:bg-white/10 hover:text-white",
                                currentSessionId === session.id
                                  ? "pointer-events-auto opacity-100 text-[#FFE0A3]"
                                  : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100"
                              )}
                              onClick={(event) => event.stopPropagation()}
                              aria-label="会话操作"
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent
                            align="start"
                            className="min-w-[120px] rounded-lg border-0 bg-white p-0 py-1 shadow-[0_4px_16px_rgba(0,0,0,0.12)]"
                          >
                            <DropdownMenuItem
                              onClick={(event) => {
                                event.stopPropagation();
                                startRename(session.id, session.title || "新对话");
                              }}
                              className="px-4 py-2 text-[14px] text-[#333333] focus:bg-[#F5F5F5] focus:text-[#333333] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#333333]"
                            >
                              <Pencil className="mr-2 h-4 w-4" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={(event) => {
                                event.stopPropagation();
                                setDeleteTarget({
                                  id: session.id,
                                  title: session.title || "新对话"
                                });
                              }}
                              className="px-4 py-2 text-[14px] text-[#FF4D4F] focus:bg-[#F5F5F5] focus:text-[#FF4D4F] data-[highlighted]:bg-[#F5F5F5] data-[highlighted]:text-[#FF4D4F]"
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </div>
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-5 bg-gradient-to-b from-transparent to-[#251C2D]"
          />
        </div>
        <div className="mt-auto pt-3">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.07] p-2 text-left transition-colors hover:bg-white/[0.12] data-[state=open]:bg-white/[0.14]"
                aria-label="用户菜单"
              >
                <div className="flex h-8 w-8 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-[#FFE0A3] to-[#FF8F70] text-[#251C2D]">
                  {showAvatar ? (
                    <img
                      src={avatarUrl}
                      alt={user?.username || user?.userId || "用户"}
                      className="h-full w-full object-cover"
                      onError={() => setAvatarFailed(true)}
                    />
                  ) : (
                    <span className="text-sm font-medium">{avatarFallback}</span>
                  )}
                </div>
                <span className="flex-1 truncate text-sm font-medium text-white/86">
                  {(() => {
                    const fallback = user?.username || user?.userId || "用户";
                    return /^\d+$/.test(fallback) ? "用户" : fallback;
                  })()}
                </span>
                <MoreHorizontal className="h-4 w-4 text-white/35" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" side="top" sideOffset={8} className="w-48">
              <DropdownMenuItem onClick={() => logout()} className="text-rose-600 focus:text-rose-600">
                <LogOut className="mr-2 h-4 w-4" />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>
      <AlertDialog open={Boolean(deleteTarget)} onOpenChange={(open) => {
        if (!open) {
          setDeleteTarget(null);
        }
      }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除该会话？</AlertDialogTitle>
            <AlertDialogDescription>
              [{deleteTarget?.title || "该会话"}] 将被永久删除，无法恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!deleteTarget) return;
                const target = deleteTarget;
                const isCurrent = currentSessionId === target.id;
                setDeleteTarget(null);
                deleteSession(target.id)
                  .then(() => {
                    if (isCurrent) {
                      navigate("/chat");
                    }
                  })
                  .catch(() => null);
              }}
            >
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
