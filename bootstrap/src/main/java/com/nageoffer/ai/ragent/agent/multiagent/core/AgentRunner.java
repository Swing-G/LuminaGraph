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

package com.nageoffer.ai.ragent.agent.multiagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.multiagent.dao.entity.AgentMemoryDO;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentConfig;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionContext;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionResult;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent运行器
 * <p>
 * 负责单个Agent的完整执行循环：
 * 1. 构建System Prompt（role + goal + tool描述）
 * 2. 加载Agent记忆
 * 3. 读取Blackboard上下文
 * 4. 调用LLM
 * 5. 解析工具调用并执行
 * 6. 返回结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunner {

    private final LLMService llmService;
    private final McpToolRegistry mcpToolRegistry;
    private final AgentMemoryService agentMemoryService;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /** TTL 透传的 SSE 回调，由 WorkflowChatRouter 设置 */
    private static final com.alibaba.ttl.TransmittableThreadLocal<com.nageoffer.ai.ragent.infra.chat.StreamCallback> STATUS_CALLBACK =
            new com.alibaba.ttl.TransmittableThreadLocal<>();

    public static com.alibaba.ttl.TransmittableThreadLocal<com.nageoffer.ai.ragent.infra.chat.StreamCallback> getStatusCallback() {
        return STATUS_CALLBACK;
    }

    private void reportStatus(String msg) {
        com.nageoffer.ai.ragent.infra.chat.StreamCallback cb = STATUS_CALLBACK.get();
        if (cb != null) cb.onStatus(msg);
    }

    /**
     * 执行单个Agent
     * 根据 Agent 配置的 strategyType（默认 REACT）选择对应的 NodeExecutionStrategy
     */
    public AgentExecutionResult run(AgentExecutionContext context) {
        long startTime = System.currentTimeMillis();
        AgentConfig config = context.getAgentConfig();
        String strategyType = config.getStrategyType() != null ? config.getStrategyType() : "REACT";
        log.info("AgentRunner启动: agentKey={}, strategy={}", config.getAgentKey(), strategyType);
        reportStatus("🤖 " + config.getAgentName() + " 开始分析...");

        try {
            // 1. 构建 NodeExecutionContext（桥接 Agent 上下文到 Node 上下文）
            com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionContext nodeCtx =
                    buildNodeContext(context);

            // 2. 从 ApplicationContext 延迟获取策略（避免循环依赖）
            com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionStrategyRegistry registry =
                    applicationContext.getBean(com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionStrategyRegistry.class);
            com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionStrategy strategy =
                    registry.getRequired(strategyType);
            com.nageoffer.ai.ragent.agent.action.domain.ActionResult actionResult =
                    strategy.execute(nodeCtx);

            // 3. 保存记忆
            saveMemory(context, config, actionResult);

            // 4. 转换为 AgentExecutionResult
            long duration = System.currentTimeMillis() - startTime;
            if (actionResult.isSuccess()) {
                String output = actionResult.getOutput() != null ? actionResult.getOutput().toString() : "";
                reportStatus("✅ " + config.getAgentName() + " 完成 (" + String.format("%.1f", duration / 1000.0) + "s)");
                return AgentExecutionResult.success(config.getAgentKey(), output,
                        actionResult.getOutput(), duration);
            } else {
                reportStatus("❌ " + config.getAgentName() + " 失败: " + actionResult.getErrorMessage());
                return AgentExecutionResult.fail(config.getAgentKey(),
                        actionResult.getErrorMessage(), duration);
            }

        } catch (Exception e) {
            log.error("AgentRunner执行失败: agentKey={}", config.getAgentKey(), e);
            reportStatus("❌ " + config.getAgentName() + " 异常: " + e.getMessage());
            return AgentExecutionResult.fail(config.getAgentKey(), e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    private com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionContext buildNodeContext(
            AgentExecutionContext context) {
        AgentConfig config = context.getAgentConfig();

        // 构建 configJson：把 Agent 的配置映射为 Node 的 config
        ObjectNode nodeConfig = objectMapper.createObjectNode();
        nodeConfig.put("strategyType", config.getStrategyType() != null ? config.getStrategyType() : "REACT");
        if (config.getTemperature() != null) nodeConfig.put("temperature", config.getTemperature());
        if (config.getMaxTokens() != null) nodeConfig.put("maxTokens", config.getMaxTokens());
        nodeConfig.put("thinking", config.getThinking() != null ? config.getThinking() : false);
        if (config.getModelId() != null) nodeConfig.put("modelId", config.getModelId());
        nodeConfig.put("taskPrompt", buildSystemPromptBody(config));
        if (config.getToolNames() != null && !config.getToolNames().isEmpty()) {
            ArrayNode allowedTools = objectMapper.createArrayNode();
            for (String toolName : config.getToolNames()) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("name", toolName);
                toolNode.put("actionType", "MCP_TOOL");
                toolNode.set("parameters", objectMapper.createObjectNode());
                ObjectNode toolConfig = objectMapper.createObjectNode();
                toolConfig.put("toolName", toolName);
                toolNode.set("config", toolConfig);
                allowedTools.add(toolNode);
            }
            nodeConfig.set("allowedTools", allowedTools);
        }

        // 构建输入
        ObjectNode input = objectMapper.createObjectNode();
        if (context.getOriginalInput() != null) {
            input.set("originalInput", context.getOriginalInput());
            // 注入 skillContext
            if (context.getOriginalInput().has("skillContext")) {
                input.set("skillContext", context.getOriginalInput().get("skillContext"));
            }
        }

        // 构建假的 instance 和 node（策略只需要 id/workflowId/nodeKey）
        com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowInstanceDO fakeInstance =
                new com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowInstanceDO();
        fakeInstance.setId(context.getInstanceId());
        fakeInstance.setWorkflowId("agent_" + config.getAgentKey());
        com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowNodeDO fakeNode =
                new com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowNodeDO();
        fakeNode.setNodeKey(config.getAgentKey());
        fakeNode.setNodeName(config.getAgentName());

        return com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionContext.builder()
                .instance(fakeInstance)
                .node(fakeNode)
                .originalInput(input)
                .nodeConfig(nodeConfig)
                .build();
    }

    private String buildSystemPromptBody(AgentConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(config.getRole()).append("\n\n");
        if (config.getGoal() != null) sb.append("目标: ").append(config.getGoal()).append("\n\n");
        if (config.getToolNames() != null && !config.getToolNames().isEmpty()) {
            sb.append("可用工具:\n");
            for (String toolName : config.getToolNames()) {
                var executor = mcpToolRegistry.getExecutor(toolName);
                executor.ifPresent(e -> {
                    sb.append("- ").append(e.getToolDefinition().name()).append(": ")
                            .append(e.getToolDefinition().description()).append("\n");
                    if (e.getToolDefinition().inputSchema() != null) {
                        try {
                            sb.append("  参数Schema: ").append(objectMapper.writeValueAsString(
                                    e.getToolDefinition().inputSchema())).append("\n");
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
        return sb.toString();
    }

    private void saveMemory(AgentExecutionContext context, AgentConfig config,
                            com.nageoffer.ai.ragent.agent.action.domain.ActionResult result) {
        try {
            String output = result.getOutput() != null ? result.getOutput().toString() : "";
            agentMemoryService.saveMessage(context.getInstanceId(), context.getNodeInstanceId(),
                    config.getAgentKey(), context.getRoundNumber(), "USER",
                    context.getOriginalInput().toString());
            agentMemoryService.saveMessage(context.getInstanceId(), context.getNodeInstanceId(),
                    config.getAgentKey(), context.getRoundNumber(), "ASSISTANT", output);

            // 异步压缩
            agentMemoryService.compressAsync(context.getInstanceId(), context.getNodeInstanceId(),
                    config.getAgentKey());
        } catch (Exception e) {
            log.warn("Agent记忆保存失败: agentKey={}", config.getAgentKey(), e);
        }
    }
}
