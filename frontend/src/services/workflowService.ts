import { api } from "@/services/api";

export interface AgentWorkflowNode {
  nodeKey: string;
  nodeName?: string | null;
  nodeType: string;
  actionType?: string | null;
  skillId?: string | null;
  config?: Record<string, unknown> | null;
  inputMapping?: Record<string, unknown> | null;
  outputMapping?: Record<string, unknown> | null;
  retryLimit?: number | null;
  timeoutMs?: number | null;
  nodeOrder?: number | null;
}

export interface AgentWorkflowEdge {
  sourceNodeKey: string;
  targetNodeKey: string;
  edgeType: string;
  conditionExpr?: string | null;
  priority?: number | null;
}

export interface AgentWorkflow {
  id: string;
  name: string;
  description?: string | null;
  workflowType?: string | null;
  harnessType?: string | null;
  version?: number | null;
  status?: string | null;
  inputSchema?: Record<string, unknown> | null;
  outputSchema?: Record<string, unknown> | null;
  config?: Record<string, unknown> | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  nodes?: AgentWorkflowNode[] | null;
  edges?: AgentWorkflowEdge[] | null;
}

export interface WorkflowMemoryEvent {
  eventType?: string;
  eventLevel?: string;
  importance?: number;
  content?: string;
  payload?: Record<string, unknown>;
}

export interface WorkflowMemory {
  strategyType?: string;
  summary?: string;
  highImportanceEvents?: WorkflowMemoryEvent[];
  compressedContext?: {
    shortTermContext?: Record<string, unknown>;
    highImportanceEvents?: WorkflowMemoryEvent[];
    recentNodeOutputs?: Record<string, unknown>;
  };
}

export interface AgentWorkflowNodeInstance {
  id: string;
  instanceId: string;
  workflowId: string;
  nodeKey: string;
  nodeName?: string | null;
  nodeType?: string | null;
  actionType?: string | null;
  status?: string | null;
  input?: Record<string, unknown> | null;
  output?: Record<string, unknown> | null;
  errorMessage?: string | null;
  retryCount?: number | null;
  startedAt?: string | null;
  completedAt?: string | null;
  durationMs?: number | null;
}

