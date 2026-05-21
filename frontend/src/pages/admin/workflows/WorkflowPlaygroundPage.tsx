import { useEffect, useMemo, useState } from "react";
import { FileJson2, GitBranch, Loader2, Play, Plus, Search, Workflow } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { buildLayeredMemoryDemoWorkflow, buildTicketTriageWorkflow, createAgentWorkflow, getAgentWorkflowInstance, getAgentWorkflowInstances, getAgentWorkflows, runAgentWorkflow, type AgentWorkflow, type AgentWorkflowInstance, type PageResult } from "@/services/workflowService";
import { getErrorMessage } from "@/utils/error";

const DEFAULT_INPUT = JSON.stringify({ ticketId: "T20260520001", accountId: "A10001", content: "我对这个账户有疑问，客户说续费失败，帮我查一下当前状态并给出处理建议。" }, null, 2);
const emptyWf: PageResult<AgentWorkflow> = { records: [], total: 0, size: 8, current: 1, pages: 0 };
const emptyRun: PageResult<AgentWorkflowInstance> = { records: [], total: 0, size: 6, current: 1, pages: 0 };
const j = (v: unknown) => { try { return v == null ? "-" : JSON.stringify(v, null, 2); } catch { return String(v); } };
const t = (v?: string | null) => v ? new Date(v).toLocaleString("zh-CN", { hour12: false }) : "-";
function statusClass(s?: string | null) { const v = (s || "").toUpperCase(); return v === "COMPLETED" || v === "SUCCESS" ? "border-emerald-200 bg-emerald-50 text-emerald-700" : v === "FAILED" ? "border-red-200 bg-red-50 text-red-700" : "border-slate-200 bg-slate-50 text-slate-600"; }
function parseInput(raw: string) { const value = JSON.parse(raw); if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("运行 input 必须是 JSON 对象"); return value as Record<string, unknown>; }
function JsonCard({ title, value }: { title: string; value: unknown }) { return <Card><CardHeader><CardTitle className="flex items-center gap-2"><FileJson2 className="h-4 w-4" />{title}</CardTitle></CardHeader><CardContent className="p-0"><pre className="max-h-72 overflow-auto whitespace-pre-wrap bg-slate-950 p-4 font-mono text-xs leading-5 text-slate-100">{j(value)}</pre></CardContent></Card>; }

