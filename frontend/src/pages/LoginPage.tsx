import * as React from "react";
import { ArrowRight, Eye, EyeOff, Lock, Sparkles, User } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/authStore";

type AuthMode = "login" | "register";

export function LoginPage() {
  const navigate = useNavigate();
  const { login, register, isLoading } = useAuthStore();
  const [mode, setMode] = React.useState<AuthMode>("login");
  const [showPassword, setShowPassword] = React.useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({ username: "", password: "", confirmPassword: "" });
  const [error, setError] = React.useState<string | null>(null);

  const isRegister = mode === "register";

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError(null);
    setShowPassword(false);
    setShowConfirmPassword(false);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    const username = form.username.trim();
    const password = form.password.trim();
    const confirmPassword = form.confirmPassword.trim();

    if (!username || !password) {
      setError("请输入用户名和密码。");
      return;
    }
    if (isRegister) {
      if (username.length < 3 || username.length > 32) {
        setError("用户名长度需为 3-32 位。");
        return;
      }
      if (password.length < 6 || password.length > 64) {
        setError("密码长度需为 6-64 位。");
        return;
      }
      if (password !== confirmPassword) {
        setError("两次输入的密码不一致。");
        return;
      }
    }

    try {
      if (isRegister) {
        await register(username, password, confirmPassword);
      } else {
        await login(username, password);
        if (!remember) {
          // 如需仅在内存中保存登录态，可在此扩展。
        }
      }
      navigate("/chat");
    } catch (err) {
      setError((err as Error).message || (isRegister ? "注册失败，请稍后重试。" : "登录失败，请稍后重试。"));
    }
  };

  return (
    <main className="relative min-h-[100dvh] overflow-hidden bg-[#F8F1E7] text-[#1F1826]">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_16%_14%,rgba(244,154,95,0.2),transparent_30%),radial-gradient(circle_at_82%_20%,rgba(105,91,205,0.16),transparent_28%),radial-gradient(circle_at_64%_86%,rgba(51,154,137,0.13),transparent_30%),linear-gradient(135deg,#FFF8EF_0%,#F8F1E7_54%,#ECEBFA_100%)]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(31,24,38,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(31,24,38,0.08)_1px,transparent_1px)] [background-size:48px_48px]"
      />

      <div className="relative mx-auto flex min-h-[100dvh] w-full max-w-7xl flex-col px-6 py-6 sm:px-8 lg:px-10">
        <nav className="flex items-center justify-between lg:-mx-20 xl:-mx-28 2xl:-mx-36">
          <button type="button" onClick={() => navigate("/")} className="flex items-center gap-3 text-left">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#1F1826] text-[#F8F1E7] shadow-[0_16px_34px_rgba(31,24,38,0.18)]">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <p className="text-base font-semibold tracking-[-0.03em]">LuminaGraph</p>
              <p className="text-xs text-[#756676]">AI Agent 编排系统</p>
            </div>
          </button>
          <button
            type="button"
            onClick={() => navigate("/")}
            className="hidden rounded-full border border-[#1F1826]/14 bg-white/70 px-4 py-2.5 text-sm font-medium text-[#1F1826] shadow-[0_12px_28px_rgba(31,24,38,0.08)] transition hover:-translate-y-0.5 hover:bg-white sm:inline-flex"
          >
            返回首页
          </button>
        </nav>

        <section className="grid flex-1 items-center gap-12 py-14 lg:grid-cols-[0.94fr_0.9fr] lg:py-8">
          <div className="hidden lg:block">
            <div className="inline-flex items-center gap-2 rounded-full border border-[#1F1826]/10 bg-white/62 px-3 py-2 text-xs font-semibold tracking-[0.18em] text-[#6E5B78] shadow-[0_10px_24px_rgba(31,24,38,0.06)]">
              <span className="h-2 w-2 rounded-full bg-[#E96F4B]" />
              LUMINAGRAPH ACCESS
            </div>
            <h1 className="mt-7 max-w-[680px] text-balance text-[clamp(3.2rem,7vw,6.4rem)] font-semibold leading-[0.88] tracking-[-0.078em] text-[#21182B]">
              进入你的编排轨道。
            </h1>
            <p className="mt-7 max-w-[60ch] text-lg leading-8 text-[#51465A]">
              面向私域问答、长工单处理与智能运维的 Agent 运行入口。登录后继续管理你的 Workflow、RAGFlow、ReAct 与 PaE 对话链路。
            </p>
            <div className="mt-8 grid max-w-[520px] grid-cols-2 gap-3">
              {["任务拆解", "状态流转", "工具编排", "多阶段校验"].map((item) => (
                <div key={item} className="rounded-2xl border border-[#1F1826]/10 bg-white/46 px-4 py-3 text-sm font-medium text-[#3D3347] shadow-[0_12px_28px_rgba(31,24,38,0.05)]">
                  {item}
                </div>
              ))}
            </div>
          </div>

          <div className="mx-auto w-full max-w-[440px] rounded-[2.25rem] border border-[#1F1826]/10 bg-white/50 p-2 shadow-[0_34px_90px_rgba(31,24,38,0.16)] backdrop-blur-xl">
            <div className="rounded-[1.8rem] border border-white/74 bg-[#FFFDF8]/92 p-6 shadow-[inset_0_1px_0_rgba(255,255,255,0.9)] sm:p-8">
              <div className="mb-7 flex items-start justify-between gap-4">
                <div>
                  <p className="text-3xl font-semibold tracking-[-0.045em] text-[#21182B]">
                    {isRegister ? "创建账号" : "欢迎回来"}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-[#756676]">
                    {isRegister ? "注册后自动进入 LuminaGraph 对话空间。" : "登录后继续你的 Agent 编排会话。"}
                  </p>
                </div>
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[#21182B] text-[#FFF8EF] shadow-[0_12px_28px_rgba(31,24,38,0.18)]">
                  <Sparkles className="h-5 w-5" />
                </div>
              </div>

              <div className="mb-5 grid grid-cols-2 rounded-2xl bg-[#F2E9DD] p-1 text-sm font-semibold text-[#756676]">
                <button
                  type="button"
                  onClick={() => switchMode("login")}
                  className={cn(
                    "rounded-xl px-4 py-2.5 transition",
                    !isRegister ? "bg-[#21182B] text-[#FFF8EF] shadow-[0_8px_20px_rgba(31,24,38,0.18)]" : "hover:text-[#21182B]"
                  )}
                >
                  登录
                </button>
                <button
                  type="button"
                  onClick={() => switchMode("register")}
                  className={cn(
                    "rounded-xl px-4 py-2.5 transition",
                    isRegister ? "bg-[#21182B] text-[#FFF8EF] shadow-[0_8px_20px_rgba(31,24,38,0.18)]" : "hover:text-[#21182B]"
                  )}
                >
                  注册
                </button>
              </div>

              <form className="space-y-4" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <label className="text-xs font-semibold tracking-[0.14em] text-[#6E5B78]">用户名</label>
                  <div className="relative">
                    <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#8D7C8E]" />
                    <Input
                      placeholder={isRegister ? "设置 3-32 位用户名" : "请输入用户名"}
                      value={form.username}
                      onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                      className="h-11 rounded-xl border-[#E8DDCF] bg-[#F7F0E6] pl-10 text-[#21182B] shadow-none placeholder:text-[#9A879B] focus-visible:ring-[#F4A261]"
                      autoComplete="username"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-xs font-semibold tracking-[0.14em] text-[#6E5B78]">密码</label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#8D7C8E]" />
                    <Input
                      type={showPassword ? "text" : "password"}
                      placeholder={isRegister ? "设置 6-64 位密码" : "请输入密码"}
                      value={form.password}
                      onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                      className="h-11 rounded-xl border-[#E8DDCF] bg-[#F7F0E6] pl-10 pr-10 text-[#21182B] shadow-none placeholder:text-[#9A879B] focus-visible:ring-[#F4A261]"
                      autoComplete={isRegister ? "new-password" : "current-password"}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((prev) => !prev)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8D7C8E] transition hover:text-[#21182B]"
                      aria-label="显示或隐藏密码"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                {isRegister ? (
                  <div className="space-y-2">
                    <label className="text-xs font-semibold tracking-[0.14em] text-[#6E5B78]">确认密码</label>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#8D7C8E]" />
                      <Input
                        type={showConfirmPassword ? "text" : "password"}
                        placeholder="再次输入密码"
                        value={form.confirmPassword}
                        onChange={(event) => setForm((prev) => ({ ...prev, confirmPassword: event.target.value }))}
                        className="h-11 rounded-xl border-[#E8DDCF] bg-[#F7F0E6] pl-10 pr-10 text-[#21182B] shadow-none placeholder:text-[#9A879B] focus-visible:ring-[#F4A261]"
                        autoComplete="new-password"
                      />
                      <button
                        type="button"
                        onClick={() => setShowConfirmPassword((prev) => !prev)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8D7C8E] transition hover:text-[#21182B]"
                        aria-label="显示或隐藏确认密码"
                      >
                        {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>
                ) : null}

                {!isRegister ? (
                  <div className="flex items-center justify-between gap-4 text-sm">
                    <label className="flex items-center gap-2 text-[#756676]">
                      <Checkbox checked={remember} onCheckedChange={(value) => setRemember(Boolean(value))} />
                      记住登录状态
                    </label>
                    <button
                      type="button"
                      onClick={() => switchMode("register")}
                      className="text-xs font-medium text-[#8B4B2E] transition hover:text-[#21182B]"
                    >
                      创建新账号
                    </button>
                  </div>
                ) : (
                  <p className="text-xs leading-5 text-[#756676]">
                    注册账号默认为普通用户，可进入对话与个人会话空间。
                  </p>
                )}

                {error ? <p className="rounded-xl bg-[#FFF0E6] px-3 py-2 text-sm text-[#B4533A]">{error}</p> : null}

                <Button
                  type="submit"
                  className="h-12 w-full rounded-xl bg-[#21182B] text-[#FFF8EF] shadow-[0_16px_34px_rgba(31,24,38,0.18)] transition hover:bg-[#33243F] active:scale-[0.99]"
                  disabled={isLoading}
                >
                  {isLoading ? (isRegister ? "正在注册..." : "正在登录...") : isRegister ? "注册并进入" : "登录"}
                </Button>
              </form>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
