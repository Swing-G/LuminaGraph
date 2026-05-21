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

package com.nageoffer.ai.ragent.agent.action.condition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.action.domain.ActionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Workflow 条件评估器
 */
@Component
@RequiredArgsConstructor
public class WorkflowConditionEvaluator {

    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();

    public boolean evaluate(ActionContext context, JsonNode condition) {
        if (condition == null || condition.isNull()) {
            return true;
        }
        if (condition.isBoolean()) {
            return condition.asBoolean();
        }
        if (condition.isTextual()) {
            return evalSpel(context, condition.asText());
        }
        if (condition.has("expression")) {
            return evalSpel(context, condition.path("expression").asText());
        }
        if (condition.has("all")) {
            return evalAll(context, condition.get("all"));
        }
        if (condition.has("any")) {
            return evalAny(context, condition.get("any"));
        }
        if (condition.has("not")) {
            return !evaluate(context, condition.get("not"));
        }
        if (condition.has("field")) {
            return evalRule(context, condition);
        }
        return true;
    }

    private boolean evalAll(ActionContext context, JsonNode node) {
        if (node == null || !node.isArray()) {
            return true;
        }
        for (JsonNode item : node) {
            if (!evaluate(context, item)) {
                return false;
            }
        }
        return true;
    }

    private boolean evalAny(ActionContext context, JsonNode node) {
        if (node == null || !node.isArray()) {
            return true;
        }
        for (JsonNode item : node) {
            if (evaluate(context, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean evalRule(ActionContext context, JsonNode node) {
        String field = node.path("field").asText(null);
        if (!StringUtils.hasText(field)) {
            return true;
        }
        String operator = node.path("operator").asText(node.has("equals") ? "eq" : "eq");
        JsonNode valueNode = node.has("value") ? node.get("value") : node.get("equals");
        Object left = readPath(context, field);
        Object right = valueNode == null ? null : objectMapper.convertValue(valueNode, Object.class);
        return compare(left, right, operator);
    }

    private boolean evalSpel(ActionContext context, String expression) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext(context);
            evalContext.setVariable("ctx", context);
            Boolean result = parser.parseExpression(expression).getValue(evalContext, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            return false;
        }
    }

    private Object readPath(ActionContext context, String path) {
        JsonNode source = context.getContext();
        String normalized = path;
        if (path.startsWith("context.")) {
            normalized = path.substring("context.".length());
        } else if (path.startsWith("input.")) {
            source = context.getInput();
            normalized = path.substring("input.".length());
        }
        JsonNode current = source;
        for (String segment : normalized.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(segment);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return objectMapper.convertValue(current, Object.class);
    }

    private boolean compare(Object left, Object right, String operator) {
        return switch (operator.toLowerCase()) {
            case "ne" -> !Objects.equals(normalize(left), normalize(right));
            case "in" -> in(left, right);
            case "contains" -> contains(left, right);
            case "gt" -> compareNumber(left, right) > 0;
            case "gte" -> compareNumber(left, right) >= 0;
            case "lt" -> compareNumber(left, right) < 0;
            case "lte" -> compareNumber(left, right) <= 0;
            case "exists" -> left != null;
            case "not_exists" -> left == null;
            default -> Objects.equals(normalize(left), normalize(right));
        };
    }

    private boolean in(Object left, Object right) {
        if (right instanceof List<?> list) {
            return list.contains(left);
        }
        if (left instanceof List<?> list) {
            return list.contains(right);
        }
        return Objects.equals(normalize(left), normalize(right));
    }

    private boolean contains(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof String text) {
            return text.contains(String.valueOf(right));
        }
        if (left instanceof List<?> list) {
            return list.contains(right);
        }
        if (left instanceof Map<?, ?> map) {
            return map.containsKey(String.valueOf(right));
        }
        return false;
    }

    private int compareNumber(Object left, Object right) {
        Double l = toDouble(left);
        Double r = toDouble(right);
        if (l == null || r == null) {
            return 0;
        }
        return Double.compare(l, r);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Object normalize(Object value) {
        return value instanceof String text ? text.trim() : value;
    }
}
