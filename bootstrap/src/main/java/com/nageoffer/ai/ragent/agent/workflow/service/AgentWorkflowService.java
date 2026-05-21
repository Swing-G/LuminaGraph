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

package com.nageoffer.ai.ragent.agent.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowCreateRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowRunRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowUpdateRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowInstanceVO;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowVO;
import com.nageoffer.ai.ragent.agent.workflow.domain.AgentWorkflowDefinition;

/**
 * Agent Workflow服务
 */
public interface AgentWorkflowService {

    AgentWorkflowVO create(AgentWorkflowCreateRequest request);

    AgentWorkflowVO update(String workflowId, AgentWorkflowUpdateRequest request);

    AgentWorkflowVO get(String workflowId);

    IPage<AgentWorkflowVO> page(Page<AgentWorkflowVO> page, String keyword, String status);

    void delete(String workflowId);

    AgentWorkflowDefinition getDefinition(String workflowId);

    AgentWorkflowInstanceVO run(String workflowId, AgentWorkflowRunRequest request);

    AgentWorkflowInstanceVO getInstance(String instanceId);

    IPage<AgentWorkflowInstanceVO> pageInstances(Page<AgentWorkflowInstanceVO> page, String workflowId);

    AgentWorkflowVO findEnabledByType(String workflowType);
}
