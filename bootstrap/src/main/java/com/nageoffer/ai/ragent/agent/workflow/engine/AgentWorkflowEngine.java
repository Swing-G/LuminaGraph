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

package com.nageoffer.ai.ragent.agent.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.harness.domain.HarnessContext;
import com.nageoffer.ai.ragent.agent.harness.domain.HarnessResult;
import com.nageoffer.ai.ragent.agent.harness.engine.HarnessEngine;
import com.nageoffer.ai.ragent.agent.harness.engine.HarnessEngineRegistry;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowRunRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowInstanceVO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowDefinitionDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowEdgeDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowEventDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowInstanceDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.AgentWorkflowNodeDO;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowDefinitionMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowEdgeMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowEventMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowInstanceMapper;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.AgentWorkflowNodeMapper;
import com.nageoffer.ai.ragent.agent.workflow.domain.AgentWorkflowDefinition;
import com.nageoffer.ai.ragent.agent.workflow.enums.WorkflowInstanceStatus;
import com.nageoffer.ai.ragent.agent.workflow.enums.WorkflowStatus;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * Agent Workflow入口引擎
 */
@Component
@RequiredArgsConstructor
public class AgentWorkflowEngine {

    private final AgentWorkflowDefinitionMapper workflowMapper;
    private final AgentWorkflowNodeMapper nodeMapper;
    private final AgentWorkflowEdgeMapper edgeMapper;
    private final AgentWorkflowInstanceMapper instanceMapper;
    private final AgentWorkflowEventMapper eventMapper;
    private final HarnessEngineRegistry harnessEngineRegistry;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowInstanceVO run(String workflowId, AgentWorkflowRunRequest request) {
        AgentWorkflowDefinition definition = getDefinition(workflowId);
        AgentWorkflowDefinitionDO workflow = definition.getWorkflow();
        if (!WorkflowStatus.ENABLED.name().equals(workflow.getStatus())) {
            throw new ClientException("工作流未启用");
        }
        ObjectNode context = objectMapper.createObjectNode();
        JsonNode input = request == null ? null : request.getInput();
        if (input != null) {
            context.set("input", input);
        }
        AgentWorkflowInstanceDO instance = AgentWorkflowInstanceDO.builder()
                .workflowId(workflowId)
                .workflowVersion(workflow.getVersion())
                .harnessType(workflow.getHarnessType())
                .businessType(request == null ? null : request.getBusinessType())
                .businessId(request == null ? null : request.getBusinessId())
                .userId(UserContext.getUserId())
                .status(WorkflowInstanceStatus.RUNNING.name())
                .inputJson(json(input))
                .contextJson(context.toString())
                .startedAt(new Date())
                .build();
        instanceMapper.insert(instance);
        appendEvent(instance.getId(), null, "WORKFLOW_STARTED", "INFO", "工作流开始执行", null);

        HarnessResult result = runHarness(definition, instance, input, context);
        instance.setStatus(result.isSuccess() ? WorkflowInstanceStatus.COMPLETED.name() : WorkflowInstanceStatus.FAILED.name());
        instance.setCurrentNodeKey(result.getCurrentNodeKey());
        instance.setContextJson(json(result.getContext()));
        instance.setOutputJson(json(result.getOutput()));
        instance.setErrorMessage(result.getErrorMessage());
        instance.setCompletedAt(new Date());
        instanceMapper.updateById(instance);

        if (result.isSuccess()) {
            appendEvent(instance.getId(), null, "WORKFLOW_COMPLETED", "INFO", "工作流执行完成", null);
        } else {
            appendEvent(instance.getId(), null, "WORKFLOW_FAILED", "ERROR", result.getErrorMessage(), null);
        }
        return toInstanceVO(instance);
    }

    private HarnessResult runHarness(AgentWorkflowDefinition definition, AgentWorkflowInstanceDO instance, JsonNode input, ObjectNode context) {
        HarnessEngine harnessEngine = harnessEngineRegistry.getRequired(definition.getWorkflow().getHarnessType());
        return harnessEngine.run(HarnessContext.builder()
                .definition(definition)
                .instance(instance)
                .input(input)
                .workflowContext(context)
                .build());
    }

    private AgentWorkflowDefinition getDefinition(String workflowId) {
        AgentWorkflowDefinitionDO workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new ClientException("未找到工作流");
        }
        List<AgentWorkflowNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowNodeDO>()
                .eq(AgentWorkflowNodeDO::getWorkflowId, workflowId)
                .eq(AgentWorkflowNodeDO::getDeleted, 0)
                .orderByAsc(AgentWorkflowNodeDO::getNodeOrder));
        List<AgentWorkflowEdgeDO> edges = edgeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowEdgeDO>()
                .eq(AgentWorkflowEdgeDO::getWorkflowId, workflowId)
                .eq(AgentWorkflowEdgeDO::getDeleted, 0)
                .orderByAsc(AgentWorkflowEdgeDO::getPriority));
        return AgentWorkflowDefinition.builder()
                .workflow(workflow)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private AgentWorkflowInstanceVO toInstanceVO(AgentWorkflowInstanceDO instance) {
        AgentWorkflowInstanceVO vo = new AgentWorkflowInstanceVO();
        vo.setId(instance.getId());
        vo.setWorkflowId(instance.getWorkflowId());
        vo.setWorkflowVersion(instance.getWorkflowVersion());
        vo.setHarnessType(instance.getHarnessType());
        vo.setBusinessType(instance.getBusinessType());
        vo.setBusinessId(instance.getBusinessId());
        vo.setUserId(instance.getUserId());
        vo.setStatus(instance.getStatus());
        vo.setInput(parse(instance.getInputJson()));
        vo.setContext(parse(instance.getContextJson()));
        vo.setOutput(parse(instance.getOutputJson()));
        vo.setErrorMessage(instance.getErrorMessage());
        vo.setCurrentNodeKey(instance.getCurrentNodeKey());
        vo.setStartedAt(instance.getStartedAt());
        vo.setCompletedAt(instance.getCompletedAt());
        return vo;
    }

    private void appendEvent(String instanceId, String nodeInstanceId, String type, String level, String content, JsonNode payload) {
        eventMapper.insert(AgentWorkflowEventDO.builder()
                .instanceId(instanceId)
                .nodeInstanceId(nodeInstanceId)
                .eventType(type)
                .eventLevel(level)
                .content(content)
                .payloadJson(json(payload))
                .importanceScore("ERROR".equals(level) ? 90 : 50)
                .createTime(new Date())
                .build());
    }

    private String json(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }

    private JsonNode parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new ClientException("JSON解析失败");
        }
    }
}
