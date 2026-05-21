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

package com.nageoffer.ai.ragent.rag.service.workflow;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowRunRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowInstanceVO;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowVO;
import com.nageoffer.ai.ragent.agent.workflow.service.AgentWorkflowService;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 对话式Workflow路由器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowChatRouter {

    private static final String TICKET_TRIAGE_WORKFLOW_TYPE = "ticket_triage_chat";

    private final AgentWorkflowService workflowService;
    private final ConversationMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public boolean handle(String question, String conversationId, String userId, StreamCallback callback) {
        AgentWorkflowVO workflow = workflowService.findEnabledByType(TICKET_TRIAGE_WORKFLOW_TYPE);
        if (workflow == null) {
            callback.onContent("当前未配置可用的工单处理 Workflow。请先在 Workflow 管理页创建并启用 workflowType=" + TICKET_TRIAGE_WORKFLOW_TYPE + " 的流程。");
            callback.onComplete();
            return true;
        }
        memoryService.append(conversationId, userId, ChatMessage.user(question));
        try {
            AgentWorkflowRunRequest request = new AgentWorkflowRunRequest();
            request.setBusinessType("chat_ticket_triage");
            request.setBusinessId("chat-ticket-" + IdUtil.getSnowflakeNextIdStr());
            request.setInput(buildInput(question, userId));
            AgentWorkflowInstanceVO instance = workflowService.run(workflow.getId(), request);
            callback.onContent(toNaturalLanguage(instance));
            callback.onComplete();
            return true;
        } catch (Exception ex) {
            log.error("对话式Workflow执行失败。workflowId={}", workflow.getId(), ex);
            callback.onContent("工单处理 Workflow 执行失败，请稍后重试。失败原因：" + ex.getMessage());
            callback.onComplete();
            return true;
        }
    }

    public boolean tryHandle(String question, String conversationId, String userId, StreamCallback callback) {
        if (!matchTicketTriage(question)) {
            return false;
        }
        AgentWorkflowVO workflow = workflowService.findEnabledByType(TICKET_TRIAGE_WORKFLOW_TYPE);
        if (workflow == null) {
            log.info("未配置可用工单处理Workflow，回退普通RAG。workflowType={}", TICKET_TRIAGE_WORKFLOW_TYPE);
            return false;
        }
        memoryService.append(conversationId, userId, ChatMessage.user(question));
        try {
            AgentWorkflowRunRequest request = new AgentWorkflowRunRequest();
            request.setBusinessType("chat_ticket_triage");
            request.setBusinessId("chat-ticket-" + IdUtil.getSnowflakeNextIdStr());
            request.setInput(buildInput(question, userId));
            AgentWorkflowInstanceVO instance = workflowService.run(workflow.getId(), request);
            callback.onContent(toNaturalLanguage(instance));
            callback.onComplete();
            return true;
        } catch (Exception ex) {
            log.error("对话式Workflow执行失败，回退错误响应。workflowId={}", workflow.getId(), ex);
            callback.onContent("工单处理 Workflow 执行失败，请稍后重试或切换为普通问答。失败原因：" + ex.getMessage());
            callback.onComplete();
            return true;
        }
    }

    private boolean matchTicketTriage(String question) {
        if (!StringUtils.hasText(question)) return false;
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("workflow")
                || question.contains("工单")
                || question.contains("投诉")
                || question.contains("客诉")
                || question.contains("处理建议")
                || question.contains("分诊");
    }

    private ObjectNode buildInput(String question, String userId) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("question", question);
        input.put("content", question);
        input.put("ticketId", extractTicketId(question));
        input.put("accountId", extractAccountId(question));
        if (StringUtils.hasText(userId)) {
            input.put("operatorUserId", userId);
        }
        input.put("source", "chat");
        return input;
    }

    private String extractTicketId(String question) {
        if (!StringUtils.hasText(question)) return "CHAT-TICKET";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[Tt][-_]?[0-9]{6,}").matcher(question);
        if (matcher.find()) {
            return matcher.group().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        }
        return "T20260520001";
    }

    private String extractAccountId(String question) {
        if (!StringUtils.hasText(question)) return "A10001";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[Aa][-_]?[0-9]{4,}").matcher(question);
        if (matcher.find()) {
            return matcher.group().toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        }
        return "A10001";
    }

    private String toNaturalLanguage(AgentWorkflowInstanceVO instance) {
        ObjectNode context = instance.getContext() != null && instance.getContext().isObject()
                ? (ObjectNode) instance.getContext()
                : objectMapper.createObjectNode();
        JsonNode analysis = context.path("analyzeAccountTicket");
        if (analysis.isMissingNode() || analysis.isNull() || analysis.isEmpty()) {
            analysis = context.path("triageTicket");
        }
        String ticketId = analysis.path("ticketId").asText(context.path("input").path("ticketId").asText("-"));
        String accountId = analysis.path("accountId").asText(context.path("input").path("accountId").asText("-"));
        String riskLevel = analysis.path("riskLevel").asText("UNKNOWN");
        String summary = analysis.path("currentState").asText(analysis.path("summary").asText("已完成初步分析。"));
        String rootCause = analysis.path("rootCause").asText("");
        String suggestion = analysis.path("suggestion").asText("建议继续补充信息后由人工跟进。");

        String latestNote = analysis.path("latestNote").asText("");


        StringBuilder reply = new StringBuilder();
        reply.append("已按工单处理 Workflow 完成分析。\n\n");
        reply.append("- 工单编号：").append(ticketId).append('\n');
        reply.append("- 账号编号：").append(accountId).append('\n');
        reply.append("- 风险等级：").append(riskLevel).append("\n\n");
        reply.append("处理摘要：\n").append(summary).append("\n\n");
        reply.append("处理建议：\n").append(suggestion).append("\n\n");
        reply.append("可回复用户的话术：\n").append(customerReply);
        if (StringUtils.hasText(latestNote)) {
            reply.append("\n\n最新处理记录：\n").append(latestNote);
        }
        if (StringUtils.hasText(memorySummary)) {
            reply.append("\n\n任务记忆摘要：\n").append(memorySummary);
        }
        return reply.toString();
    }
}
