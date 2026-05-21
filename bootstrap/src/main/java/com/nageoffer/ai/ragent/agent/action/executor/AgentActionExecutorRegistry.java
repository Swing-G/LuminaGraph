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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent Action执行器注册表
 */
@Component
public class AgentActionExecutorRegistry {

    private final Map<String, AgentActionExecutor> executorMap;

    public AgentActionExecutorRegistry(List<AgentActionExecutor> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(AgentActionExecutor::actionType, Function.identity()));
    }

    public AgentActionExecutor getRequired(String actionType) {
        AgentActionExecutor executor = executorMap.get(actionType);
        if (executor == null) {
            throw new ClientException("未找到Action执行器: " + actionType);
        }
        return executor;
    }
}
