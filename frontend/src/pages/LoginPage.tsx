import * as React from "react";
import { Eye, EyeOff, Lock, Sparkles, User } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useAuthStore } from "@/stores/authStore";

export function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuthStore();
  const [showPassword, setShowPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({ username: "", password: "" });
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    try {
      await login(form.username.trim(), form.password.trim());
      if (!remember) {
        // 如需仅在内存中保存登录态，可在此扩展。
      }
      navigate("/chat");
    } catch (err) {
      setError((err as Error).message || "登录失败，请稍后重试。");
    }
  };

  return (
    <div className="relative flex min-h-[100dvh] items-center justify-center overflow-hidden px-4 py-10">
      <div
        aria-hidden="true"
        className="absolute inset-0 bg-[radial-gradient(circle_at_18%_16%,oklch(0.9_0.055_205/.82),transparent_24%),radial-gradient(circle_at_84%_8%,oklch(0.93_0.075_76/.72),transparent_26%),linear-gradient(140deg,oklch(0.975_0.006_250),oklch(0.94_0.012_250))]"
      />
      <div
        aria-hidden="true"
        className="absolute inset-0 opacity-[0.3] [background-image:linear-gradient(oklch(0.2_0.02_250/.06)_1px,transparent_1px),linear-gradient(90deg,oklch(0.2_0.02_250/.06)_1px,transparent_1px)] [background-size:38px_38px]"
      />
      <div className="relative z-10 grid w-full max-w-5xl items-center gap-8 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="hidden lg:block">
          <div className="inline-flex items-center gap-2 rounded-xl border border-[oklch(0.84_0.02_225)] bg-[oklch(0.99_0.004_250)]/70 px-3 py-2 text-xs font-semibold tracking-[0.14em] text-[oklch(0.48_0.13_245)] shadow-[0_12px_32px_rgba(31,41,55,0.06)] backdrop-blur-xl">
            <Sparkles className="h-4 w-4" />
            RAGENT ACCESS
          </div>
          <h1 className="mt-6 max-w-[620px] font-display text-[clamp(3.6rem,7vw,6.4rem)] font-semibold leading-[0.9] tracking-[-0.065em] text-[oklch(0.2_0.02_250)]">
            进入你的知识轨道。
          </h1>
          <p className="mt-6 max-w-[56ch] text-lg leading-8 text-[oklch(0.43_0.03_250)]">
            面向正式使用场景的智能问答入口，专注检索增强对话、资料追问和可信回答。
          </p>
        </div>
        <div className="w-full rounded-[2.1rem] border border-[oklch(0.88_0.012_250)] bg-[oklch(0.995_0.004_250)]/82 p-2 shadow-[0_30px_80px_rgba(31,41,55,0.16)] backdrop-blur-xl">
          <div className="rounded-[1.65rem] border border-[oklch(0.9_0.01_250)] bg-[oklch(0.99_0.004_250)] p-7 shadow-[inset_0_1px_0_oklch(1_0_0/.8)] sm:p-8">
            <div className="mb-7">
              <p className="font-display text-3xl font-semibold tracking-[-0.04em] text-[oklch(0.22_0.018_250)]">欢迎回来</p>
              <p className="mt-2 text-sm leading-6 text-[oklch(0.5_0.025_250)]">
                登录后继续你的检索增强对话。
              </p>
            </div>
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label className="text-xs font-semibold tracking-[0.14em] text-[oklch(0.48_0.03_250)]">
                  用户名
                </label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[oklch(0.55_0.025_250)]" />
                  <Input
                    placeholder="请输入用户名"
                    value={form.username}
                    onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                    className="h-11 rounded-xl border-[oklch(0.88_0.012_250)] bg-[oklch(0.965_0.007_250)] pl-10 text-[oklch(0.24_0.02_250)] focus-visible:ring-[oklch(0.72_0.08_245)]"
                    autoComplete="username"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <label className="text-xs font-semibold tracking-[0.14em] text-[oklch(0.48_0.03_250)]">
                  密码
                </label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[oklch(0.55_0.025_250)]" />
                  <Input
                    type={showPassword ? "text" : "password"}
                    placeholder="请输入密码"
                    value={form.password}
                    onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                    className="h-11 rounded-xl border-[oklch(0.88_0.012_250)] bg-[oklch(0.965_0.007_250)] pl-10 pr-10 text-[oklch(0.24_0.02_250)] focus-visible:ring-[oklch(0.72_0.08_245)]"
                    autoComplete="current-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[oklch(0.55_0.025_250)] transition hover:text-[oklch(0.32_0.03_250)]"
                    aria-label="显示或隐藏密码"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
              <div className="flex items-center justify-between gap-4 text-sm">
                <label className="flex items-center gap-2 text-[oklch(0.5_0.025_250)]">
                  <Checkbox checked={remember} onCheckedChange={(value) => setRemember(Boolean(value))} />
                  记住登录状态
                </label>
                <span className="text-xs text-[oklch(0.56_0.025_250)]">请使用已分配账号</span>
              </div>
              {error ? <p className="text-sm text-[oklch(0.58_0.18_25)]">{error}</p> : null}
              <Button
                type="submit"
                className="h-11 w-full rounded-xl bg-[oklch(0.25_0.045_250)] text-[oklch(0.98_0.004_250)] transition hover:bg-[oklch(0.31_0.05_250)] active:scale-[0.99]"
                disabled={isLoading}
              >
                {isLoading ? "正在登录..." : "登录"}
              </Button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
