<p align="center">
  <a href="https://github.com/Swing-G/LiuGuang">
    <picture>
      <source srcset="assets/ragent-ai-banner.png">
      <img src="assets/ragent-ai-banner.png" alt="LiuGuang - Multi-Agent Orchestration">
    </picture>
  </a>
</p>

<p align="center">
  <strong>流光 — Multi-Agent 工作流编排系统</strong><br/>
  <sub>Workflow DAG × 4 协作拓扑 × 结构化 Skill × 自进化闭环</sub>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-blue?style=flat-square&logo=java" />
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5.7-green?style=flat-square&logo=springboot" />
  <img alt="React" src="https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react" />
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-pgvector-4169E1?style=flat-square&logo=postgresql" />
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache--2.0-4a9b8f?style=flat-square" /></a>
</p>

---

## 项目简介

本项目基于开源 RAG 平台 [Ragent AI](https://github.com/nageoffer/ragent)（Apache 2.0）进行深度扩展。在保留原有 RAG 检索、模型路由、文档入库等能力的基础上，全新构建了 **Multi-Agent 工作流编排引擎**，将系统从"单次问答"升级为"多智能体协作"。

**核心思路**：Workflow DAG 作为宏观流程骨架，决定任务分几步、先做什么后做什么；Multi-Agent 作为微观执行肌肉，在某个步骤内启动多个拥有独立角色、目标和工具的 Agent 并行或协作执行。

> 在线体验：http://liuguangyf.top  
> 代码仓库：https://github.com/Swing-G/LiuGuang  
> 原始项目：https://github.com/nageoffer/ragent

---

## 目录

- [一、系统总览](#一系统总览)
- [二、管理员端 — 编排能力详解](#二管理员端--编排能力详解)
  - [2.1 Agent Team 管理](#21-agent-team-管理)
  - [2.2 Workflow 可视化构建](#22-workflow-可视化构建)
  - [2.3 Workflow 全景视图](#23-workflow-全景视图)
  - [2.4 Skill 管理](#24-skill-管理)
  - [2.5 对话绑定](#25-对话绑定)
- [三、用户端 — 对话体验](#三用户端--对话体验)
  - [3.1 实时思考过程](#31-实时思考过程)
  - [3.2 流式打字效果](#32-流式打字效果)
  - [3.3 Skill 自动匹配](#33-skill-自动匹配)
  - [3.4 内容安全过滤](#34-内容安全过滤)
- [四、底层架构能力](#四底层架构能力)
- [五、演示数据与快速体验](#五演示数据与快速体验)
- [六、技术栈](#六技术栈)
- [七、快速启动](#七快速启动)
- [八、项目结构](#八项目结构)
- [九、许可证](#九许可证)

---

## 一、系统总览

系统分为两个使用端：

| 使用端 | 入口 | 功能 |
|--------|------|------|
| **管理员端** | `/admin` | 创建 Agent Team、编排 Workflow、管理 Skill、绑定对话选项、查看全景视图 |
| **用户端** | `/chat` | 选择 Workflow 模式发起对话，实时查看 Agent 执行过程，获取分析结果 |

<!-- 截图占位：首页 / 登录页 -->
> 📸 *[截图1] 系统首页*

<!-- 截图占位：管理员 Dashboard 概览 -->
> 📸 *[截图2] 管理员 Dashboard*

<!-- 截图占位：用户聊天页面全貌 -->
> 📸 *[截图3] 用户聊天页面 — WORKFLOW 模式 + 提示词模板*

---

## 二、管理员端 — 编排能力详解

这是系统的核心能力所在。管理员通过以下步骤构建一个完整的多 Agent 工作流：

```
① 创建 Agent Team（定义有哪些专家、各自什么角色和工具）
  → ② 可视化构建 Workflow（定义任务流程，引用 Team）
    → ③ 绑定到对话选项（让用户在前端能选到）
      → ④ 用户发起对话 → Agent 开始协作执行
```

下面按步骤逐一说明。

---

### 2.1 Agent Team 管理

**路径**：管理后台 → Workflow 管理 → **Agent Team**

Agent Team 是多 Agent 协作的基本单位。一个 Team 定义了：

| 配置项 | 说明 | 可选值 |
|--------|------|--------|
| **Team 名称** | 给 Team 起一个描述性名称 | 例：VIP工单分析专家组 |
| **协作拓扑** | Agent 如何协作执行 | 见下方拓扑详解 |
| **合并策略** | 多个 Agent 的结果如何合并 | 见下方合并策略详解 |
| **最大轮数** | 辩论/层级拓扑的最大执行轮次 | 1-10，默认 3 |

#### 四种协作拓扑

| 拓扑 | 行为 | 适用场景 |
|------|------|----------|
| **PARALLEL（并行）** | 所有 Agent 同时执行，各自独立分析同一输入，由合并策略决定最终输出 | 多视角会诊：账号问题同时从订阅、支付、客户关系三个角度分析 |
| **SEQUENTIAL（顺序）** | Agent 按 `agentOrder` 依次执行，后者可以看到前者输出，最后一个输出为最终结果 | 流水线：先查数据 → 再分析 → 最后生成报告 |
| **DEBATE（辩论）** | 多轮辩论：每轮所有 Agent 并行分析，Moderator 判断是否收敛，未收敛则下轮修正观点 | 争议问题：多个专家讨论达成共识 |
| **HIERARCHICAL（层级）** | Leader 拆解任务 → 分派给 Worker 并行执行 → Leader 合成最终答案（三阶段） | 复杂任务：Leader 规划，Worker 干活，Leader 汇总 |

<!-- 截图占位：Agent Team 创建/编辑页面，展示拓扑下拉选择 -->
> 📸 *[截图4] Agent Team 管理页 — 左侧 Team 列表，右侧编辑表单*

<!-- 截图占位：添加 Agent 表单展开状态 -->
> 📸 *[截图5] 添加 Agent 表单 — role/目标/工具/策略/记忆/Lieder 配置*

#### 合并策略（随拓扑自动过滤）

| 策略 | 适用拓扑 | 行为 |
|------|----------|------|
| **SYNTHESIS（综合）** | PARALLEL | LLM 将多个 Agent 分析合并为一份自然回复 |
| **FIRST（优先）** | PARALLEL | 第一个成功的 Agent 输出为最终结果 |
| **CONSENSUS（共识）** | DEBATE | 辩论到所有 Agent 达成一致 |
| **MAJORITY（多数）** | DEBATE | 少数服从多数 |
| **LEADER（Leader决策）** | HIERARCHICAL | Leader 合成 Worker 输出 |

> 💡 切换拓扑时，合并策略下拉会自动过滤为有效选项并设置默认值。

#### 每个 Agent 的配置

| 配置项 | 说明 |
|--------|------|
| **Agent Key** | 唯一标识，如 `account_analyst` |
| **Agent 名称** | 显示名称，如"账号与订阅分析师" |
| **角色描述（System Prompt）** | 定义 Agent 的人设、专业领域和能力边界 |
| **目标** | Agent 的高层目标 |
| **执行策略** | Agent 内部如何思考：**ReAct**（思考-行动-观察循环）/ **Plan-Execute**（先规划再执行）/ **Pipeline**（直接调用工具） |
| **可用工具** | 从 MCP 工具网关中选择，支持多选。选中的工具会带上 inputSchema 传给 LLM |
| **记忆策略** | 对话记忆 / 摘要压缩 / 无记忆 |
| **模型 ID** | 可选覆盖默认模型 |
| **Leader 标记** | HIERARCHICAL 拓扑中需指定哪个是 Leader |
| **排序** | SEQUENTIAL 拓扑中的执行顺序 |

---

### 2.2 Workflow 可视化构建

**路径**：管理后台 → Workflow 管理 → **可视化构建**

这是系统的核心编排工具，分 4 步完成 Workflow 创建。

<!-- 截图占位：可视化构建器 Step 0 — 模板选择 -->
> 📸 *[截图6] Step 0 — 模板选择 + 已创建 Workflow 列表*

#### Step 0：选择起点

可以选择：
- **从模板开始**：工单分析 / ReAct推理 / Plan-Execute / 多Agent并行 / 分层记忆演示
- **从已有 Workflow 编辑**：列表中选择，点击加载
- **从空白创建**：所有参数自己配
- **🎬 一键创建VIP工单全流程Demo**：自动创建 Team + Workflow + 对话绑定

#### Step 1：节点编排

<!-- 截图占位：Step 1 — 节点编排 -->
> 📸 *[截图7] Step 1 — 基本信息表单 + 节点列表 + 每个节点的配置卡片*

先填写基本信息（名称、类型、状态、描述），然后添加和配置节点。

**顶部是可视化流转预览**：节点卡片一字排开，彩色标注节点类型，AGENT_TEAM 节点有特殊标记。

**每个节点可以配置**：

| 配置项 | 说明 |
|--------|------|
| **节点 Key** | 唯一标识 |
| **显示名** | 中文名称，如"📊 数据采集" |
| **节点类型** | 见下方节点类型详解 |
| **动作类型** | 当节点类型为 ACTION 时可选 |
| **执行策略** | 当动作类型为 NOOP 时可选 |
| **超时/重试** | 可选限制 |

**节点类型**：

| 类型 | 用途 |
|------|------|
| **ACTION（动作节点）** | 执行具体任务：调 MCP 工具、跑 Agent Team、LLM 推理等 |
| **EVALUATOR（评估节点）** | 验收上游节点的输出质量，不通过可触发 Reflection 回滚重试 |
| **CONDITION（条件分支）** | 根据条件走向不同节点 |
| **HUMAN_REVIEW（人工审核）** | 暂停等待人工介入 |
| **END（结束节点）** | 流程终点 |

**动作类型**（ACTION 节点可选）：

| 动作 | 说明 |
|------|------|
| MCP_TOOL | 调用 MCP 工具（可指定具体工具名和参数映射） |
| TICKET_ACCOUNT_ANALYSIS | 分析工单账号状态 |
| TICKET_TRIAGE | 工单初筛分派 |
| REACT_ANSWER_SUMMARY | ReAct 结果后处理 |
| PLAN_EXECUTE_SUMMARY | PAE 结果后处理 |
| NOOP | 无操作——此时配合策略使用 |

**执行策略**（NOOP 动作时可选）：

| 策略 | 说明 |
|------|------|
| **PIPELINE** | 直接调用 Action 执行器，一步完成 |
| **REACT** | LLM 思考→行动→观察→循环，支持多轮工具调用 |
| **PLAN_EXECUTE** | LLM 先制定计划→逐步执行→总结输出 |
| **AGENT_TEAM** | 🔥 多 Agent 协作——选择此策略后出现 Team 下拉选择器 |

<!-- 截图占位：节点类型下拉 + 策略下拉 + AGENT_TEAM 选 Team -->
> 📸 *[截图8] ACTION 节点的策略下拉 — 选 AGENT_TEAM 后出现 Team 选择器*

**EVALUATOR 节点**额外配置：

| 配置项 | 说明 |
|--------|------|
| 评估方式 | RULE（规则校验：检查必填字段+最小长度）或 LLM（LLM 评估输出质量） |
| 评估目标节点 | 从上游节点下拉选择 |
| 必填字段 | RULE 模式下，JSON 数组指定必须存在的字段名 |
| 重试节点 | 评估失败时回滚到哪个节点重试 |
| 最大反思轮数 | 最多回滚几次 |

#### Step 2：连线定义

<!-- 截图占位：Step 2 — 连线编辑 + 流转预览 -->
> 📸 *[截图9] Step 2 — 连线编辑（源节点→目标节点→边类型） + 流转预览图*

每条边定义节点间的流转关系：

| 配置项 | 说明 |
|--------|------|
| 源节点 / 目标节点 | 从已有节点下拉选择 |
| 边类型 | **DEFAULT**（无条件）/ **SUCCESS**（成功）/ **FAILED**（失败）/ **CONDITION**（条件匹配） |

底部实时显示流转预览。

#### Step 3：保存与运行

<!-- 截图占位：Step 3 — 保存 + 运行 + 历史记录 -->
> 📸 *[截图10] Step 3 — 保存 Workflow + 输入 JSON 运行 + 查看节点输出和运行历史*

填写测试输入（JSON），点击"运行 Workflow"，右侧实时显示每个节点的执行状态、输出和耗时。底部显示历史运行记录，点击可查看详情。

---

### 2.3 Workflow 全景视图

**路径**：管理后台 → Workflow 管理 → **全景视图**

<!-- 截图占位：全景视图 — 完整 DAG 流转 + AGENT_TEAM 展开 -->
> 📸 *[截图11] 全景视图 — 节点流转图 + AGENT_TEAM 展开显示每个 Agent 详情*

选择 Workflow 后展示：
- **横向流转图**：节点按 edges 推导演示序列排列，箭头标出边类型
- **AGENT_TEAM 展开**：点击展开后，在下方网格显示每个 Agent 的完整卡片（角色、策略、工具、记忆、Leader 标记）
- **节点详情卡片**：含上游/下游节点标注、完整配置 JSON
- **边定义面板**：每条边的源→类型→目标

---

### 2.4 Skill 管理

**路径**：管理后台 → Workflow 管理 → **Skill 管理**

结构化 Skill 是可复用的业务知识包，以 Markdown 文件形式存储在 `resources/skills/` 目录下。

<!-- 截图占位：Skill 管理页 — 列表 + 编辑器 + 进化建议审核面板 -->
> 📸 *[截图12] Skill 管理页 — 左侧 Skill 列表，右侧 SOP/规则/模板编辑器，底部进化建议审核面板*

**Skill 编辑区**包含：

| 字段 | 说明 |
|------|------|
| Skill Key / 名称 / 分类 | 基本标识 |
| 标签 | 逗号分隔，用于 LLM 语义匹配 |
| 工具绑定 | 从可用 MCP 工具中选择 |
| SOP（标准处理流程） | 该场景的步骤化操作指南 |
| 领域规则 | 场景特定的业务规则 |
| 提示词模板 | 给 Agent 的提示词模板 |
| 输出规范 | 回复的格式和质量要求 |

**🔄 重载按钮**：修改 `.md` 文件后点击即可重新扫描入库。

**进化建议审核**：系统在使用 Skill 后自动检测内容是否需要更新。管理员审核通过后自动合并到 Skill。

---

### 2.5 对话绑定

**路径**：管理后台 → Workflow 管理 → **对话绑定**

将一个 `optionKey` 映射到一个 Workflow。用户在聊天页选择 WORKFLOW 模式时，下拉列表中就是这个 optionKey。可配置提示词预设（`promptPresets`），用户在对话输入框下方点击即可快速填入。

---

## 三、用户端 — 对话体验

**路径**：`/chat` → 选择 WORKFLOW 模式 → 选择已绑定的 Workflow

<!-- 截图占位：对话页面 — 选择 WORKFLOW 模式 + 提示词模板 + 思考过程展开 -->
> 📸 *[截图13] 对话页面 — WORKFLOW 模式下拉 + 提示词模板 + 思考过程实时展开*

### 3.1 实时思考过程

用户提问后，系统不是"等待→一次性返回"，而是通过 **SSE（Server-Sent Events）** 实时推送每一步执行状态，用户可以看到：

```
📋 Workflow「VIP客户工单全流程处理」开始执行 · 4 个节点 · Harness: FLOW
▶️ 进入节点「📊 数据采集」· 动作: MCP_TOOL
✅ 节点「📊 数据采集」完成 (0.2s)
▶️ 进入节点「🤖 专家会诊」· 策略: Agent Team
📋 已匹配 Skill: vip_renewal_anomaly
🤖 Agent Team「VIP工单分析专家组」启动 · 3 Agent · 拓扑: PARALLEL · 合并: SYNTHESIS
🤖 账号与订阅分析师 开始分析...
🤖 支付与风控分析师 开始分析...
🤖 客户关系与SLA分析师 开始分析...
✅ 账号与订阅分析师 完成 (3.1s)
✅ 客户关系与SLA分析师 完成 (3.5s)
✅ 支付与风控分析师 完成 (4.2s)
✅ 节点「🤖 专家会诊」完成 (5.0s)
▶️ 进入节点「✅ 质量审核」· 类型: EVALUATOR
✅ 节点「✅ 质量审核」完成 (0.1s)
```

**思考过程区域特性**：
- 运行时默认展开（`open`），完成后可折叠
- 自动滚到底部（`scrollTop = scrollHeight`），最新状态始终可见
- 状态日志持久化存储，刷新不丢失

**不同拓扑的定制化状态**：

| 拓扑 | 特有状态消息 |
|------|-------------|
| DEBATE | `📢 辩论第N/M轮开始...` |
| HIERARCHICAL | `👑 Phase 1/3: Leader 拆解` / `⚡ Phase 2/3: Worker 并行` / `🧠 Phase 3/3: Leader 合成` |
| SEQUENTIAL | `▶️ 第N/M步: Agent X 开始分析...` / `✅ 第N/M步: Agent X 完成` |
| PARALLEL | 各 Agent 启动/完成消息自然展示并行执行情况 |

### 3.2 流式打字效果

回复内容不是一次全部显示，而是逐字逐块输出，模拟真实打字。每块约 15 字符，间隔 40ms。

### 3.3 Skill 自动匹配

用户提问时，系统自动从 Skill 库匹配最佳 Skill（LLM 语义 + 标签匹配），在思考过程中显示 `📋 已匹配 Skill: xxx`，并将 Skill 的 SOP、领域规则和提示词模板注入 Agent 执行上下文。

### 3.4 内容安全过滤

所有输出内容经过全局过滤器，自动清理：

- JSON 代码块 → `[已过滤JSON代码块]`
- API Key / Token / 密码等凭证 → `[已过滤敏感凭证]`
- 手机号 → `138****5678`
- 身份证号 → `330102********1234`
- 邮箱 → `[邮箱]`

---

## 四、底层架构能力

### Workflow 引擎

- **DAG 模型**：节点 + 边组成有向无环图，支持 8 种边类型（DEFAULT/SUCCESS/FAILED/CONDITION/EVALUATION_PASS/EVALUATION_FAIL/REVIEW_APPROVED/REVIEW_REJECTED）
- **Checkpoint 回滚**：Evaluator 不通过时自动回滚到检查点，重试被评估节点
- **条件分支**：支持 SpEL 表达式判断条件走向
- **分层记忆**：短期上下文 / 事件记忆 / 任务摘要 / 长期压缩，异步增量执行

### 模型路由与容错

- 三态熔断器（CLOSED → OPEN → HALF_OPEN），每个模型独立健康状态
- 首包探测：缓冲流式事件，切换到备用模型时用户无感知
- 多 Provider 支持：Ollama / SiliconFlow / BaiLian + 通用 OpenAI 兼容

### MCP 工具网关

- 统一注册表（`McpToolRegistry`）：本地 `@Component` 工具 + 远程 MCP Server 工具统一管理
- LLM 参数提取（`LLMMcpParameterExtractor`）：自然语言 → 工具参数自动转换
- Streamable HTTP 传输

---

## 五、演示数据与快速体验

### 一键创建 Demo

打开管理后台 → Workflow 管理 → 可视化构建 → 点击 **"🎬 一键创建VIP工单全流程Demo"**。

自动完成三件事：
1. 创建 **Agent Team**（3 个专家：账号分析 / 支付风控 / 客户关系，PARALLEL 拓扑）
2. 创建 **Workflow**（4 个节点：数据采集 → 专家会诊 → 质量审核 → 结束）
3. 创建 **对话绑定**（5 条提示词模板）

### 演示数据

数据库包含 **12 个账号**、**14 条支付记录**、**14 条工单**，覆盖 6 种业务场景：

| 场景 | 测试账号 | 提示词模板 |
|------|----------|-----------|
| VIP 续费异常（风控拦截） | A10004 | "我是北京蓝图创新科技有限公司（A10004）..." |
| 权限异常排查 | A10003 | "我是深圳矩阵科技有限公司（A10003），30人无法访问 DevOps 模块..." |
| 安全事件应急 | A10008 | "紧急！我是楚天信息安全（A10008），账号被黑客入侵..." |
| 试用升级咨询 | A10007 | "我是南京星辰网络科技（A10007），试用版快到期..." |
| 账单争议处理 | A10005 | "我是成都天域云计算（A10005），账号被暂停还扣了2999元..." |
| SLA 超时投诉 | A10011 | "我是厦门海丝金融（A10011），3天前的工单没人回复..." |

### 预置 Skill

| Skill | 场景 | 关键词 |
|-------|------|--------|
| `vip_renewal_anomaly` | VIP续费异常处理 | 续费、自动续费、过期、风控 |
| `permission_troubleshooting` | 权限异常排查 | 403、无权限、无法访问、RBAC |
| `security_incident_response` | 安全事件应急 | 黑客、异常登录、被入侵、冻结 |
| `trial_upgrade_guidance` | 试用升级引导 | 试用、升级、版本、价格 |
| `billing_dispute_resolution` | 账单争议处理 | 账单、扣费、退款、金额 |
| `sla_complaint_handling` | SLA投诉处理 | 投诉、超时、没人回复、赔偿 |

---

## 六、技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17, Spring Boot 3.5.7, Maven 多模块 |
| 前端 | React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui, Zustand |
| ORM | MyBatis-Plus |
| 数据库 | PostgreSQL + pgvector |
| 向量库 | Milvus 2.6.6 |
| 缓存/分布式 | Redis, Redisson（锁/信号量）, RocketMQ |
| 认证 | Sa-Token（Redis 会话） |
| 文档解析 | Apache Tika + Markdown |
| 模型路由 | 多 Provider（Ollama/SiliconFlow/BaiLian），三态熔断，首包探测，优先级降级 |
| MCP | io.modelcontextprotocol.sdk（本地 + 远程工具），Streamable HTTP 传输 |
| 上下文透传 | Alibaba TransmittableThreadLocal + 10 专用线程池 |
| 流式推送 | SseEmitter + SSE Event 分级（meta/message/status/finish/done） |

---

## 七、快速启动

### 环境要求
- JDK 17+, Maven 3.6+, PostgreSQL 14+（pgvector）, Redis, Node.js 18+

### 后端
```bash
# 1. 运行数据库迁移（按顺序执行）
psql -h localhost -U postgres -d ragent -f resources/database/schema_pg.sql
psql -h localhost -U postgres -d ragent -f resources/database/upgrade_v1.2_to_v1.3_agent_workflow.sql
psql -h localhost -U postgres -d ragent -f resources/database/upgrade_v1.3_to_v1.4_multi_agent.sql
psql -h localhost -U postgres -d ragent -f resources/database/upgrade_v1.4_to_v1.5_skill.sql
psql -h localhost -U postgres -d ragent -f resources/database/upgrade_v1.5_to_v1.6_demo_data_plus.sql

# 2. 配置 application.yml（数据库/Redis/模型）

# 3. 编译运行
mvn clean compile && mvn spring-boot:run -pl bootstrap
```

### 前端
```bash
cd frontend && npm install && npm run dev
```

### 验证
1. 访问 `http://localhost:5173/admin` → 可视化构建 → 点"🎬 一键创建VIP工单全流程Demo"
2. 访问 `http://localhost:5173/chat` → WORKFLOW 模式 → 选"VIP工单全流程 Demo" → 点提示词测试

---

## 八、项目结构

```
ragent/
├── bootstrap/                    # 主应用
│   └── src/main/java/.../
│       ├── agent/
│       │   ├── multiagent/       # 🆕 Multi-Agent 引擎
│       │   │   ├── core/         # AgentRunner, Orchestrator, Blackboard, MergeEngine
│       │   │   ├── topology/     # 4 种拓扑策略
│       │   │   └── dao/          # Team/Agent/Record/Memory/Blackboard
│       │   ├── skill/            # 🆕 结构化 Skill
│       │   │   ├── core/         # SkillLoader, SkillService, SkillEvolutionEvaluator
│       │   │   └── controller/   # Skill CRUD + 建议审核 API
│       │   └── workflow/         # Workflow 引擎
│       └── rag/service/
│           ├── workflow/         # WorkflowChatRouter（Skill 匹配 + SSE 回调）
│           └── filter/           # ContentFilter（安全过滤）
├── framework/                    # 基础设施（Snowflake ID, Sa-Token, TTL, SSE）
├── infra-ai/                     # AI Provider（Chat/Embedding/Rerank, 熔断路由）
├── frontend/                     # React 前端
│   └── src/
│       ├── pages/admin/
│       │   ├── workflows/        # WorkflowBuilder, WorkflowOverview, AgentTeamManage
│       │   └── skills/           # SkillManagePage
│       ├── components/chat/      # MessageItem, ChatInput, MarkdownRenderer
│       ├── services/             # multiAgentService, skillService, workflowService
│       ├── stores/               # chatStore（Zustand）
│       └── hooks/                # useStreamResponse（SSE 解析）
├── resources/
│   ├── database/                 # SQL 迁移脚本
│   └── skills/                   # Skill .md 定义文件
└── mcp-server/                   # MCP 演示工具（sales_query, ticket_query, weather_query）
```

---

## 九、许可证

本项目基于 [Ragent AI](https://github.com/nageoffer/ragent)（© nageoffer, Apache License 2.0）进行二次开发。原创部分同样以 [Apache License 2.0](./LICENSE) 开源。

---

## 联系方式

- 在线体验：http://liuguangyf.top
- 代码仓库：https://github.com/Swing-G/LiuGuang
- 原始项目：https://github.com/nageoffer/ragent
