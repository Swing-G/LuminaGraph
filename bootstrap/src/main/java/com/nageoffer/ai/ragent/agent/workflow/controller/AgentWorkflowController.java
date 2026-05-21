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

package com.nageoffer.ai.ragent.agent.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowCreateRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowRunRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.request.AgentWorkflowUpdateRequest;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowInstanceVO;
import com.nageoffer.ai.ragent.agent.workflow.controller.vo.AgentWorkflowVO;
import com.nageoffer.ai.ragent.agent.workflow.service.AgentWorkflowService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Agent Workflow控制层
 */
@RestController
@RequiredArgsConstructor
@Validated
public class AgentWorkflowController {

    private final AgentWorkflowService workflowService;

    /**
     * 创建Agent Workflow
     */
    @PostMapping("/agent/workflows")
    public Result<AgentWorkflowVO> create(@RequestBody AgentWorkflowCreateRequest request) {
        return Results.success(workflowService.create(request));
    }

    /**
     * 更新Agent Workflow
     */
    @PutMapping("/agent/workflows/{id}")
    public Result<AgentWorkflowVO> update(@PathVariable String id, @RequestBody AgentWorkflowUpdateRequest request) {
        return Results.success(workflowService.update(id, request));
    }

    /**
     * 获取Agent Workflow详情
     */
    @GetMapping("/agent/workflows/{id}")
    public Result<AgentWorkflowVO> get(@PathVariable String id) {
        return Results.success(workflowService.get(id));
    }

    /**
     * 分页查询Agent Workflow
     */
    @GetMapping("/agent/workflows")
    public Result<IPage<AgentWorkflowVO>> page(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                               @RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "status", required = false) String status) {
        return Results.success(workflowService.page(new Page<>(pageNo, pageSize), keyword, status));
    }

    /**
     * 删除Agent Workflow
     */
    @DeleteMapping("/agent/workflows/{id}")
    public Result<Void> delete(@PathVariable String id) {
        workflowService.delete(id);
        return Results.success();
    }

    /**
     * 运行Agent Workflow
     */
    @PostMapping("/agent/workflows/{id}/run")
    public Result<AgentWorkflowInstanceVO> run(@PathVariable String id, @RequestBody AgentWorkflowRunRequest request) {
        return Results.success(workflowService.run(id, request));
    }

    /**
     * 获取Agent Workflow运行实例
     */
    /**
     * 分页查询Agent Workflow运行实例
     */
    @GetMapping("/agent/workflow-instances")
    public Result<IPage<AgentWorkflowInstanceVO>> pageInstances(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                               @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                               @RequestParam(value = "workflowId", required = false) String workflowId) {
        return Results.success(workflowService.pageInstances(new Page<>(pageNo, pageSize), workflowId));
    }
    @GetMapping("/agent/workflow-instances/{id}")
    public Result<AgentWorkflowInstanceVO> getInstance(@PathVariable String id) {
        return Results.success(workflowService.getInstance(id));
    }
}