export function WorkflowPlaygroundPage() {
  const [keyword, setKeyword] = useState("");
  const [wfPage, setWfPage] = useState(emptyWf);
  const [runPage, setRunPage] = useState(emptyRun);
  const [wfId, setWfId] = useState("");
  const [runId, setRunId] = useState("");
  const [detail, setDetail] = useState<AgentWorkflowInstance | null>(null);
  const [input, setInput] = useState(DEFAULT_INPUT);
  const [busy, setBusy] = useState(false);
  const selected = useMemo(() => wfPage.records.find((x) => x.id === wfId) || null, [wfPage.records, wfId]);

  async function loadWorkflows(pageNo = 1) {
    setBusy(true);
    try {
      const page = await getAgentWorkflows({ pageNo, pageSize: wfPage.size, keyword: keyword.trim() || undefined });
      setWfPage(page);
      const next = page.records.find((x) => x.id === wfId) || page.records[0];
      if (next) setWfId(next.id);
    } catch (e) { toast.error(getErrorMessage(e, "加载 Workflow 失败")); } finally { setBusy(false); }
  }
  async function loadRuns(pageNo = 1, id = wfId) {
    if (!id) return;
    try {
      const page = await getAgentWorkflowInstances({ pageNo, pageSize: runPage.size, workflowId: id });
      setRunPage(page);
      if (page.records[0]) setRunId(page.records[0].id); else { setRunId(""); setDetail(null); }
    } catch (e) { toast.error(getErrorMessage(e, "加载运行历史失败")); }
  }
  useEffect(() => { loadWorkflows(1); }, []);
  useEffect(() => { if (wfId) loadRuns(1, wfId); }, [wfId]);
  useEffect(() => { if (runId) getAgentWorkflowInstance(runId).then(setDetail).catch((e) => toast.error(getErrorMessage(e, "加载实例详情失败"))); }, [runId]);

  async function createWf(type: "ticket" | "demo") {
    setBusy(true);
    try {
      const workflow = await createAgentWorkflow(type === "ticket" ? buildTicketTriageWorkflow() : buildLayeredMemoryDemoWorkflow());
      setWfId(workflow.id);
      toast.success(type === "ticket" ? "工单处理 Workflow 已创建" : "演示 Workflow 已创建");
      await loadWorkflows(1);
    } catch (e) { toast.error(getErrorMessage(e, "创建 Workflow 失败")); } finally { setBusy(false); }
  }
  async function runWf() {
    if (!wfId) return toast.error("请先选择 Workflow");
    let payload: Record<string, unknown>;
    try { payload = parseInput(input); } catch (e) { return toast.error((e as Error).message); }
    setBusy(true);
    try {
      const instance = await runAgentWorkflow(wfId, { businessType: "manual_admin", businessId: `admin-${Date.now()}`, input: payload });
      setRunId(instance.id);
      await loadRuns(1, wfId);
      toast.success("Workflow 执行完成");
    } catch (e) { toast.error(getErrorMessage(e, "运行 Workflow 失败")); } finally { setBusy(false); }
  }

  return <div className="admin-page min-h-full space-y-6">
    <section className="rounded-2xl bg-gradient-to-br from-slate-950 to-indigo-950 p-6 text-white">
      <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div><div className="inline-flex items-center gap-2 rounded-lg border border-white/15 bg-white/10 px-3 py-1 text-xs"><Workflow className="h-3.5 w-3.5" />Internal Workflow Admin</div><h1 className="mt-4 text-3xl font-semibold">Workflow 内部管理页</h1><p className="mt-2 text-sm text-slate-300">分页列表、详情、节点图、运行历史、事件时间线、节点输出折叠面板。</p></div>
        <div className="flex flex-wrap gap-3"><Button className="bg-white text-slate-950 hover:bg-slate-100" disabled={busy} onClick={() => createWf("ticket")}><Plus className="mr-2 h-4 w-4" />创建工单处理 Workflow</Button><Button variant="outline" className="border-white/20 bg-white/10 text-white hover:bg-white/15" disabled={busy} onClick={() => createWf("demo")}>创建演示流程</Button></div>
      </div>
    </section>
    <section className="grid gap-6 xl:grid-cols-[380px_minmax(0,1fr)]">
      <div className="space-y-6"><Card><CardHeader><CardTitle>Workflow 列表</CardTitle></CardHeader><CardContent className="space-y-4"><div className="flex gap-2"><Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="按名称搜索" /><Button variant="outline" onClick={() => loadWorkflows(1)}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}</Button></div><div className="max-h-[420px] space-y-2 overflow-auto">{wfPage.records.map((wf) => <button key={wf.id} onClick={() => setWfId(wf.id)} className={cn("w-full rounded-xl border p-4 text-left", wf.id === wfId ? "border-indigo-200 bg-indigo-50" : "border-slate-200 hover:bg-slate-50")}><div className="flex justify-between gap-2"><b className="line-clamp-1 text-sm">{wf.name}</b><Badge variant="outline">{wf.status}</Badge></div><p className="mt-2 line-clamp-2 text-xs text-slate-500">{wf.description || wf.id}</p><p className="mt-2 font-mono text-[11px] text-slate-400">{wf.workflowType} / v{wf.version || 1}</p></button>)}</div><Pager page={wfPage} onPage={loadWorkflows} /></CardContent></Card><Card><CardHeader><CardTitle>运行输入</CardTitle></CardHeader><CardContent className="space-y-4"><Textarea className="min-h-[200px] font-mono text-xs" value={input} onChange={(e) => setInput(e.target.value)} /><Button className="w-full admin-primary-gradient" disabled={busy || !wfId} onClick={runWf}><Play className="mr-2 h-4 w-4" />运行 Workflow</Button></CardContent></Card></div>
      <div className="space-y-6"><div className="grid gap-4 md:grid-cols-3"><Info label="当前 Workflow" value={selected?.name || "未选择"} /><Info label="节点数量" value={selected?.nodes?.length || 0} /><Info label="运行次数" value={runPage.total || 0} /></div><WorkflowDetail workflow={selected} /><RunHistory page={runPage} runId={runId} onSelect={setRunId} onPage={(p) => loadRuns(p)} /><div className="grid gap-6 xl:grid-cols-2"><Timeline detail={detail} /><NodeOutputs detail={detail} /><JsonCard title="Workflow Memory" value={detail?.context?.workflowMemory} /><JsonCard title="完整实例详情" value={detail} /></div></div>
    </section>
  </div>;
}

