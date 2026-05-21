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

package com.nageoffer.ai.ragent.agent.memory.core;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Workflow记忆策略注册表
 */
@Component
public class WorkflowMemoryStrategyRegistry {

    private final Map<String, WorkflowMemoryStrategy> strategyMap;

    public WorkflowMemoryStrategyRegistry(List<WorkflowMemoryStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(WorkflowMemoryStrategy::strategyType, Function.identity()));
    }

    public WorkflowMemoryStrategy getRequired(String strategyType) {
        WorkflowMemoryStrategy strategy = strategyMap.get(strategyType);
        if (strategy == null) {
            throw new ClientException("未找到Workflow记忆策略: " + strategyType);
        }
        return strategy;
    }
}
