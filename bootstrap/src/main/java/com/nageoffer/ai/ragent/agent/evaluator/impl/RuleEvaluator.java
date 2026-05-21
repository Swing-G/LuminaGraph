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

package com.nageoffer.ai.ragent.agent.evaluator.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.evaluator.core.AgentEvaluator;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationContext;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于规则的评估器
 */
@Component
@RequiredArgsConstructor
public class RuleEvaluator implements AgentEvaluator {

    private final ObjectMapper objectMapper;

    @Override
    public String evaluatorType() {
        return "RULE";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        JsonNode config = context.getConfig();
        JsonNode target = resolveTarget(context);
        List<String> reasons = new ArrayList<>();
        if (config != null && config.has("requiredFields") && config.get("requiredFields").isArray()) {
            for (JsonNode fieldNode : config.get("requiredFields")) {
                String field = fieldNode.asText();
                JsonNode value = target.path(field);
                if (value.isMissingNode() || value.isNull() || value.asText("").isBlank()) {
                    reasons.add("缺少必填字段: " + field);
                }
            }
        }
        int minLength = config == null ? 0 : config.path("minLength").asInt(0);
        int actualLength = target == null || target.isNull() ? 0 : target.toString().length();
        if (minLength > 0 && actualLength < minLength) {
            reasons.add("输出长度不足: " + actualLength + "/" + minLength);
        }
        boolean passed = reasons.isEmpty();
        ObjectNode details = objectMapper.createObjectNode();
        details.put("actualLength", actualLength);
        details.set("target", target == null ? objectMapper.nullNode() : target);
        return EvaluationResult.builder()
                .passed(passed)
                .score(passed ? 100 : 50)
                .reason(passed ? "规则验收通过" : String.join("; ", reasons))
                .suggestion(passed ? "" : "请补齐必填字段并增加处理方案的完整性")
                .details(details)
                .build();
    }

    private JsonNode resolveTarget(EvaluationContext context) {
        JsonNode config = context.getConfig();
        if (config != null && config.hasNonNull("targetNodeKey")) {
            return context.getWorkflowContext().path(config.path("targetNodeKey").asText());
        }
        if (context.getTargetOutput() != null) {
            return context.getTargetOutput();
        }
        return objectMapper.nullNode();
    }
}