export interface AgentWorkflowEvent {
  id: string;
  instanceId: string;
  nodeInstanceId?: string | null;
  eventType?: string | null;
  eventLevel?: string | null;
  content?: string | null;
  payload?: Record<string, unknown> | null;
  importanceScore?: number | null;
  createTime?: string | null;
}
export interface AgentWorkflowInstance {
  id: string;
  workflowId: string;
  workflowVersion?: number | null;
  harnessType?: string | null;
  businessType?: string | null;
  businessId?: string | null;
  userId?: string | null;
  status?: string | null;
  input?: Record<string, unknown> | null;
  context?: Record<string, unknown> & {
    workflowMemory?: WorkflowMemory;
  };
  output?: Record<string, unknown> | null;
  errorMessage?: string | null;
  currentNodeKey?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  nodes?: AgentWorkflowNodeInstance[] | null;
  events?: AgentWorkflowEvent[] | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface AgentWorkflowCreatePayload {
  name: string;
  description?: string;
  workflowType: string;
  harnessType: string;
  status: string;
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  config?: Record<string, unknown>;
  nodes: AgentWorkflowNode[];
  edges: AgentWorkflowEdge[];
}

export interface AgentWorkflowRunPayload {
  businessType: string;
  businessId: string;
  input: Record<string, unknown>;
}

export async function getAgentWorkflows(params: {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  status?: string;
} = {}): Promise<PageResult<AgentWorkflow>> {
  return api.get<PageResult<AgentWorkflow>, PageResult<AgentWorkflow>>("/agent/workflows", {
    params: {
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 10,
      keyword: params.keyword || undefined,
      status: params.status || undefined
    }
  });
}

export async function createAgentWorkflow(payload: AgentWorkflowCreatePayload): Promise<AgentWorkflow> {
  return api.post<AgentWorkflow, AgentWorkflow>("/agent/workflows", payload);
}

export async function runAgentWorkflow(id: string, payload: AgentWorkflowRunPayload): Promise<AgentWorkflowInstance> {
  return api.post<AgentWorkflowInstance, AgentWorkflowInstance>(`/agent/workflows/${id}/run`, payload);
}

export async function getAgentWorkflowInstance(id: string): Promise<AgentWorkflowInstance> {
  return api.get<AgentWorkflowInstance, AgentWorkflowInstance>(`/agent/workflow-instances/${id}`);
}

export async function getAgentWorkflowInstances(params: {
  pageNo?: number;
  pageSize?: number;
  workflowId?: string;
} = {}): Promise<PageResult<AgentWorkflowInstance>> {
  return api.get<PageResult<AgentWorkflowInstance>, PageResult<AgentWorkflowInstance>>("/agent/workflow-instances", {
    params: {
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 10,
      workflowId: params.workflowId || undefined
    }
  });
}

export function buildTicketTriageWorkflow(): AgentWorkflowCreatePayload {
  return {
    name: "工单账号分析 Workflow",
    description: "演示用户提出账号或工单疑问后，Workflow 调用 MCP 查询账号、订阅、支付和工单数据，再输出处理建议的固定流程",
    workflowType: "ticket_triage_chat",
    harnessType: "FLOW",
    status: "ENABLED",
    config: {
      memoryEnabled: true,
      memoryStrategyType: "LAYERED",
      memorySummaryInterval: 1
    },
    inputSchema: {
      type: "object",
      properties: {
        ticketId: { type: "string" },
        accountId: { type: "string" },
        content: { type: "string" },
        question: { type: "string" }
      }
    },
    outputSchema: { type: "object" },
    nodes: [
      { nodeKey: "queryAccountTicket", nodeName: "查询账号工单数据", nodeType: "ACTION", actionType: "MCP_TOOL", nodeOrder: 1, config: { toolName: "ticket.account.query", inputParameters: ["ticketId", "accountId"] } },
      { nodeKey: "analyzeAccountTicket", nodeName: "分析账号当前状态", nodeType: "ACTION", actionType: "TICKET_ACCOUNT_ANALYSIS", nodeOrder: 2, config: { sourceNodeKey: "queryAccountTicket" } },
      { nodeKey: "evaluateAnalysis", nodeName: "验收分析结果", nodeType: "EVALUATOR", nodeOrder: 3, config: { evaluatorType: "RULE", targetNodeKey: "analyzeAccountTicket", requiredFields: ["ticketId", "accountId", "riskLevel", "rootCause", "currentState", "suggestion", "customerReply"], minLength: 20, maxReflectionRounds: 0, retryNodeKey: "analyzeAccountTicket" } },
      { nodeKey: "end", nodeName: "结束", nodeType: "END", nodeOrder: 4, config: {} }
    ],
    edges: [
      { sourceNodeKey: "queryAccountTicket", targetNodeKey: "analyzeAccountTicket", edgeType: "DEFAULT", priority: 1 },
      { sourceNodeKey: "analyzeAccountTicket", targetNodeKey: "evaluateAnalysis", edgeType: "DEFAULT", priority: 1 },
      { sourceNodeKey: "evaluateAnalysis", targetNodeKey: "end", edgeType: "DEFAULT", priority: 1 }
    ]
  };
}
export function buildLayeredMemoryDemoWorkflow(): AgentWorkflowCreatePayload {
  return {
    name: `Workflow 任务级记忆演示 ${new Date().toLocaleTimeString("zh-CN", { hour12: false })}`,
    description: "用于前端验证 FlowHarness、Evaluator 和 LAYERED 任务级记忆的演示流程",
    workflowType: "stage5_layered_memory_ui",
    harnessType: "FLOW",
    status: "ENABLED",
    config: {
      memoryEnabled: true,
      memoryStrategyType: "LAYERED",
      memorySummaryInterval: 2
    },
    inputSchema: {
      type: "object",
      properties: {
        ticketId: { type: "string" },
        content: { type: "string" }
      }
    },
    outputSchema: {
      type: "object"
    },
    nodes: [
      {
        nodeKey: "prepareContext",
        nodeName: "准备上下文",
        nodeType: "ACTION",
        actionType: "NOOP",
        nodeOrder: 1,
        config: {}
      },
      {
        nodeKey: "generateSolution",
        nodeName: "生成处理方案",
        nodeType: "ACTION",
        actionType: "NOOP",
        nodeOrder: 2,
        config: {}
      },
      {
        nodeKey: "evaluateSolution",
        nodeName: "验收处理方案",
        nodeType: "EVALUATOR",
        nodeOrder: 3,
        config: {
          evaluatorType: "RULE",
          targetNodeKey: "generateSolution",
          requiredFields: ["message", "nodeKey"],
          minLength: 20,
          maxReflectionRounds: 0,
          retryNodeKey: "generateSolution"
        }
      },
      {
        nodeKey: "end",
        nodeName: "结束",
        nodeType: "END",
        nodeOrder: 4,
        config: {}
      }
    ],
    edges: [
      {
        sourceNodeKey: "prepareContext",
        targetNodeKey: "generateSolution",
        edgeType: "DEFAULT",
        priority: 1
      },
      {
        sourceNodeKey: "generateSolution",
        targetNodeKey: "evaluateSolution",
        edgeType: "DEFAULT",
        priority: 1
      },
      {
        sourceNodeKey: "evaluateSolution",
        targetNodeKey: "end",
        edgeType: "DEFAULT",
        priority: 1
      }
    ]
  };
}
