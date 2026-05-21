/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.agent.action.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.action.domain.ActionConfig;
import com.nageoffer.ai.ragent.agent.action.domain.ActionContext;
import com.nageoffer.ai.ragent.agent.action.domain.ActionResult;
import com.nageoffer.ai.ragent.agent.action.enums.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工单分诊动作执行器
 */
@Component
@RequiredArgsConstructor
public class TicketTriageActionExecutor implements AgentActionExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public String actionType() {
        return ActionType.TICKET_TRIAGE.name();
    }

    @Override
    public ActionResult execute(ActionContext context, ActionConfig config) {
        String ticketId = readText(context, "ticketId", "T-UNKNOWN");
        String content = readText(context, "content", readText(context, "question", ""));
        String riskLevel = resolveRiskLevel(content, ticketId);
        String category = resolveCategory(content);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("ticketId", ticketId);
        output.put("category", category);
        output.put("riskLevel", riskLevel);
        output.put("ownerTeam", "售后支持");
        output.put("summary", buildSummary(ticketId, category, riskLevel, content));
        output.put("suggestion", buildSuggestion(category, riskLevel));
        output.put("customerReply", buildCustomerReply(ticketId, category, riskLevel));
        return ActionResult.success(output);
    }

    private String readText(ActionContext context, String field, String defaultValue) {
        String value = context.getInput() == null ? null : context.getInput().path(field).asText(null);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String resolveRiskLevel(String content, String ticketId) {
        String text = content == null ? "" : content;
        if (ticketId.endsWith("001") || text.contains("投诉") || text.contains("赔偿") || text.contains("升级")) {
            return "HIGH";
        }
        if (text.contains("无法") || text.contains("失败") || text.contains("异常")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveCategory(String content) {
        String text = content == null ? "" : content;
        if (text.contains("登录") || text.contains("账号")) return "账号访问";
        if (text.contains("续费") || text.contains("支付") || text.contains("订单")) return "支付续费";
        if (text.contains("发票") || text.contains("报销")) return "票据财务";
        if (text.contains("投诉") || text.contains("赔偿")) return "客户投诉";
        return "通用咨询";
    }

    private String buildSummary(String ticketId, String category, String riskLevel, String content) {
        String normalized = StringUtils.hasText(content) ? content : "用户未提供详细描述";
        return "工单 " + ticketId + " 已归类为「" + category + "」，风险等级为 " + riskLevel + "。原始诉求：" + normalized;
    }

    private String buildSuggestion(String category, String riskLevel) {
        String priority = "HIGH".equals(riskLevel) ? "优先升级处理" : "按标准服务流程跟进";
        return priority + "；先确认用户身份和关键业务状态，再按「" + category + "」知识库口径给出处理步骤，必要时补充责任团队与承诺时限。";
    }

    private String buildCustomerReply(String ticketId, String category, String riskLevel) {
        String prefix = "HIGH".equals(riskLevel) ? "我们已将问题标记为高优先级" : "我们已收到并完成初步分析";
        return prefix + "。工单 " + ticketId + " 属于「" + category + "」场景，建议先核对账号、订单或业务状态，随后由售后支持团队继续跟进。";
    }
}
