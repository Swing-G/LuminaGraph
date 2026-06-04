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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.multiagent.dao.entity.AgentDefinitionDO;
import com.nageoffer.ai.ragent.agent.multiagent.dao.entity.AgentTeamDefinitionDO;
import com.nageoffer.ai.ragent.agent.multiagent.dao.mapper.AgentDefinitionMapper;
import com.nageoffer.ai.ragent.agent.multiagent.dao.mapper.AgentTeamDefinitionMapper;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentConfig;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentExecutionResult;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentTeamConfig;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentMergeStrategy;
import com.nageoffer.ai.ragent.agent.multiagent.enums.AgentTeamTopology;
import com.nageoffer.ai.ragent.agent.multiagent.topology.TeamTopologyContext;
import com.nageoffer.ai.ragent.agent.multiagent.topology.TeamTopologyStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent Team总协调器
 * <p>
 * 负责：
 * 1. 从DB加载Team和Agent定义
 * 2. 初始化Blackboard
 * 3. 根据拓扑选择并执行策略
 * 4. 调用ResultMergeEngine合并结果
 * 5. 产出最终ActionResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTeamOrchestrator {

    private final AgentTeamDefinitionMapper teamMapper;
    private final AgentDefinitionMapper agentMapper;
    private final TeamTopologyStrategyRegistry topologyRegistry;
    private final ResultMergeEngine mergeEngine;
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;

    /**
     * 通过Team ID执行Agent Team
     */
    public AgentExecutionResult execute(String teamId, String instanceId, String nodeInstanceId,
                                         JsonNode originalInput, ObjectNode workflowContext) {
        log.info("AgentTeamOrchestrator启动: teamId={}, instanceId={}", teamId, instanceId);

        // 1. 加载Team定义
        AgentTeamDefinitionDO teamDO = teamMapper.selectById(teamId);
        if (teamDO == null) {
            return AgentExecutionResult.fail("ORCHESTRATOR", "Agent Team不存在: " + teamId, 0);
        }

        // 2. 加载Agent定义
        List<AgentDefinitionDO> agentDOs = agentMapper.selectByTeamId(teamId);
        if (agentDOs.isEmpty()) {
            return AgentExecutionResult.fail("ORCHESTRATOR", "Agent Team无Agent成员: " + teamId, 0);
        }

        // 3. 转换为领域模型
        AgentTeamConfig teamConfig = buildTeamConfig(teamDO, agentDOs);

        // 上报 Agent Team 启动
        reportStatus("🤖 Agent Team「" + teamConfig.getName() + "」启动 · "
                + teamConfig.getAgents().size() + " Agent · 拓扑: " + teamConfig.getTopology()
                + " · 合并: " + teamConfig.getMergeStrategy());

        // 4. 构建拓扑上下文
        TeamTopologyContext topologyContext = TeamTopologyContext.builder()
                .instanceId(instanceId)
                .nodeInstanceId(nodeInstanceId)
                .agents(teamConfig.getAgents())
                .originalInput(originalInput)
                .workflowContext(workflowContext)
                .maxRounds(teamConfig.getMaxRounds())
                .currentRound(1)
                .mergeStrategy(teamDO.getMergeStrategy())
                .build();

        // 5. 选择并执行拓扑策略
        AgentExecutionResult result = topologyRegistry.getRequired(teamConfig.getTopology().name())
                .execute(topologyContext);

        // 6. 写入Blackboard（记录最终结果）
        try {
            blackboardService.writeEntry(nodeInstanceId, 1, "ORCHESTRATOR",
                    "DECISION", result.getOutput(), null);
        } catch (Exception e) {
            log.warn("Blackboard写入失败", e);
        }

        log.info("AgentTeamOrchestrator完成: teamId={}, success={}", teamId, result.isSuccess());
        return result;
    }

    /**
     * 通过内联Agent配置执行（不经过DB）
     */
    public AgentExecutionResult executeInline(List<AgentConfig> agents, String topology,
                                               String mergeStrategy, Integer maxRounds,
                                               String instanceId, String nodeInstanceId,
                                               JsonNode originalInput, ObjectNode workflowContext) {
        log.info("AgentTeamOrchestrator启动(inline): agent数={}, topology={}", agents.size(), topology);

        TeamTopologyContext topologyContext = TeamTopologyContext.builder()
                .instanceId(instanceId)
                .nodeInstanceId(nodeInstanceId)
                .agents(agents)
                .originalInput(originalInput)
                .workflowContext(workflowContext)
                .maxRounds(maxRounds != null ? maxRounds : 1)
                .currentRound(1)
                .build();

        AgentExecutionResult result = topologyRegistry.getRequired(topology)
                .execute(topologyContext);

        log.info("AgentTeamOrchestrator完成(inline): success={}", result.isSuccess());
        return result;
    }

    private AgentTeamConfig buildTeamConfig(AgentTeamDefinitionDO teamDO, List<AgentDefinitionDO> agentDOs) {
        List<AgentConfig> agents = agentDOs.stream()
                .sorted(Comparator.comparing(AgentDefinitionDO::getAgentOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::buildAgentConfig)
                .collect(Collectors.toList());

        return AgentTeamConfig.builder()
                .teamId(teamDO.getId())
                .name(teamDO.getName())
                .topology(AgentTeamTopology.valueOf(teamDO.getTopology()))
                .maxRounds(teamDO.getMaxRounds())
                .mergeStrategy(AgentMergeStrategy.valueOf(teamDO.getMergeStrategy()))
                .agents(agents)
                .build();
    }

    private AgentConfig buildAgentConfig(AgentDefinitionDO agentDO) {
        AgentConfig.AgentConfigBuilder builder = AgentConfig.builder()
                .agentKey(agentDO.getAgentKey())
                .agentName(agentDO.getAgentName())
                .role(agentDO.getRole())
                .goal(agentDO.getGoal())
                .modelId(agentDO.getModelId())
                .agentOrder(agentDO.getAgentOrder())
                .isLeader(agentDO.getIsLeader())
                .memoryStrategy(agentDO.getMemoryStrategy());

        // 解析toolNames JSON
        try {
            if (agentDO.getToolNames() != null) {
                JsonNode toolsNode = objectMapper.readTree(agentDO.getToolNames());
                List<String> toolNames = objectMapper.convertValue(toolsNode, List.class);
                builder.toolNames(toolNames);
            }
        } catch (Exception e) {
            log.warn("解析Agent toolNames失败: agentKey={}", agentDO.getAgentKey(), e);
        }

        // 解析llmConfig JSON
        try {
            if (agentDO.getLlmConfig() != null) {
                JsonNode configNode = objectMapper.readTree(agentDO.getLlmConfig());
                if (configNode.has("strategyType")) {
                    builder.strategyType(configNode.get("strategyType").asText());
                }
                if (configNode.has("temperature")) {
                    builder.temperature(configNode.get("temperature").asDouble());
                }
                if (configNode.has("maxTokens")) {
                    builder.maxTokens(configNode.get("maxTokens").asInt());
                }
                if (configNode.has("thinking")) {
                    builder.thinking(configNode.get("thinking").asBoolean());
                }
            }
        } catch (Exception e) {
            log.warn("解析Agent llmConfig失败: agentKey={}", agentDO.getAgentKey(), e);
        }

        return builder.build();
    }

    private void reportStatus(String msg) {
        com.nageoffer.ai.ragent.infra.chat.StreamCallback cb = com.nageoffer.ai.ragent.agent.multiagent.core.AgentRunner.getStatusCallback().get();
        if (cb != null) cb.onStatus(msg);
    }
}
