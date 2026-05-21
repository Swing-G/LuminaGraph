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

package com.nageoffer.ai.ragent.agent.memory.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.memory.core.WorkflowMemoryStrategy;
import com.nageoffer.ai.ragent.agent.memory.domain.WorkflowMemoryContext;
import com.nageoffer.ai.ragent.agent.memory.domain.WorkflowMemoryResult;
import com.nageoffer.ai.ragent.agent.memory.enums.WorkflowMemoryStrategyType;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowEventDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowMemorySummaryDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowEventMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowMemorySummaryMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 分层Workflow记忆策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayeredWorkflowMemoryStrategy implements WorkflowMemoryStrategy {

    private static final int SUMMARY_MAX_CHARS = 600;
    private static final int IMPORTANCE_THRESHOLD = 70;
    private static final int RECENT_NODE_OUTPUT_LIMIT = 3;

    private final AgentWorkflowEventMapper eventMapper;
    private final AgentWorkflowMemorySummaryMapper memorySummaryMapper;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public String strategyType() {
        return WorkflowMemoryStrategyType.LAYERED.name();
    }

    @Override
    public WorkflowMemoryResult compress(WorkflowMemoryContext memoryContext) {
        ObjectNode context = memoryContext.getWorkflowContext();
        normalizeShortTermContext(context, memoryContext.getInput());
        List<AgentWorkflowEventDO> events = loadEvents(memoryContext.getInstance().getId());
        if (events.isEmpty()) {
            return WorkflowMemoryResult.builder().compressed(false).eventCount(0).build();
        }
        ArrayNode importantEvents = buildImportantEvents(events);
        ObjectNode compressedContext = buildCompressedContext(context, importantEvents);
        String previousSummary = loadPreviousSummary(memoryContext.getInstance().getId());
        String summary = summarize(previousSummary, compressedContext);
        saveSummary(memoryContext, summary, importantEvents, compressedContext, events);
        ObjectNode memoryNode = objectMapper.createObjectNode();
        memoryNode.put("strategyType", strategyType());
        memoryNode.put("summary", summary);
        memoryNode.set("highImportanceEvents", importantEvents);
        memoryNode.set("compressedContext", compressedContext);
        context.set("workflowMemory", memoryNode);
        return WorkflowMemoryResult.builder()
                .compressed(true)
                .summary(summary)
                .highImportanceEvents(importantEvents)
                .compressedContext(compressedContext)
                .eventCount(events.size())
                .build();
    }

    private void normalizeShortTermContext(ObjectNode context, JsonNode input) {
        if (!context.has("input")) {
            context.set("input", input == null ? objectMapper.createObjectNode() : input);
        }
        if (!context.has("variables")) {
            context.set("variables", objectMapper.createObjectNode());
        }
        if (!context.has("lastOutput")) {
            context.set("lastOutput", objectMapper.createObjectNode());
        }
        if (!context.has("reflectionHints")) {
            context.set("reflectionHints", objectMapper.createArrayNode());
        }
        if (!context.has("toolResults")) {
            context.set("toolResults", objectMapper.createObjectNode());
        }
        if (!context.has("retrievedContext")) {
            context.put("retrievedContext", "");
        }
    }

    private List<AgentWorkflowEventDO> loadEvents(String instanceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<AgentWorkflowEventDO>()
                        .eq(AgentWorkflowEventDO::getInstanceId, instanceId)
                        .orderByAsc(AgentWorkflowEventDO::getCreateTime))
                .stream()
                .peek(event -> event.setImportanceScore(resolveImportance(event)))
                .toList();
    }

    private ArrayNode buildImportantEvents(List<AgentWorkflowEventDO> events) {
        ArrayNode result = objectMapper.createArrayNode();
        events.stream()
                .filter(event -> resolveImportance(event) >= IMPORTANCE_THRESHOLD)
                .forEach(event -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("eventType", event.getEventType());
                    node.put("eventLevel", event.getEventLevel());
                    node.put("importance", resolveImportance(event));
                    node.put("content", event.getContent());
                    if (StringUtils.hasText(event.getPayloadJson())) {
                        node.set("payload", parse(event.getPayloadJson()));
                    }
                    result.add(node);
                });
        return result;
    }

    private ObjectNode buildCompressedContext(ObjectNode context, ArrayNode importantEvents) {
        ObjectNode compressed = objectMapper.createObjectNode();
        compressed.set("shortTermContext", cleanShortTermContext(context));
        compressed.set("highImportanceEvents", importantEvents);
        compressed.set("recentNodeOutputs", recentNodeOutputs(context));
        return compressed;
    }

    private ObjectNode cleanShortTermContext(ObjectNode context) {
        ObjectNode shortTerm = objectMapper.createObjectNode();
        copyIfPresent(context, shortTerm, "input");
        copyIfPresent(context, shortTerm, "variables");
        copyIfPresent(context, shortTerm, "lastOutput");
        copyIfPresent(context, shortTerm, "reflectionHints");
        copyIfPresent(context, shortTerm, "toolResults");
        copyIfPresent(context, shortTerm, "retrievedContext");
        return shortTerm;
    }

    private void copyIfPresent(ObjectNode source, ObjectNode target, String fieldName) {
        if (source.has(fieldName)) {
            target.set(fieldName, source.get(fieldName));
        }
    }

    private ObjectNode recentNodeOutputs(ObjectNode context) {
        ObjectNode outputs = objectMapper.createObjectNode();
        List<String> fieldNames = context.properties().stream()
                .map(Map.Entry::getKey)
                .filter(key -> !List.of("input", "variables", "lastOutput", "reflectionHints", "toolResults", "retrievedContext", "workflowMemory").contains(key))
                .toList();
        int start = Math.max(0, fieldNames.size() - RECENT_NODE_OUTPUT_LIMIT);
        for (String key : fieldNames.subList(start, fieldNames.size())) {
            outputs.set(key, context.get(key));
        }
        return outputs;
    }

    private String loadPreviousSummary(String instanceId) {
        return memorySummaryMapper.selectList(new LambdaQueryWrapper<AgentWorkflowMemorySummaryDO>()
                        .eq(AgentWorkflowMemorySummaryDO::getInstanceId, instanceId)
                        .eq(AgentWorkflowMemorySummaryDO::getStrategyType, strategyType())
                        .orderByDesc(AgentWorkflowMemorySummaryDO::getCreateTime))
                .stream()
                .findFirst()
                .map(AgentWorkflowMemorySummaryDO::getSummaryContent)
                .orElse("");
    }

    private String summarize(String previousSummary, ObjectNode compressedContext) {
        String prompt = "你是Workflow任务记忆摘要器。请融合历史任务摘要、短期上下文、高重要性事件与最近节点输出，生成任务级摘要。"
                + "要求：只保留影响后续任务执行的事实、决策、失败原因、反思提示和任务状态；不要复述低价值日志；严格不超过"
                + SUMMARY_MAX_CHARS + "字；只输出一段中文摘要。\n"
                + "历史摘要：\n" + (StringUtils.hasText(previousSummary) ? previousSummary : "无") + "\n"
                + "压缩上下文：\n" + compressedContext.toPrettyString();
        try {
            return llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.3D)
                    .topP(0.8D)
                    .thinking(false)
                    .build());
        } catch (Exception ex) {
            log.error("Workflow任务记忆摘要生成失败", ex);
            return StringUtils.hasText(previousSummary) ? previousSummary : fallbackSummary(compressedContext);
        }
    }

    private String fallbackSummary(ObjectNode compressedContext) {
        JsonNode input = compressedContext.path("shortTermContext").path("input");
        int importantEventCount = compressedContext.path("highImportanceEvents").size();
        int recentOutputCount = compressedContext.path("recentNodeOutputs").size();
        return "Workflow任务级记忆已压缩：保留原始输入" + input.toString()
                + "，高重要性事件" + importantEventCount
                + "条，最近节点输出" + recentOutputCount
                + "个；后续节点可基于该摘要、关键事件和短期上下文继续执行。";
    }

    private void saveSummary(WorkflowMemoryContext context, String summary, ArrayNode importantEvents, ObjectNode compressedContext, List<AgentWorkflowEventDO> events) {
        Date lastEventTime = events.stream()
                .map(AgentWorkflowEventDO::getCreateTime)
                .max(Comparator.naturalOrder())
                .orElse(new Date());
        memorySummaryMapper.insert(AgentWorkflowMemorySummaryDO.builder()
                .instanceId(context.getInstance().getId())
                .workflowId(context.getInstance().getWorkflowId())
                .strategyType(strategyType())
                .summaryContent(summary)
                .highImportanceEvents(importantEvents.toString())
                .compressedContext(compressedContext.toString())
                .lastEventTime(lastEventTime)
                .eventCount(events.size())
                .build());
    }

    private int resolveImportance(AgentWorkflowEventDO event) {
        if (event.getImportanceScore() != null && event.getImportanceScore() >= IMPORTANCE_THRESHOLD) {
            return event.getImportanceScore();
        }
        return switch (event.getEventType()) {
            case "USER_INPUT" -> 90;
            case "TOOL_RESULT" -> 80;
            case "EVALUATION_FAILED", "REFLECTION_TRIGGERED" -> 85;
            case "WORKFLOW_ROLLBACK", "REVIEW_REQUIRED" -> 80;
            case "NODE_COMPLETED" -> 60;
            case "DEBUG_LOG" -> 20;
            default -> event.getImportanceScore() == null ? 50 : event.getImportanceScore();
        };
    }

    private JsonNode parse(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode().put("raw", raw);
        }
    }
}
