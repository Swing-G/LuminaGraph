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
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 辩论拓扑策略
 * <p>
 * 多轮辩论：每轮所有 Agent 并行分析，通过 Moderator 判断是否收敛。
 * Agent 在每轮可以看到上轮所有同行输出，修正自己的观点。
 * 达到共识或最大轮数后输出最终结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebateTopologyStrategy implements TeamTopologyStrategy {

    private final AgentRunner agentRunner;
    private final Executor agentTeamExecutor;
    private final LLMService llmService;
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int AGENT_TIMEOUT_SECONDS = 120;

    @Override
    public String topologyType() {
        return AgentTeamTopology.DEBATE.name();
    }

    @Override
    public AgentExecutionResult execute(TeamTopologyContext context) {
        List<AgentConfig> agents = context.getAgents();
        int maxRounds = context.getMaxRounds() != null ? context.getMaxRounds() : 3;
        log.info("DebateTopology启动: agent数={}, maxRounds={}, nodeInstanceId={}",
                agents.size(), maxRounds, context.getNodeInstanceId());

        // 保存每轮每个 Agent 的结果
        Map<Integer, Map<String, AgentExecutionResult>> roundResults = new LinkedHashMap<>();
        String consensusOutput = null;

        for (int round = 1; round <= maxRounds; round++) {
            log.info("Debate 第{}轮开始", round);
            reportStatus("📢 辩论第" + round + "/" + maxRounds + "轮开始...");

            // 1. 构建本轮输入：首轮用原始任务，后续轮加入上轮所有 Agent 输出
            ObjectNode roundInput = buildRoundInput(context, roundResults, round, agents);
            List<AgentConfig> roundAgents = (round == 1) ? agents
                    : agents.stream().filter(a -> {
                        // 后续轮：pass 掉上轮已经和目标一致的 Agent（可选优化）
                        return true;
                    }).collect(Collectors.toList());

            // 2. 所有 Agent 并行执行
            Map<String, AgentExecutionResult> thisRound = runRound(context, roundAgents, roundInput, round);
            roundResults.put(round, thisRound);

            // 3. 写入 Blackboard（供后续轮参考）
            for (Map.Entry<String, AgentExecutionResult> entry : thisRound.entrySet()) {
                if (entry.getValue().isSuccess()) {
                    blackboardService.writeEntry(context.getNodeInstanceId(), round,
                            entry.getKey(), "RESULT", entry.getValue().getOutput(), null);
                }
            }

            // 4. Moderator 判断是否收敛
            ModeratorResult moderator = judgeConsensus(thisRound, context.getOriginalInput().toString(), round, maxRounds);
            log.info("Debate 第{}轮 Moderator: converged={}, confidence={}", round, moderator.converged, moderator.confidence);

            if (moderator.converged || round >= maxRounds) {
                consensusOutput = moderator.output;
                break;
            }
            log.info("Debate 第{}轮未收敛，进入下一轮", round);
        }

        if (consensusOutput == null) {
            // 兜底：取最后一轮的 Moderator 合成
            Map<String, AgentExecutionResult> lastRound = roundResults.get(roundResults.size());
            ModeratorResult fallback = judgeConsensus(lastRound, context.getOriginalInput().toString(),
                    roundResults.size(), maxRounds);
            consensusOutput = fallback.output;
        }

        log.info("DebateTopology完成: 总轮数={}", roundResults.size());
        return buildStructuredResult(roundResults, consensusOutput, context);
    }

    // ─── 构建本轮输入 ──────
    private ObjectNode buildRoundInput(TeamTopologyContext context,
                                        Map<Integer, Map<String, AgentExecutionResult>> roundResults,
                                        int round, List<AgentConfig> agents) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("round", round);
        input.put("maxRounds", context.getMaxRounds() != null ? context.getMaxRounds() : 3);

        if (round == 1) {
            input.set("task", context.getOriginalInput());
            input.put("instruction", "这是第一轮分析。请基于你的专业角度给出独立分析结论。");
        } else {
            input.set("originalTask", context.getOriginalInput());
            // 放入上轮所有 Agent 的分析
            ObjectNode previousRound = objectMapper.createObjectNode();
            Map<String, AgentExecutionResult> prevResults = roundResults.get(round - 1);
            if (prevResults != null) {
                for (Map.Entry<String, AgentExecutionResult> entry : prevResults.entrySet()) {
                    if (entry.getValue().isSuccess()) {
                        previousRound.put(entry.getKey(), entry.getValue().getOutput());
                    }
                }
            }
            input.set("previousRoundOutputs", previousRound);
            input.put("instruction",
                    "这是第" + round + "轮辩论。请阅读上轮其他专家的分析，反思自己的观点是否有遗漏或偏差，"
                            + "给出修正后的结论。如果同意其他专家的观点，请明确说明并补充你的看法。");
        }
        return input;
    }

    // ─── 并行执行一轮 ──────
    private Map<String, AgentExecutionResult> runRound(TeamTopologyContext context,
                                                        List<AgentConfig> agents,
                                                        ObjectNode roundInput, int round) {
        List<String> agentKeys = new ArrayList<>();
        List<CompletableFuture<AgentExecutionResult>> futures = new ArrayList<>();

        for (AgentConfig agent : agents) {
            agentKeys.add(agent.getAgentKey());
            CompletableFuture<AgentExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                AgentExecutionContext agentCtx = AgentExecutionContext.builder()
                        .instanceId(context.getInstanceId())
                        .nodeInstanceId(context.getNodeInstanceId())
                        .agentConfig(agent)
                        .originalInput(roundInput)
                        .workflowContext(context.getWorkflowContext())
                        .roundNumber(round)
                        .blackboardContext(null)
                        .build();
                return agentRunner.run(agentCtx);
            }, agentTeamExecutor)
                    .orTimeout(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> AgentExecutionResult.fail(
                            agent.getAgentKey(), "Agent超时: " + ex.getMessage(), 0));
            futures.add(future);
        }

        Map<String, AgentExecutionResult> results = new LinkedHashMap<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.put(agentKeys.get(i), futures.get(i).get());
            } catch (Exception e) {
                log.error("Debate Agent执行异常: agentKey={}", agentKeys.get(i), e);
            }
        }
        return results;
    }

    // ─── Moderator 判断收敛 ──────
    private ModeratorResult judgeConsensus(Map<String, AgentExecutionResult> results,
                                            String originalTask, int round, int maxRounds) {
        List<AgentExecutionResult> successful = results.values().stream()
                .filter(AgentExecutionResult::isSuccess).collect(Collectors.toList());
        if (successful.isEmpty()) {
            return new ModeratorResult(false, 0, "所有 Agent 本轮均失败，无法判断共识", round);
        }

        // 构建 Moderator prompt
        StringBuilder analyses = new StringBuilder();
        int idx = 1;
        for (AgentExecutionResult r : successful) {
            analyses.append("### 专家").append(idx).append(": ").append(r.getAgentKey()).append("\n")
                    .append(r.getOutput()).append("\n\n");
            idx++;
        }

        boolean isLastRound = round >= maxRounds;
        String prompt = isLastRound
                ? "以下是多位专家对同一问题的分析（最后一轮）。"
                    + "直接输出给用户的答案，不要提专家、不要提共识、不要提这是最后一轮：\n\n" + analyses
                : "以下是多位专家对同一问题的分析（辩论第" + round + "轮）。请判断专家们是否已达成共识。"
                    + "如果是，直接告诉用户答案。如果还有明显分歧，只需要回复\"CONTINUE\"。\n\n"
                    + "原始问题: " + originalTask + "\n\n" + analyses;

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.system("你是一个辩论 Moderator。"
                                    + "重要: 直接输出给用户的最终答案，不要输出任何分析过程、不要提专家、不要提共识。"
                                    + "就像你自己是客服在回复用户一样。如果专家们还有明显冲突未解决，只回复 CONTINUE。"),
                            ChatMessage.user(prompt)))
                    .temperature(0.1)
                    .build();
            String response = llmService.chat(request);

            boolean converged = isLastRound || !response.trim().toUpperCase().contains("CONTINUE");
            if (!converged && isLastRound) converged = true; // 最后一轮强制收敛

            return new ModeratorResult(converged, converged ? 90 : 40, response, round);
        } catch (Exception e) {
            log.warn("Moderator 判断失败，强制拼接输出", e);
            return new ModeratorResult(true, 50,
                    "多位专家分析完成。\n\n" + String.join("\n\n",
                            successful.stream().map(AgentExecutionResult::getOutput).collect(Collectors.toList())),
                    round);
        }
    }

    // ─── 结构化结果 ──────
    private AgentExecutionResult buildStructuredResult(
            Map<Integer, Map<String, AgentExecutionResult>> roundResults,
            String consensusOutput, TeamTopologyContext context) {

        ArrayNode roundsArray = objectMapper.createArrayNode();
        long totalDuration = 0;
        for (Map.Entry<Integer, Map<String, AgentExecutionResult>> roundEntry : roundResults.entrySet()) {
            ObjectNode roundNode = objectMapper.createObjectNode();
            roundNode.put("round", roundEntry.getKey());
            ArrayNode agentArray = objectMapper.createArrayNode();
            for (Map.Entry<String, AgentExecutionResult> a : roundEntry.getValue().entrySet()) {
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("agentKey", a.getKey());
                entry.put("success", a.getValue().isSuccess());
                entry.put("output", a.getValue().getOutput() != null ? a.getValue().getOutput() : "");
                entry.put("durationMs", a.getValue().getDurationMs() != null ? a.getValue().getDurationMs() : 0);
                if (a.getValue().getErrorMessage() != null) {
                    entry.put("errorMessage", a.getValue().getErrorMessage());
                }
                totalDuration += a.getValue().getDurationMs() != null ? a.getValue().getDurationMs() : 0;
                agentArray.add(entry);
            }
            roundNode.set("agents", agentArray);
            roundsArray.add(roundNode);
        }

        ObjectNode structuredOutput = objectMapper.createObjectNode();
        structuredOutput.put("topology", "DEBATE");
        structuredOutput.put("totalRounds", roundResults.size());
        structuredOutput.put("maxRounds", context.getMaxRounds() != null ? context.getMaxRounds() : 3);
        structuredOutput.set("rounds", roundsArray);

        return AgentExecutionResult.builder()
                .agentKey("DEBATE_CONSENSUS")
                .success(true)
                .status("SUCCESS")
                .output(consensusOutput)
                .structuredOutput(structuredOutput)
                .durationMs(totalDuration)
                .build();
    }

    private void reportStatus(String msg) {
        com.nageoffer.ai.ragent.infra.chat.StreamCallback cb =
                com.nageoffer.ai.ragent.agent.multiagent.core.AgentRunner.getStatusCallback().get();
        if (cb != null) cb.onStatus(msg);
    }

    // ─── 内部类 ──────
    private record ModeratorResult(boolean converged, int confidence, String output, int round) {
    }
}
