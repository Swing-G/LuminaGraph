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

package com.nageoffer.ai.ragent.agent.memory.impl;

import com.nageoffer.ai.ragent.agent.memory.core.WorkflowMemoryStrategy;
import com.nageoffer.ai.ragent.agent.memory.domain.WorkflowMemoryContext;
import com.nageoffer.ai.ragent.agent.memory.domain.WorkflowMemoryResult;
import com.nageoffer.ai.ragent.agent.memory.enums.WorkflowMemoryStrategyType;
import org.springframework.stereotype.Component;

/**
 * 空Workflow记忆策略
 */
@Component
public class NoopWorkflowMemoryStrategy implements WorkflowMemoryStrategy {

    @Override
    public String strategyType() {
        return WorkflowMemoryStrategyType.NONE.name();
    }

    @Override
    public WorkflowMemoryResult compress(WorkflowMemoryContext context) {
        return WorkflowMemoryResult.builder().compressed(false).eventCount(0).build();
    }
}
