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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionResult;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentMergeStrategy;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 结果合并引擎
 * <p>
 * 支持多种合并策略：CONSENSUS、MAJORITY、LEADER、FIRST。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultMergeEngine {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 合并多个Agent的执行结果
     */
    public AgentExecutionResult merge(List<AgentExecutionResult> results, AgentMergeStrategy strategy) {
        if (results.isEmpty()) {
            return AgentExecutionResult.fail("MERGE_EMPTY", "无可用结果进行合并", 0);
        }

        List<AgentExecutionResult> successful = results.stream()
                .filter(AgentExecutionResult::isSuccess)
                .collect(Collectors.toList());

        if (successful.isEmpty()) {
            return AgentExecutionResult.fail("MERGE_ALL_FAILED",
                    "所有Agent均失败，无法合并", 0);
        }

        if (successful.size() == 1) {
            return successful.get(0);
        }

        return switch (strategy) {
            case SYNTHESIS -> mergeBySynthesis(successful);
            case FIRST -> mergeFirst(successful);
            case CONSENSUS -> mergeByConsensus(successful);
            case MAJORITY -> mergeByMajority(successful);
            case LEADER -> mergeByLeader(successful);
            default -> mergeBySynthesis(successful);
        };
    }

    /**
     * FIRST: 第一个成功的结果
     */
    private AgentExecutionResult mergeFirst(List<AgentExecutionResult> results) {
        return results.get(0);
    }

    /**
     * SYNTHESIS: 使用LLM将多个Agent的分析综合为一份完整报告（适用于PARALLEL）
     */
    private AgentExecutionResult mergeBySynthesis(List<AgentExecutionResult> results) {
        StringBuilder allOutputs = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            allOutputs.append("Agent ").append(i + 1).append(" (")
                    .append(results.get(i).getAgentKey()).append(") 分析:\n")
                    .append(results.get(i).getOutput()).append("\n\n");
        }

        String prompt = "以下是多位专家对同一个问题的分析。请直接告诉用户答案，"
                + "像客服回复一样自然、简洁，不要用 综合分析报告 这类标题：\n\n" + allOutputs;

        try {
            String synthesis = llmService.chat(prompt);
            return AgentExecutionResult.builder()
                    .agentKey("SYNTHESIS")
                    .success(true)
                    .status("SUCCESS")
                    .output(synthesis)
                    .build();
        } catch (Exception e) {
            log.warn("综合合并失败，回退到FIRST策略", e);
            return mergeFirst(results);
        }
    }

    /**
     * CONSENSUS: 使用LLM作为Moderator评估一致性并合成
     */
    private AgentExecutionResult mergeByConsensus(List<AgentExecutionResult> results) {
        StringBuilder allOutputs = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            allOutputs.append("Agent ").append(i + 1).append(" (")
                    .append(results.get(i).getAgentKey()).append("):\n")
                    .append(results.get(i).getOutput()).append("\n\n");
        }

        String prompt = "以下是对同一任务的多个专家的独立分析结果。请评估他们的共识程度，"
                + "找出共同结论和分歧点，并合成一份综合分析报告：\n\n" + allOutputs;

        try {
            String synthesis = llmService.chat(prompt);
            return AgentExecutionResult.builder()
                    .agentKey("CONSENSUS_SYNTHESIS")
                    .success(true)
                    .status("SUCCESS")
                    .output(synthesis)
                    .build();
        } catch (Exception e) {
            log.warn("共识合并失败，回退到FIRST策略", e);
            return mergeFirst(results);
        }
    }

    /**
     * MAJORITY: 多数结果一致时采用多数意见
     */
    private AgentExecutionResult mergeByMajority(List<AgentExecutionResult> results) {
        // 简化实现：使用LLM评估多数意见
        return mergeByConsensus(results);
    }

    /**
     * LEADER: 由Leader Agent的结果为准
     */
    private AgentExecutionResult mergeByLeader(List<AgentExecutionResult> results) {
        // Leader结果在列表中排第一位（由Orchestrator保证）
        return results.get(0);
    }

    /**
     * 构建合并后的结构化输出
     */
    public ObjectNode buildMergedOutput(List<AgentExecutionResult> results) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("totalAgents", results.size());
        output.put("successCount", results.stream().filter(AgentExecutionResult::isSuccess).count());

        ArrayNode agentResults = objectMapper.createArrayNode();
        for (AgentExecutionResult r : results) {
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("agentKey", r.getAgentKey());
            entry.put("success", r.isSuccess());
            entry.put("output", r.getOutput() != null ? r.getOutput() : "");
            entry.put("errorMessage", r.getErrorMessage() != null ? r.getErrorMessage() : "");
            entry.put("durationMs", r.getDurationMs() != null ? r.getDurationMs() : 0);
            agentResults.add(entry);
        }
        output.set("agentResults", agentResults);

        return output;
    }
}
