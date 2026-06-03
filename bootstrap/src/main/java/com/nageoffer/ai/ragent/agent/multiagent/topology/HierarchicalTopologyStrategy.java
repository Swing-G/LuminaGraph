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

package com.nageoffer.ai.ragent.agent.multiagent.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.multiagent.core.AgentRunner;
import com.nageoffer.ai.ragent.agent.multiagent.core.ResultMergeEngine;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentConfig;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionContext;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionResult;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentTeamTopology;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 层级拓扑策略
 * <p>
 * Leader Agent 拆解任务 → 分派给 Worker Agent 并行执行 → Leader 合成最终答案。
 * 三阶段执行：LEADER_PLAN → WORKERS_EXECUTE → LEADER_SYNTHESIZE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HierarchicalTopologyStrategy implements TeamTopologyStrategy {

    private final AgentRunner agentRunner;
    private final Executor agentTeamExecutor;
    private final LLMService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int WORKER_TIMEOUT_SECONDS = 120;

    @Override
    public String topologyType() {
        return AgentTeamTopology.HIERARCHICAL.name();
    }

    @Override
    public AgentExecutionResult execute(TeamTopologyContext context) {
        List<AgentConfig> agents = context.getAgents();
        log.info("HierarchicalTopology启动: agent数={}, nodeInstanceId={}", agents.size(), context.getNodeInstanceId());

        // 1. 找 Leader（isLeader=true 的第一个）和 Worker
        AgentConfig leader = agents.stream().filter(a -> Boolean.TRUE.equals(a.getIsLeader())).findFirst().orElse(null);
        List<AgentConfig> workers = agents.stream().filter(a -> !Boolean.TRUE.equals(a.getIsLeader())).collect(Collectors.toList());

        if (leader == null) {
            return AgentExecutionResult.fail("HIERARCHICAL", "未配置 Leader Agent（需设置 isLeader=true）", 0);
        }
        if (workers.isEmpty()) {
            return AgentExecutionResult.fail("HIERARCHICAL", "至少需要 1 个 Worker Agent", 0);
        }
        log.info("Hierarchical: Leader={}, Workers={}", leader.getAgentKey(),
                workers.stream().map(AgentConfig::getAgentKey).collect(Collectors.joining(",")));

        // ─── Phase 1: LEADER_PLAN — 拆解任务 ─────
        AgentExecutionResult planResult = runLeaderPlan(leader, context);
        if (!planResult.isSuccess()) {
            return AgentExecutionResult.fail("HIERARCHICAL", "Leader 拆解任务失败: " + planResult.getErrorMessage(), planResult.getDurationMs());
        }
        log.info("Hierarchical Phase1 完成: Leader 已拆解任务");

        // 解析拆解结果，匹配 Worker
        Map<String, String> workerTasks = parseSubtasks(planResult.getOutput(), workers);
        if (workerTasks.isEmpty()) {
            // 解析失败时，所有 Worker 拿到相同的原始任务
            workers.forEach(w -> workerTasks.put(w.getAgentKey(), context.getOriginalInput().toString()));
        }

        // ─── Phase 2: WORKERS_EXECUTE — 并行执行 ─────
        Map<String, AgentExecutionResult> workerResults = runWorkers(workers, workerTasks, context);
        log.info("Hierarchical Phase2 完成: Worker成功={}/{}",
                workerResults.values().stream().filter(AgentExecutionResult::isSuccess).count(), workers.size());

        // ─── Phase 3: LEADER_SYNTHESIZE — 合成最终答案 ─────
        AgentExecutionResult finalResult = runLeaderSynthesize(leader, context, planResult, workerResults);
        log.info("Hierarchical Phase3 完成: success={}", finalResult.isSuccess());

        return buildStructuredResult(leader, workers, planResult, workerResults, finalResult);
    }

    // ─── Phase 1: Leader 拆解任务 ──────
    private AgentExecutionResult runLeaderPlan(AgentConfig leader, TeamTopologyContext context) {
        AgentExecutionContext leaderCtx = AgentExecutionContext.builder()
                .instanceId(context.getInstanceId())
                .nodeInstanceId(context.getNodeInstanceId())
                .agentConfig(leader)
                .originalInput(context.getOriginalInput())
                .workflowContext(context.getWorkflowContext())
                .roundNumber(0)
                .build();
        // 给 Leader 特殊的 system prompt 追加拆解指令
        AgentConfig planLeader = buildPlanLeaderConfig(leader, context);
        leaderCtx.setAgentConfig(planLeader);
        return agentRunner.run(leaderCtx);
    }

    private AgentConfig buildPlanLeaderConfig(AgentConfig leader, TeamTopologyContext context) {
        List<AgentConfig> workers = context.getAgents().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsLeader())).collect(Collectors.toList());
        StringBuilder workerDesc = new StringBuilder();
        for (AgentConfig w : workers) {
            workerDesc.append("- ").append(w.getAgentKey()).append("（").append(w.getAgentName()).append("）: ")
                    .append(w.getRole()).append("\n");
        }
        String enhancedRole = leader.getRole() + "\n\n## 你的职责\n"
                + "你是任务协调者。首先分析用户问题，然后将任务拆解分配给以下专家，每人负责一个子任务。\n\n"
                + "## 可用专家\n" + workerDesc + "\n"
                + "## 输出格式\n"
                + "请以 JSON 格式输出任务分配，每个专家一个子任务：\n"
                + "{\"subtasks\": [{\"agentKey\": \"专家Key\", \"task\": \"这个专家要分析的具体问题\"}]}\n"
                + "注意：agentKey 必须从上面的可用专家列表中选择。";
        return AgentConfig.builder()
                .agentKey(leader.getAgentKey()).agentName(leader.getAgentName())
                .role(enhancedRole).goal(leader.getGoal())
                .modelId(leader.getModelId()).toolNames(leader.getToolNames())
                .temperature(leader.getTemperature()).maxTokens(leader.getMaxTokens())
                .thinking(leader.getThinking()).memoryStrategy(leader.getMemoryStrategy())
                .build();
    }

    private Map<String, String> parseSubtasks(String planOutput, List<AgentConfig> workers) {
        Map<String, String> tasks = new LinkedHashMap<>();
        try {
            String json = extractJson(planOutput);
            if (json != null) {
                ObjectNode root = (ObjectNode) objectMapper.readTree(json);
                if (root.has("subtasks") && root.get("subtasks").isArray()) {
                    for (int i = 0; i < root.get("subtasks").size(); i++) {
                        ObjectNode sub = (ObjectNode) root.get("subtasks").get(i);
                        String agentKey = sub.path("agentKey").asText();
                        String task = sub.path("task").asText();
                        if (!agentKey.isEmpty() && !task.isEmpty()) {
                            // 只接受已注册 Worker 的 key
                            if (workers.stream().anyMatch(w -> w.getAgentKey().equals(agentKey))) {
                                tasks.put(agentKey, task);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 Leader 拆解结果失败，将使用原始任务", e);
        }
        return tasks;
    }

    // ─── Phase 2: Worker 并行执行 ──────
    private Map<String, AgentExecutionResult> runWorkers(List<AgentConfig> workers,
                                                          Map<String, String> workerTasks,
                                                          TeamTopologyContext context) {
        List<CompletableFuture<AgentExecutionResult>> futures = new ArrayList<>();
        List<String> workerKeys = new ArrayList<>();
        for (AgentConfig worker : workers) {
            String task = workerTasks.getOrDefault(worker.getAgentKey(), context.getOriginalInput().toString());
            workerKeys.add(worker.getAgentKey());
            CompletableFuture<AgentExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                AgentExecutionContext workerCtx = AgentExecutionContext.builder()
                        .instanceId(context.getInstanceId())
                        .nodeInstanceId(context.getNodeInstanceId())
                        .agentConfig(worker)
                        .originalInput(objectMapper.createObjectNode().put("task", task))
                        .workflowContext(context.getWorkflowContext())
                        .roundNumber(1)
                        .build();
                return agentRunner.run(workerCtx);
            }, agentTeamExecutor)
                    .orTimeout(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> AgentExecutionResult.fail(
                            worker.getAgentKey(), "Worker超时: " + ex.getMessage(), 0));
            futures.add(future);
        }

        Map<String, AgentExecutionResult> results = new LinkedHashMap<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.put(workerKeys.get(i), futures.get(i).get());
            } catch (Exception e) {
                log.error("Worker执行异常: agentKey={}", workerKeys.get(i), e);
            }
        }
        return results;
    }

    // ─── Phase 3: Leader 合成 ──────
    private AgentExecutionResult runLeaderSynthesize(AgentConfig leader, TeamTopologyContext context,
                                                      AgentExecutionResult planResult,
                                                      Map<String, AgentExecutionResult> workerResults) {
        StringBuilder workersOutput = new StringBuilder();
        for (Map.Entry<String, AgentExecutionResult> entry : workerResults.entrySet()) {
            workersOutput.append("### ").append(entry.getKey()).append("\n");
            if (entry.getValue().isSuccess()) {
                workersOutput.append(entry.getValue().getOutput()).append("\n\n");
            } else {
                workersOutput.append("[失败] ").append(entry.getValue().getErrorMessage()).append("\n\n");
            }
        }

        String synthPrompt = "你是一个任务协调者。以下是原始问题和各专家的分析结果，请直接告诉用户答案，"
                + "像客服回复一样自然、简洁：\n\n"
                + "## 原始问题\n" + context.getOriginalInput().toString() + "\n\n"
                + "## 专家分析\n" + workersOutput;

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.system(leader.getRole()), ChatMessage.user(synthPrompt)))
                    .temperature(leader.getTemperature() != null ? leader.getTemperature() : 0.3)
                    .maxTokens(leader.getMaxTokens() != null ? leader.getMaxTokens() : 2000)
                    .thinking(leader.getThinking() != null ? leader.getThinking() : false)
                    .build();
            String synthesis = llmService.chat(request, leader.getModelId());
            return AgentExecutionResult.success(leader.getAgentKey(), synthesis, null,
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Leader 合成失败，拼接 Worker 输出", e);
            return AgentExecutionResult.success(leader.getAgentKey(), workersOutput.toString(), null, 0);
        }
    }

    // ─── 构建结构化输出 ──────
    private AgentExecutionResult buildStructuredResult(AgentConfig leader, List<AgentConfig> workers,
                                                        AgentExecutionResult planResult,
                                                        Map<String, AgentExecutionResult> workerResults,
                                                        AgentExecutionResult finalResult) {
        long totalDuration = 0;
        ArrayNode workerArray = objectMapper.createArrayNode();
        for (AgentConfig w : workers) {
            AgentExecutionResult wr = workerResults.get(w.getAgentKey());
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("agentKey", w.getAgentKey());
            entry.put("agentName", w.getAgentName());
            entry.put("success", wr != null && wr.isSuccess());
            entry.put("output", wr != null && wr.getOutput() != null ? wr.getOutput() : "");
            entry.put("durationMs", wr != null && wr.getDurationMs() != null ? wr.getDurationMs() : 0);
            if (wr != null) totalDuration += wr.getDurationMs() != null ? wr.getDurationMs() : 0;
            if (wr != null && wr.getErrorMessage() != null) {
                entry.put("errorMessage", wr.getErrorMessage());
            }
            workerArray.add(entry);
        }

        ObjectNode structuredOutput = objectMapper.createObjectNode();
        structuredOutput.put("topology", "HIERARCHICAL");
        structuredOutput.put("leader", leader.getAgentKey());
        structuredOutput.put("totalWorkers", workers.size());
        structuredOutput.put("successCount", workerResults.values().stream().filter(AgentExecutionResult::isSuccess).count());
        structuredOutput.set("workerResults", workerArray);
        structuredOutput.put("plan", planResult.getOutput());

        return AgentExecutionResult.builder()
                .agentKey(leader.getAgentKey())
                .success(finalResult.isSuccess())
                .status(finalResult.isSuccess() ? "SUCCESS" : "FAILED")
                .output(finalResult.getOutput())
                .structuredOutput(structuredOutput)
                .durationMs(totalDuration)
                .build();
    }

    private String extractJson(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return null;
    }
}
