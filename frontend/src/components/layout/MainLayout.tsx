import * as React from "react";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";

interface MainLayoutProps {
  children: React.ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);

  return (
    <div className="relative flex min-h-[100dvh] overflow-hidden bg-[oklch(0.955_0.008_250)] text-[oklch(0.22_0.018_250)]">
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_12%_8%,oklch(0.88_0.045_205/.75),transparent_30%),radial-gradient(circle_at_88%_12%,oklch(0.92_0.055_70/.65),transparent_28%),linear-gradient(135deg,oklch(0.975_0.006_250),oklch(0.95_0.01_250))]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 opacity-[0.28] [background-image:linear-gradient(oklch(0.2_0.02_250/.055)_1px,transparent_1px),linear-gradient(90deg,oklch(0.2_0.02_250/.055)_1px,transparent_1px)] [background-size:34px_34px]"
      />
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="relative flex min-h-[100dvh] flex-1 flex-col overflow-hidden border-l border-[oklch(0.88_0.012_250)]/70 bg-[oklch(0.985_0.004_250)]/72 shadow-[inset_1px_0_0_oklch(1_0_0/.6)]">
        <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
        <main className="min-h-0 flex-1 overflow-hidden">
          {children}
        </main>
      </div>
    </div>
  );
}
