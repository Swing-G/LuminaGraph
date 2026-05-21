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

package com.nageoffer.ai.ragent.agent.workflow.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Date;

/**
 * Agent Workflow节点运行实例视图
 */
@Data
public class AgentWorkflowNodeInstanceVO {

    private String id;

    private String instanceId;

    private String workflowId;

    private String nodeKey;

    private String nodeName;

    private String nodeType;

    private String actionType;

    private String status;

    private JsonNode input;

    private JsonNode output;

    private String errorMessage;

    private Integer retryCount;

    private Date startedAt;

    private Date completedAt;

    private Long durationMs;
}
