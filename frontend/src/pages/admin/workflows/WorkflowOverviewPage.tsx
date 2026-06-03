import { useEffect, useState, useCallback } from "react";
import { Loader2, GitBranch, ArrowRight, Users, Bot, Wrench, Eye, Zap, ChevronDown, ChevronRight, Workflow } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
  getAgentWorkflows, getAgentWorkflow,
  type AgentWorkflow, type AgentWorkflowNode, type PageResult
} from "@/services/workflowService";
import { getAgentTeam, type AgentTeam, type AgentDefinition } from "@/services/multiAgentService";
import { getErrorMessage } from "@/utils/error";

// ─── mappings ───────────────────
const NODE_LABELS: Record<string, string> = { ACTION: "动作", EVALUATOR: "评估", CONDITION: "条件", REFLECTION: "反思", HUMAN_REVIEW: "人工审核", END: "结束" };
const STRATEGY_LABELS: Record<string, string> = { PIPELINE: "Pipeline", REACT: "ReAct", PLAN_EXECUTE: "Plan-Execute", AGENT_TEAM: "Agent Team" };
const TOPO_LABELS: Record<string, string> = { PARALLEL: "并行", SEQUENTIAL: "顺序", DEBATE: "辩论", HIERARCHICAL: "层级" };
const MERGE_LABELS: Record<string, string> = { SYNTHESIS: "综合合并", FIRST: "最快返回", CONSENSUS: "共识一致", MAJORITY: "多数投票", LEADER: "Leader决策" };
const MEM_LABELS: Record<string, string> = { CONVERSATION: "对话记忆", SUMMARIZE: "摘要压缩", NONE: "无记忆" };
const EDGE_LABELS: Record<string, string> = { DEFAULT: "默认", SUCCESS: "成功", FAILED: "失败", CONDITION: "条件", EVALUATION_PASS: "评估通过", EVALUATION_FAIL: "评估失败" };

const nodeColor = (t: string) => {
  switch (t) {
    case "ACTION": return "border-blue-300 bg-blue-50";
    case "EVALUATOR": return "border-violet-300 bg-violet-50";
    case "END": return "border-green-300 bg-green-50";
    default: return "border-gray-300 bg-gray-50";
  }
};
const edgeColor = (t: string) => t === "DEFAULT" ? "#cbd5e1" : t === "SUCCESS" ? "#10b981" : "#ef4444";

