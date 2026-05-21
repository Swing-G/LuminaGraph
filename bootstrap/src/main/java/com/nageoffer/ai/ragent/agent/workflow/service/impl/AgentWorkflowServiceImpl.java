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

package com.nageoffer.ai.ragent.agent.workflow.service.impl;

import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.*;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.*;
import com.nageoffer.ai.ragent.agent.workflow.dao.entity.*;
import com.nageoffer.ai.ragent.agent.workflow.dao.mapper.*;
import com.nageoffer.ai.ragent.agent.workflow.domain.AgentWorkflowDefinition;
import com.nageoffer.ai.ragent.agent.workflow.engine.AgentWorkflowEngine;
import com.nageoffer.ai.ragent.agent.workflow.enums.*;
import com.nageoffer.ai.ragent.agent.workflow.service.AgentWorkflowService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentWorkflowServiceImpl implements AgentWorkflowService {
    private final AgentWorkflowDefinitionMapper workflowMapper;
    private final AgentWorkflowNodeMapper nodeMapper;
    private final AgentWorkflowEdgeMapper edgeMapper;
    private final AgentWorkflowInstanceMapper instanceMapper;
    private final AgentWorkflowNodeInstanceMapper nodeInstanceMapper;
    private final AgentWorkflowEventMapper eventMapper;
    private final ObjectMapper objectMapper;
    @Lazy private final AgentWorkflowEngine workflowEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowVO create(AgentWorkflowCreateRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        AgentWorkflowDefinitionDO workflow = AgentWorkflowDefinitionDO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .workflowType(request.getWorkflowType())
                .harnessType(def(request.getHarnessType(), HarnessType.FLOW.name()))
                .version(1)
                .status(def(request.getStatus(), WorkflowStatus.DRAFT.name()))
                .inputSchema(json(request.getInputSchema()))
                .outputSchema(json(request.getOutputSchema()))
                .configJson(json(request.getConfig()))
                .createdBy(UserContext.getUsername())
                .updatedBy(UserContext.getUsername())
                .build();
        workflowMapper.insert(workflow);
        saveNodes(workflow.getId(), request.getNodes());
        saveEdges(workflow.getId(), request.getEdges());
        return get(workflow.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowVO update(String workflowId, AgentWorkflowUpdateRequest request) {
        AgentWorkflowDefinitionDO workflow = requiredWorkflow(workflowId);
        if (StringUtils.hasText(request.getName())) workflow.setName(request.getName());
        if (request.getDescription() != null) workflow.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getWorkflowType())) workflow.setWorkflowType(request.getWorkflowType());
        if (StringUtils.hasText(request.getHarnessType())) workflow.setHarnessType(request.getHarnessType());
        if (StringUtils.hasText(request.getStatus())) workflow.setStatus(request.getStatus());
        if (request.getConfig() != null) workflow.setConfigJson(json(request.getConfig()));
        workflow.setVersion(workflow.getVersion() == null ? 1 : workflow.getVersion() + 1);
        workflow.setUpdatedBy(UserContext.getUsername());
        workflowMapper.updateById(workflow);
        if (request.getNodes() != null) saveNodes(workflowId, request.getNodes());
        if (request.getEdges() != null) saveEdges(workflowId, request.getEdges());
        return get(workflowId);
    }

    @Override
    public AgentWorkflowVO get(String workflowId) {
        AgentWorkflowDefinitionDO workflow = requiredWorkflow(workflowId);
        AgentWorkflowVO vo = new AgentWorkflowVO();
        vo.setId(workflow.getId());
        vo.setName(workflow.getName());
        vo.setDescription(workflow.getDescription());
        vo.setWorkflowType(workflow.getWorkflowType());
        vo.setHarnessType(workflow.getHarnessType());
        vo.setVersion(workflow.getVersion());
        vo.setStatus(workflow.getStatus());
        vo.setConfig(parse(workflow.getConfigJson()));
        vo.setCreateTime(workflow.getCreateTime());
        vo.setUpdateTime(workflow.getUpdateTime());
        vo.setNodes(nodes(workflowId).stream().map(this::nodeVO).toList());
        vo.setEdges(edges(workflowId).stream().map(this::edgeVO).toList());
        return vo;
    }

    @Override
    public IPage<AgentWorkflowVO> page(Page<AgentWorkflowVO> page, String keyword, String status) {
        IPage<AgentWorkflowDefinitionDO> result = workflowMapper.selectPage(new Page<>(page.getCurrent(), page.getSize()),
                new LambdaQueryWrapper<AgentWorkflowDefinitionDO>()
                        .eq(AgentWorkflowDefinitionDO::getDeleted, 0)
                        .like(StringUtils.hasText(keyword), AgentWorkflowDefinitionDO::getName, keyword)
                        .eq(StringUtils.hasText(status), AgentWorkflowDefinitionDO::getStatus, status)
                        .orderByDesc(AgentWorkflowDefinitionDO::getUpdateTime));
        Page<AgentWorkflowVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(each -> get(each.getId())).toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workflowId) {
        requiredWorkflow(workflowId);
        workflowMapper.deleteById(workflowId);
        nodeMapper.delete(new LambdaQueryWrapper<AgentWorkflowNodeDO>().eq(AgentWorkflowNodeDO::getWorkflowId, workflowId));
        edgeMapper.delete(new LambdaQueryWrapper<AgentWorkflowEdgeDO>().eq(AgentWorkflowEdgeDO::getWorkflowId, workflowId));
    }

    @Override
    public AgentWorkflowDefinition getDefinition(String workflowId) {
        return AgentWorkflowDefinition.builder().workflow(requiredWorkflow(workflowId)).nodes(nodes(workflowId)).edges(edges(workflowId)).build();
    }

    @Override
    public AgentWorkflowInstanceVO run(String workflowId, AgentWorkflowRunRequest request) { return workflowEngine.run(workflowId, request); }

    @Override
    public AgentWorkflowInstanceVO getInstance(String instanceId) {
        AgentWorkflowInstanceDO instance = instanceMapper.selectById(instanceId);
        Assert.notNull(instance, () -> new ClientException("未找到工作流实例"));
        return instanceVO(instance, true);
    }

    @Override
    public IPage<AgentWorkflowInstanceVO> pageInstances(Page<AgentWorkflowInstanceVO> page, String workflowId) {
        IPage<AgentWorkflowInstanceDO> result = instanceMapper.selectPage(new Page<>(page.getCurrent(), page.getSize()),
                new LambdaQueryWrapper<AgentWorkflowInstanceDO>()
                        .eq(AgentWorkflowInstanceDO::getDeleted, 0)
                        .eq(StringUtils.hasText(workflowId), AgentWorkflowInstanceDO::getWorkflowId, workflowId)
                        .orderByDesc(AgentWorkflowInstanceDO::getCreateTime));
        Page<AgentWorkflowInstanceVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(instance -> instanceVO(instance, false)).toList());
        return voPage;
    }

    @Override
    public AgentWorkflowVO findEnabledByType(String workflowType) {
        if (!StringUtils.hasText(workflowType)) return null;
        return workflowMapper.selectList(new LambdaQueryWrapper<AgentWorkflowDefinitionDO>()
                        .eq(AgentWorkflowDefinitionDO::getDeleted, 0)
                        .eq(AgentWorkflowDefinitionDO::getWorkflowType, workflowType)
                        .eq(AgentWorkflowDefinitionDO::getStatus, WorkflowStatus.ENABLED.name())
                        .orderByDesc(AgentWorkflowDefinitionDO::getUpdateTime))
                .stream()
                .findFirst()
                .map(workflow -> get(workflow.getId()))
                .orElse(null);
    }
    private void saveNodes(String workflowId, List<AgentWorkflowNodeRequest> list) {
        nodeMapper.delete(new LambdaQueryWrapper<AgentWorkflowNodeDO>().eq(AgentWorkflowNodeDO::getWorkflowId, workflowId));
        if (list == null) return;
        for (AgentWorkflowNodeRequest n : list) nodeMapper.insert(AgentWorkflowNodeDO.builder().workflowId(workflowId).nodeKey(n.getNodeKey()).nodeName(n.getNodeName()).nodeType(n.getNodeType()).actionType(n.getActionType()).configJson(json(n.getConfig())).retryLimit(n.getRetryLimit() == null ? 0 : n.getRetryLimit()).nodeOrder(n.getNodeOrder() == null ? 0 : n.getNodeOrder()).build());
    }

    private void saveEdges(String workflowId, List<AgentWorkflowEdgeRequest> list) {
        edgeMapper.delete(new LambdaQueryWrapper<AgentWorkflowEdgeDO>().eq(AgentWorkflowEdgeDO::getWorkflowId, workflowId));
        if (list == null) return;
        for (AgentWorkflowEdgeRequest e : list) edgeMapper.insert(AgentWorkflowEdgeDO.builder().workflowId(workflowId).sourceNodeKey(e.getSourceNodeKey()).targetNodeKey(e.getTargetNodeKey()).edgeType(def(e.getEdgeType(), WorkflowEdgeType.DEFAULT.name())).priority(e.getPriority() == null ? 0 : e.getPriority()).build());
    }

    private List<AgentWorkflowNodeDO> nodes(String workflowId) { return nodeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowNodeDO>().eq(AgentWorkflowNodeDO::getWorkflowId, workflowId).eq(AgentWorkflowNodeDO::getDeleted, 0).orderByAsc(AgentWorkflowNodeDO::getNodeOrder)); }
    private List<AgentWorkflowEdgeDO> edges(String workflowId) { return edgeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowEdgeDO>().eq(AgentWorkflowEdgeDO::getWorkflowId, workflowId).eq(AgentWorkflowEdgeDO::getDeleted, 0).orderByAsc(AgentWorkflowEdgeDO::getPriority)); }
    private AgentWorkflowDefinitionDO requiredWorkflow(String workflowId) { AgentWorkflowDefinitionDO workflow = workflowMapper.selectById(workflowId); Assert.notNull(workflow, () -> new ClientException("未找到工作流")); return workflow; }
    private AgentWorkflowNodeVO nodeVO(AgentWorkflowNodeDO n) { AgentWorkflowNodeVO vo = new AgentWorkflowNodeVO(); vo.setId(n.getId()); vo.setNodeKey(n.getNodeKey()); vo.setNodeName(n.getNodeName()); vo.setNodeType(n.getNodeType()); vo.setActionType(n.getActionType()); vo.setConfig(parse(n.getConfigJson())); return vo; }
    private AgentWorkflowEdgeVO edgeVO(AgentWorkflowEdgeDO e) { AgentWorkflowEdgeVO vo = new AgentWorkflowEdgeVO(); vo.setId(e.getId()); vo.setSourceNodeKey(e.getSourceNodeKey()); vo.setTargetNodeKey(e.getTargetNodeKey()); vo.setEdgeType(e.getEdgeType()); vo.setPriority(e.getPriority()); return vo; }
    private AgentWorkflowInstanceVO instanceVO(AgentWorkflowInstanceDO instance, boolean detail) {
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
        vo.setCreateTime(instance.getCreateTime());
        vo.setUpdateTime(instance.getUpdateTime());
        if (detail) {
            vo.setNodes(nodeInstances(instance.getId()).stream().map(this::nodeInstanceVO).toList());
            vo.setEvents(events(instance.getId()).stream().map(this::eventVO).toList());
        }
        return vo;
    }

    private List<AgentWorkflowNodeInstanceDO> nodeInstances(String instanceId) {
        return nodeInstanceMapper.selectList(new LambdaQueryWrapper<AgentWorkflowNodeInstanceDO>()
                .eq(AgentWorkflowNodeInstanceDO::getInstanceId, instanceId)
                .eq(AgentWorkflowNodeInstanceDO::getDeleted, 0)
                .orderByAsc(AgentWorkflowNodeInstanceDO::getCreateTime));
    }

    private List<AgentWorkflowEventDO> events(String instanceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<AgentWorkflowEventDO>()
                .eq(AgentWorkflowEventDO::getInstanceId, instanceId)
                .orderByAsc(AgentWorkflowEventDO::getCreateTime));
    }

    private AgentWorkflowNodeInstanceVO nodeInstanceVO(AgentWorkflowNodeInstanceDO n) {
        AgentWorkflowNodeInstanceVO vo = new AgentWorkflowNodeInstanceVO();
        vo.setId(n.getId());
        vo.setInstanceId(n.getInstanceId());
        vo.setWorkflowId(n.getWorkflowId());
        vo.setNodeKey(n.getNodeKey());
        vo.setNodeName(n.getNodeName());
        vo.setNodeType(n.getNodeType());
        vo.setActionType(n.getActionType());
        vo.setStatus(n.getStatus());
        vo.setInput(parse(n.getInputJson()));
        vo.setOutput(parse(n.getOutputJson()));
        vo.setErrorMessage(n.getErrorMessage());
        vo.setRetryCount(n.getRetryCount());
        vo.setStartedAt(n.getStartedAt());
        vo.setCompletedAt(n.getCompletedAt());
        vo.setDurationMs(n.getDurationMs());
        return vo;
    }

    private AgentWorkflowEventVO eventVO(AgentWorkflowEventDO e) {
        AgentWorkflowEventVO vo = new AgentWorkflowEventVO();
        vo.setId(e.getId());
        vo.setInstanceId(e.getInstanceId());
        vo.setNodeInstanceId(e.getNodeInstanceId());
        vo.setEventType(e.getEventType());
        vo.setEventLevel(e.getEventLevel());
        vo.setContent(e.getContent());
        vo.setPayload(parse(e.getPayloadJson()));
        vo.setImportanceScore(e.getImportanceScore());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
    private String json(JsonNode node) { return node == null || node.isNull() ? null : node.toString(); }
    private JsonNode parse(String raw) { if (!StringUtils.hasText(raw)) return null; try { return objectMapper.readTree(raw); } catch (Exception ex) { throw new ClientException("JSON解析失败"); } }
    private String def(String value, String defaultValue) { return StringUtils.hasText(value) ? value : defaultValue; }
}
