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
import com.nageoffer.ai.ragent.agent.multiagent.core.BlackboardService;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentConfig;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionContext;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionResult;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentTeamTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 顺序拓扑策略
 * <p>
 * Agent 按 agentOrder 依次执行，每个 Agent 可以看到前面所有 Agent 的输出。
 * 最后一个 Agent 的输出即为整个 Team 的最终结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SequentialTopologyStrategy implements TeamTopologyStrategy {

    private final AgentRunner agentRunner;
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String topologyType() {
        return AgentTeamTopology.SEQUENTIAL.name();
    }

    @Override
    public AgentExecutionResult execute(TeamTopologyContext context) {
        List<AgentConfig> agents = context.getAgents().stream()
                .sorted(Comparator.comparing(a -> a.getAgentOrder() != null ? a.getAgentOrder() : 0))
                .collect(Collectors.toList());
        log.info("SequentialTopology启动: agent数={}, nodeInstanceId={}, 顺序: {}",
                agents.size(), context.getNodeInstanceId(),
                agents.stream().map(AgentConfig::getAgentKey).collect(Collectors.joining(" → ")));

        List<AgentExecutionResult> results = new ArrayList<>();
        ObjectNode accumulatedContext = objectMapper.createObjectNode();

        for (int i = 0; i < agents.size(); i++) {
            AgentConfig agent = agents.get(i);
            log.info("Sequential 执行 Agent {}/{}: {}", i + 1, agents.size(), agent.getAgentKey());

            // 构建输入：原始任务 + 前面所有 Agent 的输出
            ObjectNode agentInput = objectMapper.createObjectNode();
            agentInput.set("originalTask", context.getOriginalInput());
            agentInput.put("step", i + 1);
            agentInput.put("totalSteps", agents.size());

            if (i > 0) {
                agentInput.put("instruction", "这是第" + (i + 1) + "步。前面已有 "
                        + i + " 位专家完成了分析，请参考他们的结论，在此基础上继续深化或补充。");
                ArrayNode prevOutputs = objectMapper.createArrayNode();
                for (int j = 0; j < i; j++) {
                    ObjectNode prev = objectMapper.createObjectNode();
                    prev.put("agentKey", results.get(j).getAgentKey());
                    prev.put("output", results.get(j).isSuccess()
                            ? results.get(j).getOutput() : "[失败] " + results.get(j).getErrorMessage());
                    prevOutputs.add(prev);
                }
                agentInput.set("previousOutputs", prevOutputs);
            } else {
                agentInput.put("instruction", "你是第一个分析的专家。请基于你的专业角度独立分析。");
            }

            // 上报状态
            reportStatus("▶️ 第" + (i + 1) + "/" + agents.size() + " 步: 「" + agent.getAgentName() + "」开始分析...");
            long agentStart = System.currentTimeMillis();

            // 执行
            AgentExecutionContext agentCtx = AgentExecutionContext.builder()
                    .instanceId(context.getInstanceId())
                    .nodeInstanceId(context.getNodeInstanceId())
                    .agentConfig(agent)
                    .originalInput(agentInput)
                    .workflowContext(context.getWorkflowContext())
                    .roundNumber(i + 1)
                    .build();

            AgentExecutionResult result = agentRunner.run(agentCtx);
            results.add(result);
            float dur = (System.currentTimeMillis() - agentStart) / 1000.0f;
            reportStatus("✅ 第" + (i + 1) + "/" + agents.size() + " 步: 「" + agent.getAgentName() + "」完成 (" + String.format("%.1f", dur) + "s)");

            // 写入 Blackboard
            if (result.isSuccess()) {
                blackboardService.writeEntry(context.getNodeInstanceId(), i + 1,
                        agent.getAgentKey(), "RESULT", result.getOutput(), null);
            }
        }

        // 取最后一个成功的作为最终输出
        AgentExecutionResult lastSuccess = null;
        for (int i = results.size() - 1; i >= 0; i--) {
            if (results.get(i).isSuccess()) {
                lastSuccess = results.get(i);
                break;
            }
        }

        if (lastSuccess == null) {
            return AgentExecutionResult.fail("SEQUENTIAL", "所有 Agent 均失败", 0);
        }

        log.info("SequentialTopology完成: 成功={}/{}", results.stream().filter(AgentExecutionResult::isSuccess).count(), agents.size());
        return buildStructuredResult(results, lastSuccess);
    }

    private AgentExecutionResult buildStructuredResult(List<AgentExecutionResult> results,
                                                        AgentExecutionResult lastSuccess) {
        ArrayNode stepsArray = objectMapper.createArrayNode();
        long totalDuration = 0;
        for (int i = 0; i < results.size(); i++) {
            AgentExecutionResult r = results.get(i);
            ObjectNode step = objectMapper.createObjectNode();
            step.put("step", i + 1);
            step.put("agentKey", r.getAgentKey());
            step.put("success", r.isSuccess());
            step.put("output", r.getOutput() != null ? r.getOutput() : "");
            step.put("durationMs", r.getDurationMs() != null ? r.getDurationMs() : 0);
            if (r.getErrorMessage() != null) step.put("errorMessage", r.getErrorMessage());
            totalDuration += r.getDurationMs() != null ? r.getDurationMs() : 0;
            stepsArray.add(step);
        }

        ObjectNode structuredOutput = objectMapper.createObjectNode();
        structuredOutput.put("topology", "SEQUENTIAL");
        structuredOutput.put("totalSteps", results.size());
        structuredOutput.put("successCount", results.stream().filter(AgentExecutionResult::isSuccess).count());
        structuredOutput.set("steps", stepsArray);

        return AgentExecutionResult.builder()
                .agentKey(lastSuccess.getAgentKey())
                .success(true)
                .status("SUCCESS")
                .output(lastSuccess.getOutput())
                .structuredOutput(structuredOutput)
                .durationMs(totalDuration)
                .build();
    }

    private void reportStatus(String msg) {
        com.nageoffer.ai.ragent.infra.chat.StreamCallback cb = com.nageoffer.ai.ragent.agent.multiagent.core.AgentRunner.getStatusCallback().get();
        if (cb != null) cb.onStatus(msg);
    }
}