export function WorkflowOverviewPage() {
  const [wfList, setWfList] = useState<AgentWorkflow[]>([]);
  const [wfId, setWfId] = useState("");
  const [workflow, setWorkflow] = useState<AgentWorkflow | null>(null);
  const [teamCache, setTeamCache] = useState<Record<string, AgentTeam>>({});
  const [busy, setBusy] = useState(false);
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());

  // 加载 Workflow 列表
  useEffect(() => {
    getAgentWorkflows({ pageSize: 50 }).then(p => setWfList(p.records)).catch(() => { });
  }, []);

  // 选中 Workflow → 加载详情 + 预加载所有 Agent Team
  const selectWorkflow = useCallback(async (id: string) => {
    setWfId(id); setBusy(true); setWorkflow(null); setTeamCache({});
    try {
      const wf = await getAgentWorkflow(id);
      setWorkflow(wf);
      // 找出所有 AGENT_TEAM 节点，预加载 Team 信息
      const teamIds = (wf.nodes || [])
        .filter(n => (n.config as Record<string, unknown>)?.strategyType === "AGENT_TEAM" && (n.config as Record<string, unknown>)?.teamId)
        .map(n => (n.config as Record<string, unknown>).teamId as string);
      const cache: Record<string, AgentTeam> = {};
      for (const tid of [...new Set(teamIds)]) {
        try { cache[tid] = await getAgentTeam(tid); } catch { /* ignore */ }
      }
      setTeamCache(cache);
    } catch (e) { toast.error(getErrorMessage(e)); }
    finally { setBusy(false); }
  }, []);

  if (busy && !workflow) return <div className="flex items-center justify-center h-64"><Loader2 className="h-8 w-8 animate-spin text-slate-400" /></div>;

  return (
    <div className="admin-page space-y-6">
      {/* Hero */}
      <section className="rounded-2xl bg-gradient-to-br from-slate-950 to-indigo-950 p-6 text-white">
        <div className="inline-flex items-center gap-2 rounded-lg border border-white/15 bg-white/10 px-3 py-1 text-xs">
          <Eye className="h-3.5 w-3.5" /> Overview
        </div>
        <h1 className="mt-4 text-3xl font-semibold">Workflow 全景视图</h1>
        <p className="mt-2 text-sm text-slate-300">可视化每个 Workflow 的完整结构：节点流转、内部策略、Agent 角色与工具。</p>
      </section>

      {/* Selector */}
      <Card>
        <CardContent className="flex items-center gap-4 py-4">
          <Label>选择 Workflow</Label>
          <select className="w-[400px] h-10 rounded-md border px-3 text-sm" value={wfId}
            onChange={e => selectWorkflow(e.target.value)}>
            <option value="">-- 请选择 --</option>
            {wfList.map(wf => <option key={wf.id} value={wf.id}>{wf.name} ({wf.status})</option>)}
          </select>
          {busy && <Loader2 className="h-4 w-4 animate-spin" />}
        </CardContent>
      </Card>

      {!workflow && (
        <Card><CardContent className="py-16 text-center text-slate-400">请在上方选择一个 Workflow 查看全景</CardContent></Card>
      )}

      {workflow && (
        <>
          {/* Workflow Info */}
          <Card>
            <CardContent className="grid gap-3 md:grid-cols-5 py-4">
              <Info label="名称" value={workflow.name} />
              <Info label="类型" value={workflow.workflowType || "-"} />
              <Info label="Harness" value={workflow.harnessType || "-"} />
              <Info label="状态" value={workflow.status || "-"} />
              <Info label="版本" value={`v${workflow.version || 1}`} />
            </CardContent>
          </Card>

          {/* Flow Diagram */}
          <Card>
            <CardHeader><CardTitle className="flex items-center gap-2"><GitBranch className="h-5 w-5" />流转视图</CardTitle></CardHeader>
            <CardContent>
              <FlowDiagram workflow={workflow} teamCache={teamCache} expandedNodes={expandedNodes}
                toggleNode={(k: string) => {
                  const next = new Set(expandedNodes);
                  next.has(k) ? next.delete(k) : next.add(k);
                  setExpandedNodes(next);
                }} />
            </CardContent>
          </Card>

          {/* Node Detail Cards */}
          <div className="space-y-4">
            <h2 className="text-lg font-semibold">节点详情</h2>
            {(workflow.nodes || []).sort((a, b) => (a.nodeOrder || 0) - (b.nodeOrder || 0)).map(node => (
              <NodeDetailCard key={node.nodeKey} node={node} workflow={workflow} teamCache={teamCache} />
            ))}
          </div>

          {/* Edges detail */}
          <Card>
            <CardHeader><CardTitle>边定义</CardTitle></CardHeader>
            <CardContent>
              <div className="grid gap-2 md:grid-cols-2">
                {(workflow.edges || []).map((e, i) => {
                  const src = workflow.nodes?.find(n => n.nodeKey === e.sourceNodeKey);
                  const tgt = workflow.nodes?.find(n => n.nodeKey === e.targetNodeKey);
                  return (
                    <div key={i} className="flex items-center gap-2 rounded-lg bg-slate-50 p-3 text-sm">
                      <Badge variant="outline">{src?.nodeName || e.sourceNodeKey}</Badge>
                      <span className="text-slate-400 text-xs ml-1">({e.sourceNodeKey})</span>
                      <ArrowRight className="h-4 w-4 text-slate-400 mx-1" />
                      <Badge style={{ background: edgeColor(e.edgeType), color: "#fff" }} className="text-xs">{EDGE_LABELS[e.edgeType] || e.edgeType}</Badge>
                      <ArrowRight className="h-4 w-4 text-slate-400 mx-1" />
                      <Badge variant="outline">{tgt?.nodeName || e.targetNodeKey}</Badge>
                      <span className="text-slate-400 text-xs ml-1">({e.targetNodeKey})</span>
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

// ─── Sub Components ───────────────────

function Label({ children }: { children: React.ReactNode }) {
  return <span className="text-sm font-medium text-slate-600 whitespace-nowrap">{children}</span>;
}

function Info({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg bg-slate-50 p-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 font-semibold text-sm">{value}</div>
    </div>
  );
}

// ─── Flow Diagram (horizontal nodes + expanded team below) ──────
function FlowDiagram({ workflow, teamCache, expandedNodes, toggleNode }: {
  workflow: AgentWorkflow;
  teamCache: Record<string, AgentTeam>;
  expandedNodes: Set<string>;
  toggleNode: (key: string) => void;
}) {
  const allNodes = [...(workflow.nodes || [])];
  const edges = workflow.edges || [];

  // 按 edges 构建后继节点映射 + 前驱映射
  const edgeMap = new Map<string, { target: string; type: string }[]>();
  const predecessors = new Map<string, string[]>();
  edges.forEach(e => {
    if (!edgeMap.has(e.sourceNodeKey)) edgeMap.set(e.sourceNodeKey, []);
    edgeMap.get(e.sourceNodeKey)!.push({ target: e.targetNodeKey, type: e.edgeType });
    if (!predecessors.has(e.targetNodeKey)) predecessors.set(e.targetNodeKey, []);
    predecessors.get(e.targetNodeKey)!.push(e.sourceNodeKey);
  });

  // 拓扑推导展示顺序：从无前驱的起始节点出发，沿 edges 走
  const walkOrder: string[] = [];
  const visited = new Set<string>();
  // 找起始节点（无前驱）
  const startNodes = allNodes.filter(n => !predecessors.has(n.nodeKey) || predecessors.get(n.nodeKey)!.length === 0);
  // 按 nodeOrder 排起始节点顺序
  startNodes.sort((a, b) => (a.nodeOrder || 0) - (b.nodeOrder || 0));
  // BFS/DFS 沿 edges 遍历
  function walk(key: string) {
    if (visited.has(key)) return;
    visited.add(key);
    walkOrder.push(key);
    const outs = edgeMap.get(key);
    if (outs) {
      outs.sort((a, b) => {
        const na = allNodes.find(n => n.nodeKey === a.target);
        const nb = allNodes.find(n => n.nodeKey === b.target);
        return (na?.nodeOrder || 0) - (nb?.nodeOrder || 0);
      });
      outs.forEach(o => walk(o.target));
    }
  }
  startNodes.forEach(s => walk(s.nodeKey));
  // 剩下孤立节点也加上
  allNodes.filter(n => !visited.has(n.nodeKey)).forEach(n => walkOrder.push(n.nodeKey));

  // 展示用的有序节点列表
  const nodes = walkOrder.map(k => allNodes.find(n => n.nodeKey === k)!).filter(Boolean);

  // 按 nodeOrder 排序
  // 也支持通过 edges 推导拓扑序
  const nodeMap = new Map(nodes.map(n => [n.nodeKey, n]));

  return (
    <div className="overflow-x-auto pb-4">
      {/* ─── 第一行：节点卡片横向流转 ─── */}
      <div className="flex min-w-max items-start">
        {nodes.map((node, i) => {
          const config = (node.config || {}) as Record<string, unknown>;
          const strategy = config.strategyType as string;
          const isAgentTeam = strategy === "AGENT_TEAM";
          const team = isAgentTeam && config.teamId ? teamCache[config.teamId as string] : null;
          const isExpanded = expandedNodes.has(node.nodeKey);
          const outEdges = edgeMap.get(node.nodeKey) || [];
          // 本节点出边
          const label = outEdges.length > 1
            ? outEdges.map(e => EDGE_LABELS[e.type] || e.type).join(",")
            : outEdges.length === 1 ? (EDGE_LABELS[outEdges[0].type] || outEdges[0].type) : null;

          return (
            <div key={node.nodeKey} className="flex items-start">
              {/* Node Card */}
              <div className={cn("w-[260px] rounded-2xl border-2 bg-white p-4 shadow-sm flex-shrink-0", nodeColor(node.nodeType))}>
                <div className="flex items-center justify-between mb-1">
                  <Badge className="text-xs">{NODE_LABELS[node.nodeType] || node.nodeType}</Badge>
                  <span className="text-xs text-slate-400">#{node.nodeOrder}</span>
                </div>
                <div className="font-bold text-sm mb-0.5">{node.nodeName || node.nodeKey}</div>
                <div className="font-mono text-[10px] text-slate-400 mb-2">{node.nodeKey}</div>
                <div className="flex flex-wrap gap-1 mb-1">
                  {strategy && <Badge variant="secondary" className="text-xs"><Zap className="h-3 w-3 mr-0.5" />{STRATEGY_LABELS[strategy] || strategy}</Badge>}
                  {node.actionType && node.actionType !== "NOOP" && <Badge variant="outline" className="text-xs"><Wrench className="h-3 w-3 mr-0.5" />{node.actionType}</Badge>}
                </div>
                {node.nodeType === "EVALUATOR" && (
                  <div className="text-xs text-slate-500 space-y-0.5 mt-1 border-t pt-1">
                    <div>方式: <b>{config.evaluatorType as string || "RULE"}</b> · 目标: <b>{config.targetNodeKey as string || "-"}</b></div>
                  </div>
                )}
                {isAgentTeam && (
                  <button className="mt-1 flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-800 border-t pt-1 w-full"
                    onClick={() => toggleNode(node.nodeKey)}>
                    <Users className="h-3 w-3" />
                    <span className="truncate">{team ? team.name : "加载中..."} ({team?.agents?.length || "?"} Agent)</span>
                    {isExpanded ? <ChevronDown className="h-3 w-3 ml-auto flex-shrink-0" /> : <ChevronRight className="h-3 w-3 ml-auto flex-shrink-0" />}
                  </button>
                )}
              </div>

              {/* 箭头（只在有出边时显示） */}
              {outEdges.length > 0 && (
                <div className="flex flex-col items-center justify-center w-20 flex-shrink-0 pt-10">
                  <div className="h-px w-full bg-slate-300" />
                  <div className="flex items-center gap-1 -mt-2.5">
                    <ArrowRight className="h-4 w-4 text-slate-400" />
                    {label && <span className="text-[10px] text-slate-400 bg-white px-1">{label}</span>}
                  </div>
                  <div className="h-px w-full bg-slate-300" />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* ─── 第二行：展开的 Agent Team 详情 ─── */}
      <div className="mt-4">
        {nodes.filter(n => {
          const config = (n.config || {}) as Record<string, unknown>;
          const strategy = config.strategyType as string;
          return strategy === "AGENT_TEAM" && expandedNodes.has(n.nodeKey);
        }).map(node => {
          const config = (node.config || {}) as Record<string, unknown>;
          const team = teamCache[config.teamId as string];
          if (!team) return null;
          return (
            <div key={`team-${node.nodeKey}`} className="mb-4 rounded-2xl border-2 border-indigo-200 bg-indigo-50/30 p-5">
              <div className="flex items-center gap-3 mb-4">
                <div className="flex items-center gap-2 text-indigo-900">
                  <GitBranch className="h-4 w-4" />
                  <span className="font-bold">{node.nodeName || node.nodeKey}</span>
                </div>
                <Badge variant="outline">{TOPO_LABELS[team.topology] || team.topology}</Badge>
                <Badge variant="outline">{MERGE_LABELS[team.mergeStrategy] || team.mergeStrategy}</Badge>
                <span className="text-xs text-slate-400 ml-auto">
                  ↑ 上游: {(predecessors.get(node.nodeKey) || []).join(", ") || "无"} &nbsp;|&nbsp;
                  ↓ 下游: {(edgeMap.get(node.nodeKey) || []).map(e => e.target).join(", ") || "无"}
                </span>
              </div>
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {team.agents?.map(agent => (
                  <div key={agent.agentKey} className="rounded-xl border bg-white p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Bot className="h-5 w-5 text-indigo-500" />
                      <span className="font-bold text-sm">{agent.agentName}</span>
                      {agent.isLeader && <Badge className="bg-amber-100 text-amber-700 text-[10px]">Leader</Badge>}
                    </div>
                    <div className="font-mono text-[10px] text-slate-400 mb-2">{agent.agentKey}</div>
                    <div className="text-xs text-slate-600 mb-2 line-clamp-3" title={agent.role}>{agent.role}</div>
                    {agent.goal && <div className="text-[11px] text-slate-500 mb-2">目标: {agent.goal}</div>}
                    <div className="flex flex-wrap gap-1 mb-2">
                      <Badge variant="secondary" className="text-[10px]">{STRATEGY_LABELS[((agent.llmConfig as Record<string, unknown>)?.strategyType as string) || "PIPELINE"]}</Badge>
                      <Badge variant="outline" className="text-[10px]">{MEM_LABELS[agent.memoryStrategy || "CONVERSATION"]}</Badge>
                      {agent.modelId && <Badge variant="outline" className="text-[10px] font-mono">{agent.modelId}</Badge>}
                    </div>
                    {agent.toolNames && agent.toolNames.length > 0 && (
                      <div className="flex flex-wrap gap-1 border-t pt-2">
                        <span className="text-[10px] text-slate-400">工具:</span>
                        {agent.toolNames.map(t => <Badge key={t} className="text-[10px] bg-slate-100 text-slate-700">{t}</Badge>)}
                      </div>
                    )}
                  </div>
                ))}
              </div>
              <div className="text-[11px] text-slate-400 mt-3 pt-2 border-t">
                数据流: 上游节点输出 → 所有 Agent 按 {TOPO_LABELS[team.topology] || team.topology} 模式执行
                → 通过 {MERGE_LABELS[team.mergeStrategy] || team.mergeStrategy} 合并 → 传递给下游节点
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── Node Detail Card (full json) ──────
function NodeDetailCard({ node, workflow, teamCache }: { node: AgentWorkflowNode; workflow: AgentWorkflow; teamCache: Record<string, AgentTeam> }) {
  const config = (node.config || {}) as Record<string, unknown>;
  const strategy = config.strategyType as string;
  const team = strategy === "AGENT_TEAM" && config.teamId ? teamCache[config.teamId as string] : null;

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-3">
        <CardTitle className="text-base flex items-center gap-2">
          <Badge className={cn(nodeColor(node.nodeType))}>{NODE_LABELS[node.nodeType] || node.nodeType}</Badge>
          {node.nodeName || node.nodeKey}
          <span className="text-xs text-slate-400 font-normal">({node.nodeKey})</span>
        </CardTitle>
        <div className="flex gap-2">
          {strategy && <Badge variant="secondary">{STRATEGY_LABELS[strategy] || strategy}</Badge>}
          {node.actionType && <Badge variant="outline">{node.actionType}</Badge>}
          {node.timeoutMs && <span className="text-xs text-slate-400">超时:{node.timeoutMs}ms</span>}
          {node.retryLimit && node.retryLimit > 0 && <span className="text-xs text-slate-400">重试:{node.retryLimit}</span>}
        </div>
      </CardHeader>
      <CardContent>
        {/* Upstream / Downstream */}
        <div className="text-xs text-slate-500 mb-4 flex gap-4">
          <span>上游: {(workflow.edges || []).filter(e => e.targetNodeKey === node.nodeKey).map(e => e.sourceNodeKey).join(", ") || "无（起始节点）"}</span>
          <span>下游: {(workflow.edges || []).filter(e => e.sourceNodeKey === node.nodeKey).map(e => e.targetNodeKey).join(", ") || "无（结束节点）"}</span>
        </div>

        {/* Agent Team details */}
        {team && (
          <div className="rounded-xl border-2 border-indigo-200 bg-indigo-50/30 p-4 mb-4">
            <div className="flex items-center gap-2 mb-3">
              <Users className="h-5 w-5 text-indigo-600" />
              <span className="font-bold text-indigo-900">{team.name}</span>
              <Badge>{TOPO_LABELS[team.topology] || team.topology}</Badge>
              <Badge variant="outline">{MERGE_LABELS[team.mergeStrategy] || team.mergeStrategy}</Badge>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              {team.agents?.map(agent => (
                <div key={agent.agentKey} className="rounded-lg border bg-white p-3">
                  <div className="flex items-center gap-2 mb-1">
                    <Bot className="h-4 w-4 text-indigo-500" />
                    <span className="font-semibold text-sm">{agent.agentName}</span>
                    {agent.isLeader && <Badge className="bg-amber-100 text-amber-700 text-[10px]">Leader</Badge>}
                  </div>
                  <div className="font-mono text-[10px] text-slate-400 mb-1">{agent.agentKey}</div>
                  <div className="text-xs text-slate-600 line-clamp-3 mb-2">{agent.role}</div>
                  <div className="flex flex-wrap gap-1">
                    <Badge variant="secondary" className="text-[10px]">{STRATEGY_LABELS[((agent.llmConfig as Record<string, unknown>)?.strategyType as string) || "PIPELINE"]}</Badge>
                    <Badge variant="outline" className="text-[10px]">{MEM_LABELS[agent.memoryStrategy || "CONVERSATION"]}</Badge>
                    {agent.modelId && <Badge variant="outline" className="text-[10px] font-mono">{agent.modelId}</Badge>}
                  </div>
                  {agent.toolNames && agent.toolNames.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2 border-t pt-2">
                      <span className="text-[10px] text-slate-400">工具:</span>
                      {agent.toolNames.map(t => <Badge key={t} className="text-[10px] bg-slate-100">{t}</Badge>)}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Full config */}
        <details>
          <summary className="text-xs text-slate-400 cursor-pointer">完整配置 JSON</summary>
          <pre className="mt-2 max-h-48 overflow-auto rounded bg-slate-950 p-3 font-mono text-xs text-slate-100">
            {JSON.stringify(node, null, 2)}
          </pre>
        </details>
      </CardContent>
    </Card>
  );
}
