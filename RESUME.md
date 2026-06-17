# LuminaGraph — AI Agent 编排系统

**全栈开发** · 2026.01 - 2026.06

**项目概述**：面向企业复杂任务场景的 Agent Workflow 与 Multi-Agent 运行时编排系统。以 DAG 工作流为宏观骨架，以多 Agent 协作拓扑为微观执行肌肉，将大模型的非确定性输出约束在可控的结构化执行链路中。应用于私域问答、长工单处理与智能运维等场景。

代码及演示地址：github.com/Swing-G/LuminaGraph · liuguangyf.top

**技术栈**：Spring Boot 3.5.7 · PostgreSQL (pgvector) · MCP · Redis · React 18 · TypeScript · MyBatis-Plus · Milvus

---

## 项目细节与亮点

**1. Workflow 基座与 Multi-Agent 双层编排**

设计面向复杂任务的 DAG 工作流模型作为宏观骨架，将业务流程拆解为多阶段节点（支持 ACTION / EVALUATOR / CONDITION / HUMAN_REVIEW / END）。在节点内部嵌入 Multi-Agent 协作层，支持 **Parallel（并行会诊）、Sequential（流水线）、Debate（多轮辩论收敛）与 Hierarchical（Leader 拆解→Worker 执行→合成）** 四种协作拓扑。Agent 间通过共享 Blackboard 交换结论，每个 Agent 拥有独立的角色、目标、工具集和执行策略。Workflow 负责条件分支、状态流转与 Checkpoint 回滚，Agent Team 负责节点内的微观协作——两层互补，实现"流程可控 + 执行智能"的统一。

**2. 多策略驱动与反思闭环**

基于策略模式构建 Pipeline / ReAct / Plan-and-Execute 三种执行策略。Agent 可自主选择策略——简单查询走 Pipeline，复杂推理走 ReAct（思考-行动-观察循环），多步操作走 Plan-and-Execute（先规划再执行）。引入独立 Evaluator 节点实现"生成与验收分离"，评估失败时触发 Reflection（LLM 生成修正提示词）+ Checkpoint 回滚，重试被评估节点。ReAct 输出格式非法时自动重试（在 maxIterations 内），避免偶发的 LLM 格式异常导致流程崩溃。

**3. 分层记忆与动态压缩**

构建融合短期上下文、事件记忆、任务状态与长期摘要的分层记忆架构。基于事件类型的重要性评分算法（USER_INPUT=90, TOOL_RESULT=80, NODE_COMPLETED=60 等），配合异步增量摘要与 LLM 失败时的 fallback 机制，实现长链路场景下的上下文动态裁剪与 Token 控制。

**4. MCP 工具网关与结构化 Skill**

基于 Streamable HTTP 构建 MCP 工具网关，统一管理本地工具与远程 MCP Server 工具，支持 LLM 自动提取自然语言参数并调用工具。将业务 SOP、领域规则和工具调用范式沉淀为结构化 Skill（YAML 格式 Markdown 文件），通过 LLM 语义匹配自动路由到对应 Agent 执行上下文。执行后触发 Skill 自检：LLM 主动识别过时内容并生成变更建议，管理员审核后自动合并更新，形成 Skill 持续进化闭环。

**5. 高可用路由与全链路实时观测**

针对多模型路由设计三态熔断器（CLOSED→OPEN→HALF_OPEN）与优先级降级链路，结合首包探测技术使模型切换对用户透明。基于 TransmittableThreadLocal 实现 10 个专用线程池的上下文透传。通过 SSE 分级事件（meta / message / status / finish）实时向客户端推送 Workflow 节点执行状态、Agent 启动/完成的耗时、辩论轮次、层级分阶段进度等全链路信息。用户可实时观察整个 Agent 链路的每一步决策过程。

**6. 可视化编排与端到端管理**

开发完整的 React 管理控制台。**Workflow 可视化构建器**：4 步向导（模板选择→节点编排→连线定义→保存运行），所有配置通过下拉选择完成，拓扑自动约束合并策略选项。**Agent Team 管理**：配置 Agent 角色、目标、工具集、执行策略和记忆策略。**Skill 管理**：在线编辑 SOP、规则、模板，审核 AI 生成的进化建议。**全景视图**：DAG 节点流转图 + Agent Team 展开详览。用户端支持流式打字效果与内容安全过滤。

---

## 项目规模

- 后端 Java 代码约 50,000 行，400+ 源文件，4 个 Maven 模块
- 前端 React/TypeScript 代码约 20,000 行，20+ 页面/组件
- 数据库 25 张业务表，覆盖工作流、多 Agent、Skill 自进化、会话记忆、链路追踪等完整业务域
- 6 个结构化 Skill，12 条演示账号数据，覆盖 6 种真实业务场景
