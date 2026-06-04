---
id: sla_complaint_handling
name: SLA投诉升级处理
version: 1.0.0
category: customer_success
tags: [SLA, 投诉, 超时, VIP, 补偿, 升级]
tools: [ticket.account.query]
status: enabled
---

# SLA投诉升级处理

## 适用范围
VIP/企业客户投诉工单响应超时、SLA未达标、要求赔偿或升级处理。

## SOP
1. 查询账号信息 — 确认客户等级和 SLA 承诺标准
2. 查询投诉工单 — 确认原始工单的创建时间、首次响应时间、当前状态
3. 确认 SLA 违约事实 — 计算超时时长
4. 输出道歉、解释原因、补救方案和补偿措施

## 领域规则
- VIP SLA: 2小时内首次响应，4小时内给出方案
- Enterprise SLA: 4小时内首次响应，8小时内给出方案
- Standard SLA: 8小时内首次响应，24小时内给出方案
- SLA 违约补偿: VIP 赔偿当月费用10% / Enterprise 5%
- 确认为系统故障 (路由异常等) → 不向客户解释技术细节，道歉+补偿即可

## 提示词模板
VIP 客户 {accountId} 投诉 SLA 超时。请确认客户等级、原始工单详情和违约事实，给出处理方案。

## 输出规范
- 真诚道歉，不推卸责任
- 简要解释超时原因（技术原因简化表述）
- 明确补偿措施
- 告知当前处理进展
- 提供升级联系人
