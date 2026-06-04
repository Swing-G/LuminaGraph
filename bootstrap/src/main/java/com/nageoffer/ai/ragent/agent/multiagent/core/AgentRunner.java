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
    private final ObjectMapper objectMapper;

    private static final int MAX_TOOL_ITERATIONS = 5;

    /**
     * 执行单个Agent
     */
    public AgentExecutionResult run(AgentExecutionContext context) {
        long startTime = System.currentTimeMillis();
        AgentConfig config = context.getAgentConfig();
        log.info("AgentRunner启动: agentKey={}, role={}", config.getAgentKey(), config.getAgentName());

        try {
            // 1. 构建消息列表
            List<ChatMessage> messages = buildMessages(config, context);

            // 2. 执行ReAct循环
            StringBuilder finalOutput = new StringBuilder();
            List<JsonNode> toolResults = new ArrayList<>();
            int totalTokens = 0;

            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatRequest request = buildChatRequest(config, messages);
                String response = llmService.chat(request, config.getModelId());
                totalTokens += estimateTokens(response);

                // 3. 尝试解析Tool Call
                JsonNode toolCall = parseToolCall(response);
                if (toolCall != null) {
                    String toolName = toolCall.path("tool").asText();
                    JsonNode params = toolCall.path("parameters");
                    JsonNode toolResult = executeTool(toolName, params);
                    toolResults.add(toolResult);

                    messages.add(ChatMessage.assistant("调用工具 " + toolName + " 结果: " + toolResult));
                    log.debug("Agent {} 调用工具: {}, 结果: {}", config.getAgentKey(), toolName, toolResult);
                } else {
                    // 无工具调用，视为最终输出
                    finalOutput.append(response);
                    break;
                }
            }

            if (finalOutput.isEmpty()) {
                finalOutput.append(messages.get(messages.size() - 1).getContent());
            }

            // 4. 保存记忆
            saveMemory(context, config, messages, finalOutput.toString());

            long duration = System.currentTimeMillis() - startTime;
            AgentExecutionResult result = AgentExecutionResult.success(
                    config.getAgentKey(),
                    finalOutput.toString(),
                    extractStructuredOutput(finalOutput.toString()),
                    duration
            );
            result.setEstimatedTokens(totalTokens);
            result.setToolResults(toolResults);
            return result;

        } catch (Exception e) {
            log.error("AgentRunner执行失败: agentKey={}", config.getAgentKey(), e);
            return AgentExecutionResult.fail(config.getAgentKey(), e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    private List<ChatMessage> buildMessages(AgentConfig config, AgentExecutionContext context) {
        List<ChatMessage> messages = new ArrayList<>();

        // System Prompt
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(config.getRole()).append("\n\n");
        systemPrompt.append("目标: ").append(config.getGoal()).append("\n\n");

        // 可用工具描述（含MCP inputSchema，让LLM知道需要哪些参数）
        if (config.getToolNames() != null && !config.getToolNames().isEmpty()) {
            systemPrompt.append("可用工具:\n");
            for (String toolName : config.getToolNames()) {
                Optional<McpToolExecutor> executor = mcpToolRegistry.getExecutor(toolName);
                if (executor.isPresent()) {
                    McpSchema.Tool tool = executor.get().getToolDefinition();
                    systemPrompt.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
                    if (tool.inputSchema() != null) {
                        try {
                            String schemaStr = objectMapper.writeValueAsString(tool.inputSchema());
                            systemPrompt.append("  参数Schema: ").append(schemaStr).append("\n");
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            systemPrompt.append("\n如需调用工具，请输出JSON格式: {\"tool\": \"工具名\", \"parameters\": {...}}。");
            systemPrompt.append("parameters中的参数值必须从任务输入的JSON中提取对应的字段值。\n");
        }

        // 注入 Skill 上下文（如果输入中有 skillContext 字段）
        if (context.getOriginalInput() != null && context.getOriginalInput().has("skillContext")) {
            String skillContext = context.getOriginalInput().path("skillContext").asText(null);
            if (skillContext != null && !skillContext.isBlank()) {
                systemPrompt.append("\n\n## 业务知识库（Skill）\n").append(skillContext).append("\n");
            }
        }
        messages.add(ChatMessage.system(systemPrompt.toString()));

        // 加载历史记忆
        List<AgentMemoryDO> history = agentMemoryService.getHistoryWithTokenLimit(
                context.getNodeInstanceId(), config.getAgentKey());
        for (AgentMemoryDO mem : history) {
            if ("USER".equals(mem.getRole())) {
                messages.add(ChatMessage.user(mem.getContent()));
            } else if ("ASSISTANT".equals(mem.getRole())) {
                messages.add(ChatMessage.assistant(mem.getContent()));
            } else if ("SYSTEM".equals(mem.getRole())) {
                messages.add(ChatMessage.system(mem.getContent()));
            }
        }

        // 当前任务
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("任务输入:\n").append(context.getOriginalInput().toString());

        // Blackboard上下文
        if (context.getBlackboardContext() != null && !context.getBlackboardContext().isEmpty()) {
            userMsg.append("\n\n其他Agent的分析结果（Blackboard）:\n")
                    .append(context.getBlackboardContext().toString());
        }

        userMsg.append("\n\n请完成你的分析并给出结论。");
        messages.add(ChatMessage.user(userMsg.toString()));

        return messages;
    }

    private ChatRequest buildChatRequest(AgentConfig config, List<ChatMessage> messages) {
        ChatRequest.ChatRequestBuilder builder = ChatRequest.builder().messages(messages);
        if (config.getTemperature() != null) {
            builder.temperature(config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            builder.maxTokens(config.getMaxTokens());
        }
        if (config.getThinking() != null) {
            builder.thinking(config.getThinking());
        }
        return builder.build();
    }

    /**
     * 尝试从LLM输出解析工具调用
     */
    private JsonNode parseToolCall(String response) {
        try {
            String trimmed = response.trim();
            // 尝试提取JSON块
            if (trimmed.contains("```json")) {
                int start = trimmed.indexOf("```json") + 7;
                int end = trimmed.indexOf("```", start);
                if (end > start) {
                    trimmed = trimmed.substring(start, end).trim();
                }
            }
            if (trimmed.startsWith("{")) {
                JsonNode node = objectMapper.readTree(trimmed);
                if (node.has("tool") && node.has("parameters")) {
                    return node;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 执行MCP工具调用
     */
    private JsonNode executeTool(String toolName, JsonNode params) {
        try {
            Optional<McpToolExecutor> executor = mcpToolRegistry.getExecutor(toolName);
            if (executor.isEmpty()) {
                return objectMapper.createObjectNode()
                        .put("error", "工具未找到: " + toolName);
            }
            Map<String, Object> paramMap = objectMapper.convertValue(params, Map.class);
            McpSchema.CallToolResult result = executor.get().execute(paramMap);
            if (result.isError() != null && result.isError()) {
                return objectMapper.createObjectNode()
                        .put("error", "工具执行失败")
                        .put("toolName", toolName);
            }
            return objectMapper.valueToTree(result);
        } catch (Exception e) {
            return objectMapper.createObjectNode()
                    .put("error", e.getMessage())
                    .put("toolName", toolName);
        }
    }

    private JsonNode extractStructuredOutput(String output) {
        try {
            String trimmed = output.trim();
            if (trimmed.contains("```json")) {
                int start = trimmed.indexOf("```json") + 7;
                int end = trimmed.indexOf("```", start);
                if (end > start) {
                    return objectMapper.readTree(trimmed.substring(start, end).trim());
                }
            }
            if (trimmed.startsWith("{")) {
                return objectMapper.readTree(trimmed);
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode().put("raw", output);
    }

    private void saveMemory(AgentExecutionContext context, AgentConfig config,
                            List<ChatMessage> messages, String output) {
        try {
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

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) (text.length() * 0.3);
    }
}
