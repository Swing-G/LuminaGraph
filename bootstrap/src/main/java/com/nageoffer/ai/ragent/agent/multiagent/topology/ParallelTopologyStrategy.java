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
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentMergeStrategy;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentTeamTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 并行拓扑策略
 * <p>
 * 所有Agent并发执行，各自独立分析同一输入。
 * 结果按Agent顺序收集，供外部MergeEngine合并。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelTopologyStrategy implements TeamTopologyStrategy {

    private final AgentRunner agentRunner;
    private final Executor agentTeamExecutor;
    private final ResultMergeEngine mergeEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int AGENT_TIMEOUT_SECONDS = 120;

    @Override
    public String topologyType() {
        return AgentTeamTopology.PARALLEL.name();
    }

    @Override
    public AgentExecutionResult execute(TeamTopologyContext context) {
        List<AgentConfig> agents = context.getAgents();
        log.info("ParallelTopology启动: agent数={}, nodeInstanceId={}", agents.size(), context.getNodeInstanceId());

        // 并发启动所有Agent
        List<CompletableFuture<AgentExecutionResult>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(() -> {
                    AgentExecutionContext agentCtx = AgentExecutionContext.builder()
                            .instanceId(context.getInstanceId())
                            .nodeInstanceId(context.getNodeInstanceId())
                            .agentConfig(agent)
                            .originalInput(context.getOriginalInput())
                            .workflowContext(context.getWorkflowContext())
                            .roundNumber(context.getCurrentRound())
                            .blackboardContext(null) // 并行模式下首轮无Blackboard上下文
                            .build();
                    return agentRunner.run(agentCtx);
                }, agentTeamExecutor)
                        .orTimeout(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .exceptionally(ex -> AgentExecutionResult.fail(
                                "unknown", "Agent执行超时或异常: " + ex.getMessage(), 0)))
                .collect(Collectors.toList());

        // 等待所有Agent完成
        List<AgentExecutionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 检查成功数
        long successCount = results.stream().filter(AgentExecutionResult::isSuccess).count();
        log.info("ParallelTopology完成: 成功={}/{}, nodeInstanceId={}", successCount, agents.size(),
                context.getNodeInstanceId());

        // 如果全部失败，返回最后一个错误
        if (successCount == 0) {
            AgentExecutionResult firstFailure = results.get(0);
            return AgentExecutionResult.fail("PARALLEL_ALL_FAILED",
                    "所有Agent执行失败: " + firstFailure.getErrorMessage(), 0);
        }

        // 构建聚合结果
        return buildAggregatedResult(results, context.getMergeStrategy());
    }

    private AgentExecutionResult buildAggregatedResult(List<AgentExecutionResult> results, String mergeStrategy) {
        long totalDuration = results.stream().mapToLong(r -> r.getDurationMs() != null ? r.getDurationMs() : 0).max().orElse(0);
        int totalTokens = results.stream().mapToInt(r -> r.getEstimatedTokens() != null ? r.getEstimatedTokens() : 0).sum();
        long successCount = results.stream().filter(AgentExecutionResult::isSuccess).count();

        // SYNTHESIS: 调用 mergeEngine 综合所有 Agent 分析
        String finalOutput;
        if ("SYNTHESIS".equalsIgnoreCase(mergeStrategy) && successCount > 1) {
            AgentExecutionResult synthesized = mergeEngine.merge(
                    results.stream().filter(AgentExecutionResult::isSuccess).collect(java.util.stream.Collectors.toList()),
                    AgentMergeStrategy.SYNTHESIS);
            finalOutput = synthesized.getOutput();
        } else {
            // FIRST 或其他：拼接原始输出
            StringBuilder sb = new StringBuilder("=== 多Agent并行分析结果 ===\n\n");
            for (AgentExecutionResult r : results) {
                sb.append("--- ").append(r.getAgentKey()).append(" ---\n");
                if (r.isSuccess()) {
                    sb.append(r.getOutput()).append("\n\n");
                } else {
                    sb.append("[失败] ").append(r.getErrorMessage()).append("\n\n");
                }
            }
            finalOutput = sb.toString();
        }

        ArrayNode agentResultsArray = objectMapper.createArrayNode();
        for (AgentExecutionResult r : results) {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("agentKey", r.getAgentKey());
            entry.put("success", r.isSuccess());
            entry.put("output", r.getOutput() != null ? r.getOutput() : "");
            entry.put("durationMs", r.getDurationMs() != null ? r.getDurationMs() : 0);
            if (r.getErrorMessage() != null) {
                entry.put("errorMessage", r.getErrorMessage());
            }
            agentResultsArray.add(entry);
        }

        ObjectNode structuredOutput = objectMapper.createObjectNode();
        structuredOutput.put("topology", "PARALLEL");
        structuredOutput.put("mergeStrategy", mergeStrategy != null ? mergeStrategy : "");
        structuredOutput.put("totalAgents", results.size());
        structuredOutput.put("successCount", successCount);
        structuredOutput.set("agentResults", agentResultsArray);
        structuredOutput.put("aggregatedSummary", finalOutput);

        return AgentExecutionResult.builder()
                .agentKey("PARALLEL_AGGREGATED")
                .success(true)
                .status("SUCCESS")
                .output(finalOutput)
                .structuredOutput(structuredOutput)
                .durationMs(totalDuration)
                .estimatedTokens(totalTokens)
                .build();
    }
}
