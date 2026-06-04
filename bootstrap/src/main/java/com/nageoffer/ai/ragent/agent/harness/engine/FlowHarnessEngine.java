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

package com.nageoffer.ai.ragent.agent.harness.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.action.domain.ActionResult;
import com.nageoffer.ai.ragent.agent.action.enums.ActionType;
import com.nageoffer.ai.ragent.agent.evaluator.core.AgentEvaluator;
import com.nageoffer.ai.ragent.agent.evaluator.core.AgentEvaluatorRegistry;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationContext;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationResult;
import com.nageoffer.ai.ragent.agent.harness.domain.HarnessContext;
import com.nageoffer.ai.ragent.agent.harness.domain.HarnessResult;
import com.nageoffer.ai.ragent.agent.memory.core.WorkflowMemoryService;
import com.nageoffer.ai.ragent.agent.reflection.core.AgentReflector;
import com.nageoffer.ai.ragent.agent.reflection.domain.ReflectionContext;
import com.nageoffer.ai.ragent.agent.reflection.domain.ReflectionResult;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowCheckpointDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowEdgeDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowEventDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowInstanceDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowNodeDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowNodeInstanceDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowCheckpointMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowEventMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowInstanceMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowNodeInstanceMapper;
import com.nageoffer.ai.ragent.agent.workflow.domain.AgentWorkflowDefinition;
import com.nageoffer.ai.ragent.agent.workflow.enums.HarnessType;
import com.nageoffer.ai.ragent.agent.workflow.enums.NodeExecutionStrategyType;
import com.nageoffer.ai.ragent.agent.workflow.enums.WorkflowEdgeType;
import com.nageoffer.ai.ragent.agent.workflow.enums.WorkflowNodeInstanceStatus;
import com.nageoffer.ai.ragent.agent.workflow.enums.WorkflowNodeType;
import com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionContext;
import com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionStrategy;
import com.nageoffer.ai.ragent.agent.workflow.strategy.NodeExecutionStrategyRegistry;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FlowHarnessEngine implements HarnessEngine {

    private static final int MAX_EXECUTION_STEPS = 100;

    private final AgentWorkflowInstanceMapper instanceMapper;
    private final AgentWorkflowNodeInstanceMapper nodeInstanceMapper;
    private final AgentWorkflowCheckpointMapper checkpointMapper;
    private final AgentWorkflowEventMapper eventMapper;
    private final NodeExecutionStrategyRegistry strategyRegistry;
    private final AgentEvaluatorRegistry evaluatorRegistry;
    private final AgentReflector reflector;
    private final WorkflowMemoryService workflowMemoryService;
    private final ObjectMapper objectMapper;

    @Override
    public String harnessType() {
        return HarnessType.FLOW.name();
    }

    @Override
    public HarnessResult run(HarnessContext context) {
        AgentWorkflowInstanceDO instance = context.getInstance();
        ObjectNode workflowContext = context.getWorkflowContext();
        String currentNodeKey = null;
        try {
            currentNodeKey = executeFlow(instance, context.getDefinition(), context.getInput(), workflowContext);
            return HarnessResult.success(workflowContext, currentNodeKey);
        } catch (Exception ex) {
            return HarnessResult.fail(workflowContext, currentNodeKey, ex.getMessage());
        }
    }

    private String executeFlow(AgentWorkflowInstanceDO instance, AgentWorkflowDefinition definition, JsonNode input, ObjectNode context) {
        Map<String, AgentWorkflowNodeDO> nodeMap = definition.getNodes().stream().collect(Collectors.toMap(AgentWorkflowNodeDO::getNodeKey, Function.identity()));
        Map<String, List<AgentWorkflowEdgeDO>> edgeMap = definition.getEdges().stream().collect(Collectors.groupingBy(AgentWorkflowEdgeDO::getSourceNodeKey));
        JsonNode workflowConfig = workflowMemoryService.parseConfig(definition.getWorkflow().getConfigJson());
        normalizeContext(context, input);
        appendEvent(instance.getId(), null, "USER_INPUT", "INFO", "用户输入", input);
        Map<String, Integer> reflectionRounds = new HashMap<>();
        // 上报 Workflow 启动状态
        reportStatus("📋 Workflow「" + definition.getWorkflow().getName() + "」开始执行 · "
                + definition.getNodes().size() + " 个节点 · Harness: " + definition.getWorkflow().getHarnessType());

        String current = resolveStartNode(definition);
        String lastNodeKey = null;
        int guard = 0;
        while (StringUtils.hasText(current)) {
            if (++guard > MAX_EXECUTION_STEPS) {
                throw new ClientException("工作流节点超过最大执行步数");
            }
            AgentWorkflowNodeDO node = nodeMap.get(current);
            if (node == null) {
                throw new ClientException("未找到节点: " + current);
            }
            lastNodeKey = current;
            persist(instance, current, context);
            saveCheckpoint(instance, current, context);
            // 节点开始
            String strategyLabel = resolveNodeStrategyLabel(node);
            long nodeStart = System.currentTimeMillis();
            String displayName = StringUtils.hasText(node.getNodeName()) ? node.getNodeName() : current;
            reportStatus("▶️ 进入节点「" + displayName + "」"
                    + " · 类型: " + node.getNodeType() + (strategyLabel.isEmpty() ? "" : " · " + strategyLabel));
            appendEvent(instance.getId(), null, "NODE_STARTED", "INFO", "节点开始执行: " + current, objectMapper.createObjectNode().put("nodeKey", current));
            ActionResult result = executeNode(instance, node, input, context);
            if (result.getOutput() != null) {
                context.set(node.getNodeKey(), result.getOutput());
                persist(instance, current, context);
            }
            float nodeDuration = (System.currentTimeMillis() - nodeStart) / 1000.0f;
            reportStatus("✅ 节点「" + displayName + "」完成 ("
                    + String.format("%.1f", nodeDuration) + "s)");
            appendEvent(instance.getId(), null, "NODE_COMPLETED", "INFO", "节点执行完成: " + current, objectMapper.valueToTree(result));
            updateShortTermContext(context, node, result);
            workflowMemoryService.compressIfNeeded(instance, context, input, workflowConfig, current, guard);
            persist(instance, current, context);
            if (!result.isSuccess()) {
                throw new ClientException(result.getErrorMessage());
            }
            if (isEvaluatorFailed(node, result)) {
                JsonNode config = parse(node.getConfigJson());
                String retryNodeKey = config.path("retryNodeKey").asText(config.path("targetNodeKey").asText(null));
                int maxReflectionRounds = config.path("maxReflectionRounds").asInt(0);
                int usedRounds = reflectionRounds.getOrDefault(node.getNodeKey(), 0);
                if (!StringUtils.hasText(retryNodeKey) || usedRounds >= maxReflectionRounds) {
                    appendEvent(instance.getId(), null, "EVALUATION_FAILED", "WARN", "Evaluator验收不通过", result.getOutput());
                    throw new ClientException("Evaluator验收不通过: " + result.getOutput().path("evaluation").path("reason").asText("未知原因"));
                }
                reflectionRounds.put(node.getNodeKey(), usedRounds + 1);
                current = reflectAndRollback(instance, node, retryNodeKey, input, context, result);
                continue;
            }
            if (WorkflowNodeType.END.name().equals(node.getNodeType())) {
                workflowMemoryService.compressOnFinish(instance, context, input, workflowConfig, current);
                persist(instance, current, context);
                break;
            }
            current = nextNode(edgeMap.getOrDefault(node.getNodeKey(), List.of()), result);
        }
        return lastNodeKey;
    }

    private boolean isEvaluatorFailed(AgentWorkflowNodeDO node, ActionResult result) {
        return WorkflowNodeType.EVALUATOR.name().equals(node.getNodeType()) && !result.getOutput().path("evaluation").path("passed").asBoolean(false);
    }

    private void normalizeContext(ObjectNode context, JsonNode input) {
        if (!context.has("input")) context.set("input", input == null ? objectMapper.createObjectNode() : input);
        if (!context.has("variables")) context.set("variables", objectMapper.createObjectNode());
        if (!context.has("lastOutput")) context.set("lastOutput", objectMapper.createObjectNode());
        if (!context.has("reflectionHints")) context.set("reflectionHints", objectMapper.createArrayNode());
        if (!context.has("toolResults")) context.set("toolResults", objectMapper.createObjectNode());
        if (!context.has("retrievedContext")) context.put("retrievedContext", "");
    }

    private void updateShortTermContext(ObjectNode context, AgentWorkflowNodeDO node, ActionResult result) {
        if (result.getOutput() != null) context.set("lastOutput", result.getOutput());
        if (ActionType.MCP_TOOL.name().equals(node.getActionType()) && result.getOutput() != null) {
            context.withObject("toolResults").set(node.getNodeKey(), result.getOutput());
        }
    }

    private ActionResult executeNode(AgentWorkflowInstanceDO instance, AgentWorkflowNodeDO node, JsonNode input, ObjectNode context) {
        Date start = new Date();
        AgentWorkflowNodeInstanceDO nodeInstance = AgentWorkflowNodeInstanceDO.builder()
                .instanceId(instance.getId()).workflowId(instance.getWorkflowId()).nodeKey(node.getNodeKey())
                .nodeName(node.getNodeName()).nodeType(node.getNodeType()).actionType(node.getActionType())
                .status(WorkflowNodeInstanceStatus.RUNNING.name()).inputJson(json(input))
                .retryCount(resolveRetryCount(context, node.getNodeKey())).startedAt(start).build();
        nodeInstanceMapper.insert(nodeInstance);
        try {
            ActionResult result;
            if (WorkflowNodeType.END.name().equals(node.getNodeType())) {
                result = ActionResult.success(objectMapper.createObjectNode().put("end", true));
            } else if (WorkflowNodeType.EVALUATOR.name().equals(node.getNodeType())) {
                result = executeEvaluator(instance, node, context);
            } else {
                result = executeStrategy(instance, node, input, context);
            }
            nodeInstance.setStatus(result.isSuccess() ? WorkflowNodeInstanceStatus.SUCCESS.name() : WorkflowNodeInstanceStatus.FAILED.name());
            nodeInstance.setOutputJson(toActionResultJson(result));
            nodeInstance.setErrorMessage(result.getErrorMessage());
            return result;
        } catch (Exception ex) {
            nodeInstance.setStatus(WorkflowNodeInstanceStatus.FAILED.name());
            nodeInstance.setErrorMessage(ex.getMessage());
            throw ex;
        } finally {
            Date end = new Date();
            nodeInstance.setCompletedAt(end);
            nodeInstance.setDurationMs(end.getTime() - start.getTime());
            nodeInstanceMapper.updateById(nodeInstance);
        }
    }

    private ActionResult executeStrategy(AgentWorkflowInstanceDO instance, AgentWorkflowNodeDO node, JsonNode input, ObjectNode context) {
        JsonNode config = parse(node.getConfigJson());
        String strategyType = resolveStrategyType(node, config);
        NodeExecutionStrategy strategy = strategyRegistry.getRequired(strategyType);
        return strategy.execute(NodeExecutionContext.builder()
                .instance(instance)
                .node(node)
                .originalInput(input)
                .workflowContext(context)
                .nodeConfig(config == null ? objectMapper.createObjectNode() : config)
                .build());
    }

    private String resolveStrategyType(AgentWorkflowNodeDO node, JsonNode config) {
        String actionType = node == null ? null : node.getActionType();
        if (StringUtils.hasText(actionType) && !ActionType.NOOP.name().equals(actionType)) {
            return NodeExecutionStrategyType.PIPELINE.name();
        }
        String strategyType = config == null ? null : config.path("strategyType").asText(null);
        return StringUtils.hasText(strategyType) ? strategyType : NodeExecutionStrategyType.PIPELINE.name();
    }

    private ActionResult executeEvaluator(AgentWorkflowInstanceDO instance, AgentWorkflowNodeDO node, ObjectNode context) {
        JsonNode config = parse(node.getConfigJson());
        String evaluatorType = config.path("evaluatorType").asText("RULE");
        String targetNodeKey = config.path("targetNodeKey").asText(null);
        AgentEvaluator evaluator = evaluatorRegistry.getRequired(evaluatorType);
        EvaluationResult evaluation = evaluator.evaluate(EvaluationContext.builder().instanceId(instance.getId())
                .workflowId(instance.getWorkflowId()).nodeKey(node.getNodeKey()).workflowContext(context)
                .targetOutput(StringUtils.hasText(targetNodeKey) ? context.path(targetNodeKey) : objectMapper.nullNode())
                .config(config).build());
        ObjectNode output = objectMapper.createObjectNode();
        output.set("evaluation", objectMapper.valueToTree(evaluation));
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("nodeType", WorkflowNodeType.EVALUATOR.name()).put("evaluatorType", evaluatorType).put("targetNodeKey", targetNodeKey);
        return ActionResult.success(output, metadata);
    }

    private String reflectAndRollback(AgentWorkflowInstanceDO instance, AgentWorkflowNodeDO node, String retryNodeKey, JsonNode input, ObjectNode context, ActionResult result) {
        JsonNode config = parse(node.getConfigJson());
        EvaluationResult evaluation = objectMapper.convertValue(result.getOutput().path("evaluation"), EvaluationResult.class);
        ReflectionResult reflection = reflector.reflect(ReflectionContext.builder().instanceId(instance.getId()).workflowId(instance.getWorkflowId())
                .evaluatorNodeKey(node.getNodeKey()).retryNodeKey(retryNodeKey).workflowContext(context).originalInput(input)
                .previousOutput(context.path(config.path("targetNodeKey").asText(retryNodeKey))).evaluationResult(evaluation)
                .config(config.path("reflection")).build());
        ObjectNode restored = loadCheckpoint(instance.getId(), retryNodeKey);
        context.removeAll();
        context.setAll(restored);
        ObjectNode reflectionNode = objectMapper.createObjectNode();
        reflectionNode.put("retryNodeKey", retryNodeKey).put("evaluatorNodeKey", node.getNodeKey()).put("reason", evaluation.getReason())
                .put("suggestion", evaluation.getSuggestion()).put("revisedPrompt", reflection.getRevisedPrompt()).put("retry", reflection.isRetry())
                .put("retryCount", resolveRetryCount(context, retryNodeKey) + 1);
        context.set("reflection", reflectionNode);
        context.set("reflection_" + node.getNodeKey(), reflectionNode);
        persist(instance, retryNodeKey, context);
        appendEvent(instance.getId(), null, "REFLECTION_TRIGGERED", "WARN", "Reflection生成修正提示", reflectionNode);
        appendEvent(instance.getId(), null, "WORKFLOW_ROLLBACK", "WARN", "Evaluator不通过，回滚到节点: " + retryNodeKey, reflectionNode);
        return retryNodeKey;
    }

    private void saveCheckpoint(AgentWorkflowInstanceDO instance, String nodeKey, ObjectNode context) {
        checkpointMapper.insert(AgentWorkflowCheckpointDO.builder().instanceId(instance.getId()).nodeKey(nodeKey)
                .checkpointType("BEFORE_NODE").contextJson(context.toString()).createTime(new Date()).build());
    }

    private ObjectNode loadCheckpoint(String instanceId, String nodeKey) {
        AgentWorkflowCheckpointDO checkpoint = checkpointMapper.selectList(new LambdaQueryWrapper<AgentWorkflowCheckpointDO>()
                        .eq(AgentWorkflowCheckpointDO::getInstanceId, instanceId).eq(AgentWorkflowCheckpointDO::getNodeKey, nodeKey)
                        .orderByDesc(AgentWorkflowCheckpointDO::getCreateTime)).stream().findFirst()
                .orElseThrow(() -> new ClientException("未找到Checkpoint: " + nodeKey));
        JsonNode parsed = parse(checkpoint.getContextJson());
        return parsed != null && parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
    }

    private String resolveStartNode(AgentWorkflowDefinition definition) {
        Set<String> targetNodeKeys = definition.getEdges().stream().map(AgentWorkflowEdgeDO::getTargetNodeKey).collect(Collectors.toSet());
        List<AgentWorkflowNodeDO> startNodes = definition.getNodes().stream().filter(node -> !targetNodeKeys.contains(node.getNodeKey()))
                .sorted(Comparator.comparing(node -> node.getNodeOrder() == null ? 0 : node.getNodeOrder())).toList();
        if (startNodes.isEmpty()) throw new ClientException("工作流缺少开始节点");
        if (startNodes.size() > 1) throw new ClientException("FlowHarness暂只支持一个开始节点");
        return startNodes.get(0).getNodeKey();
    }

    private String nextNode(List<AgentWorkflowEdgeDO> edges, ActionResult result) {
        return edges.stream().filter(edge -> WorkflowEdgeType.SUCCESS.name().equals(edge.getEdgeType()) && result.isSuccess()).findFirst()
                .or(() -> edges.stream().filter(edge -> WorkflowEdgeType.DEFAULT.name().equals(edge.getEdgeType())).findFirst())
                .map(AgentWorkflowEdgeDO::getTargetNodeKey).orElse(null);
    }

    private void persist(AgentWorkflowInstanceDO instance, String nodeKey, ObjectNode context) {
        instance.setCurrentNodeKey(nodeKey);
        instance.setContextJson(context.toString());
        instanceMapper.updateById(instance);
    }

    private void appendEvent(String instanceId, String nodeInstanceId, String type, String level, String content, JsonNode payload) {
        eventMapper.insert(AgentWorkflowEventDO.builder().instanceId(instanceId).nodeInstanceId(nodeInstanceId).eventType(type)
                .eventLevel(level).content(content).payloadJson(json(payload)).importanceScore(resolveImportance(type, level))
                .createTime(new Date()).build());
    }

    private int resolveImportance(String type, String level) {
        if ("ERROR".equals(level)) return 90;
        return switch (type) {
            case "USER_INPUT" -> 90;
            case "TOOL_RESULT" -> 80;
            case "EVALUATION_FAILED", "REFLECTION_TRIGGERED" -> 85;
            case "WORKFLOW_ROLLBACK", "REVIEW_REQUIRED" -> 80;
            case "NODE_COMPLETED" -> 60;
            case "DEBUG_LOG" -> 20;
            default -> 50;
        };
    }

    private int resolveRetryCount(ObjectNode context, String nodeKey) {
        return context.path("reflection").path("retryNodeKey").asText("").equals(nodeKey) ? context.path("reflection").path("retryCount").asInt(0) : 0;
    }

    private String toActionResultJson(ActionResult result) { try { return objectMapper.writeValueAsString(result); } catch (Exception ex) { throw new ClientException("ActionResult序列化失败"); } }
    private String json(JsonNode node) { return node == null || node.isNull() ? null : node.toString(); }
    private JsonNode parse(String raw) { if (!StringUtils.hasText(raw)) return null; try { return objectMapper.readTree(raw); } catch (Exception ex) { throw new ClientException("JSON解析失败"); } }

    private void reportStatus(String msg) {
        com.nageoffer.ai.ragent.infra.chat.StreamCallback cb = com.nageoffer.ai.ragent.agent.multiagent.core.AgentRunner.getStatusCallback().get();
        if (cb != null) cb.onStatus(msg);
    }

    private String resolveNodeStrategyLabel(AgentWorkflowNodeDO node) {
        JsonNode config = parse(node.getConfigJson());
        if (config == null) return "";
        String strategy = config.path("strategyType").asText(null);
        if ("AGENT_TEAM".equals(strategy)) {
            return "策略: Agent Team";
        }
        if (strategy != null) return "策略: " + strategy;
        if (node.getActionType() != null && !"NOOP".equals(node.getActionType())) return "动作: " + node.getActionType();
        return "";
    }
}
