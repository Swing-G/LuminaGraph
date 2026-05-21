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

package com.nageoffer.ai.ragent.agent.action.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.action.domain.ActionConfig;
import com.nageoffer.ai.ragent.agent.action.domain.ActionContext;
import com.nageoffer.ai.ragent.agent.action.domain.ActionResult;
import com.nageoffer.ai.ragent.agent.action.enums.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 空动作执行器，用于占位节点和流程连通性验证
 */
@Component
@RequiredArgsConstructor
public class NoopActionExecutor implements AgentActionExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public String actionType() {
        return ActionType.NOOP.name();
    }

    @Override
    public ActionResult execute(ActionContext context, ActionConfig config) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("message", "noop action executed");
        output.put("nodeKey", context.getNodeKey());
        return ActionResult.success(output);
    }
}
