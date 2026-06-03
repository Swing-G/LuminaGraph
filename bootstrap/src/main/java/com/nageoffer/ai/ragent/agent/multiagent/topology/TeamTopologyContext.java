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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.multiagent.domain.AgentConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拓扑执行上下文
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamTopologyContext {

    /**
     * Workflow实例ID
     */
    private String instanceId;

    /**
     * Node实例ID
     */
    private String nodeInstanceId;

    /**
     * Agent列表
     */
    private List<AgentConfig> agents;

    /**
     * 原始输入
     */
    private JsonNode originalInput;

    /**
     * Workflow上下文
     */
    private ObjectNode workflowContext;

    /**
     * 最大轮数
     */
    private Integer maxRounds;

    /**
     * 当前轮数
     */
    private Integer currentRound;

    /**
     * 合并策略
     */
    private String mergeStrategy;
}