function Info({ label, value }: { label: string; value: string | number }) { return <div className="rounded-xl border bg-white p-4"><p className="text-xs text-slate-500">{label}</p><p className="mt-2 line-clamp-1 text-lg font-semibold">{value}</p></div>; }
function Pager<T>({ page, onPage }: { page: PageResult<T>; onPage: (n: number) => void }) { const pages = Math.max(page.pages || 1, 1); return <div className="flex items-center justify-between text-xs text-slate-500"><span>共 {page.total} 条，第 {page.current}/{pages} 页</span><div className="flex gap-2"><Button size="sm" variant="outline" disabled={page.current <= 1} onClick={() => onPage(page.current - 1)}>上一页</Button><Button size="sm" variant="outline" disabled={page.current >= pages} onClick={() => onPage(page.current + 1)}>下一页</Button></div></div>; }
function WorkflowDetail({ workflow }: { workflow: AgentWorkflow | null }) { return <Card><CardHeader><CardTitle className="flex items-center gap-2"><GitBranch className="h-4 w-4" />Workflow 详情与节点图</CardTitle></CardHeader><CardContent className="space-y-4">{workflow ? <><p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">{workflow.description || "暂无描述"}</p><div className="flex flex-wrap gap-3">{(workflow.nodes || []).map((n) => <div key={n.nodeKey} className="rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-3"><p className="text-sm font-semibold">{n.nodeName || n.nodeKey}</p><p className="font-mono text-[11px] text-indigo-600">{n.nodeType}{n.actionType ? ` / ${n.actionType}` : ""}</p></div>)}</div><JsonCard title="Workflow 配置" value={workflow} /></> : <p className="text-sm text-slate-500">请选择 Workflow</p>}</CardContent></Card>; }
function RunHistory({ page, runId, onSelect, onPage }: { page: PageResult<AgentWorkflowInstance>; runId: string; onSelect: (id: string) => void; onPage: (n: number) => void }) { return <Card><CardHeader><CardTitle>运行历史</CardTitle></CardHeader><CardContent className="space-y-3">{page.records.map((r) => <button key={r.id} onClick={() => onSelect(r.id)} className={cn("w-full rounded-xl border p-4 text-left", r.id === runId ? "border-emerald-200 bg-emerald-50" : "border-slate-200 hover:bg-slate-50")}><div className="flex justify-between"><span className="font-mono text-xs">{r.id}</span><Badge variant="outline" className={statusClass(r.status)}>{r.status}</Badge></div><p className="mt-2 text-xs text-slate-500">{t(r.createTime)} / {r.businessType || "-"}</p></button>)}<Pager page={page} onPage={onPage} /></CardContent></Card>; }
function Timeline({ detail }: { detail: AgentWorkflowInstance | null }) { const list = detail?.events || []; return <Card><CardHeader><CardTitle>事件时间线</CardTitle></CardHeader><CardContent className="max-h-96 space-y-3 overflow-auto">{list.map((e) => <div key={e.id} className="border-l-2 border-indigo-200 pl-4"><Badge variant="outline">{e.eventType}</Badge><p className="mt-2 text-sm">{e.content}</p><p className="text-xs text-slate-400">{t(e.createTime)} / importance {e.importanceScore ?? "-"}</p></div>)}</CardContent></Card>; }
function NodeOutputs({ detail }: { detail: AgentWorkflowInstance | null }) { const list = detail?.nodes || []; return <Card><CardHeader><CardTitle>节点输出折叠面板</CardTitle></CardHeader><CardContent className="max-h-96 space-y-3 overflow-auto">{list.map((n) => <details key={n.id} className="rounded-xl border p-4"><summary className="cursor-pointer text-sm font-semibold">{n.nodeName || n.nodeKey} <Badge variant="outline" className={statusClass(n.status)}>{n.status}</Badge></summary><pre className="mt-3 max-h-56 overflow-auto rounded-lg bg-slate-950 p-3 font-mono text-xs text-slate-100">{j(n.output)}</pre></details>)}</CardContent></Card>; }
