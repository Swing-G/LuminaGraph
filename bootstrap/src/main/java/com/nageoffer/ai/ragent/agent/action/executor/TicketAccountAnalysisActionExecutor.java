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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.action.domain.ActionConfig;
import com.nageoffer.ai.ragent.agent.action.domain.ActionContext;
import com.nageoffer.ai.ragent.agent.action.domain.ActionResult;
import com.nageoffer.ai.ragent.agent.action.enums.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 工单账号状态分析动作执行器
 */
@Component
@RequiredArgsConstructor
public class TicketAccountAnalysisActionExecutor implements AgentActionExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public String actionType() {
        return ActionType.TICKET_ACCOUNT_ANALYSIS.name();
    }

    @Override
    public ActionResult execute(ActionContext context, ActionConfig config) {
        JsonNode source = resolveSource(context, config);
        if (source == null || source.isMissingNode() || source.isNull()) {
            return ActionResult.fail("缺少可分析的账号工单数据");
        }
        if (source.path("isError").asBoolean(false)) {
            return ActionResult.fail("账号工单查询失败: " + source.path("content").toString());
        }
        JsonNode data = source.path("structuredContent").isMissingNode() ? source : source.path("structuredContent");

        String ticketId = text(data, "ticket_id", text(data, "ticketId", "-"));
        String accountId = text(data, "account_id", text(data, "accountId", "-"));
        String customerName = text(data, "customer_name", "未知客户");
        String accountStatus = text(data, "account_status", "UNKNOWN");
        String subscriptionStatus = text(data, "subscription_status", "UNKNOWN");
        String payStatus = text(data, "pay_status", "UNKNOWN");
        String failureReason = text(data, "failure_reason", "-");
        String ticketStatus = text(data, "ticket_status", "UNKNOWN");
        String priority = text(data, "priority", "P2");
        String subject = text(data, "subject", "用户账号疑问");
        String latestNote = text(data, "latest_note", "暂无处理记录");
        boolean riskFlag = data.path("risk_flag").asBoolean(false);
        boolean autoRenewEnabled = data.path("auto_renew_enabled").asBoolean(false);
        BigDecimal amount = decimal(data, "amount");
        BigDecimal dueAmount = decimal(data, "renewal_due_amount");

        String riskLevel = resolveRiskLevel(priority, riskFlag, payStatus, subscriptionStatus, ticketStatus);
        String rootCause = resolveRootCause(payStatus, subscriptionStatus, failureReason, autoRenewEnabled);
        String currentState = buildCurrentState(accountStatus, subscriptionStatus, payStatus, ticketStatus, failureReason);
        String suggestion = buildSuggestion(riskLevel, rootCause, autoRenewEnabled, dueAmount, amount);
        String customerReply = buildCustomerReply(customerName, ticketId, rootCause, suggestion);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("ticketId", ticketId);
        output.put("accountId", accountId);
        output.put("customerName", customerName);
        output.put("subject", subject);
        output.put("riskLevel", riskLevel);
        output.put("rootCause", rootCause);
        output.put("currentState", currentState);
        output.put("latestNote", latestNote);
        output.put("suggestion", suggestion);
        output.put("customerReply", customerReply);
        output.set("sourceData", data);
        return ActionResult.success(output);
    }

    private JsonNode resolveSource(ActionContext context, ActionConfig config) {
        String sourceNodeKey = config.getConfig() == null ? "queryAccountTicket" : config.getConfig().path("sourceNodeKey").asText("queryAccountTicket");
        JsonNode byNode = context.getContext() == null ? null : context.getContext().path(sourceNodeKey);
        if (byNode != null && !byNode.isMissingNode() && !byNode.isNull()) {
            return byNode;
        }
        return context.getContext() == null ? null : context.getContext().path("lastOutput");
    }

    private String resolveRiskLevel(String priority, boolean riskFlag, String payStatus, String subscriptionStatus, String ticketStatus) {
        if (riskFlag || "P0".equalsIgnoreCase(priority) || "P1".equalsIgnoreCase(priority)) {
            return "HIGH";
        }
        if ("FAILED".equalsIgnoreCase(payStatus) || "EXPIRED".equalsIgnoreCase(subscriptionStatus) || "OPEN".equalsIgnoreCase(ticketStatus)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String resolveRootCause(String payStatus, String subscriptionStatus, String failureReason, boolean autoRenewEnabled) {
        if ("FAILED".equalsIgnoreCase(payStatus)) {
            return "最近一次续费支付失败，失败原因为「" + failureReason + "」";
        }
        if ("EXPIRED".equalsIgnoreCase(subscriptionStatus)) {
            return "订阅已过期，账号权益受限";
        }
        if (!autoRenewEnabled) {
            return "账号未开启自动续费，需要用户主动完成续费";
        }
        return "账号与订阅状态未发现阻断项，需要结合用户补充信息继续核实";
    }

    private String buildCurrentState(String accountStatus, String subscriptionStatus, String payStatus, String ticketStatus, String failureReason) {
        return "账号状态=" + accountStatus + "，订阅状态=" + subscriptionStatus + "，最近支付状态=" + payStatus + "，工单状态=" + ticketStatus + "，支付失败原因=" + failureReason + "。";
    }

    private String buildSuggestion(String riskLevel, String rootCause, boolean autoRenewEnabled, BigDecimal dueAmount, BigDecimal amount) {
        String priority = "HIGH".equals(riskLevel) ? "先升级到售后支持负责人，承诺处理时限并持续同步" : "按标准售后流程跟进";
        String money = dueAmount.compareTo(BigDecimal.ZERO) > 0 ? "待续费金额 " + dueAmount + " 元" : "最近支付金额 " + amount + " 元";
        String renew = autoRenewEnabled ? "检查扣款渠道、余额或银行风控状态" : "引导用户重新开启自动续费或手动支付";
        return priority + "；当前判断：" + rootCause + "；建议动作：" + renew + "，核对 " + money + "，必要时补偿一次人工续期并保留回访记录。";
    }

    private String buildCustomerReply(String customerName, String ticketId, String rootCause, String suggestion) {
        return customerName + "，我们已核实工单 " + ticketId + " 的账号与续费状态。当前问题主要是：" + rootCause + "。" + suggestion;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(node.path(field).asText("0"));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }
}
