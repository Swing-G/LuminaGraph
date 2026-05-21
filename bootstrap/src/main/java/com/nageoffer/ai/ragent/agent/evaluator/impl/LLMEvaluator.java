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
import com.nageoffer.ai.ragent.agent.evaluator.core.AgentEvaluator;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationContext;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationResult;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于LLM的评估器
 */
@Component
@RequiredArgsConstructor
public class LLMEvaluator implements AgentEvaluator {

    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public String evaluatorType() {
        return "LLM";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        String prompt = buildPrompt(context);
        String raw = llmService.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build());
        try {
            JsonNode node = objectMapper.readTree(LLMResponseCleaner.stripMarkdownCodeFence(raw));
            return EvaluationResult.builder()
                    .passed(node.path("passed").asBoolean(false))
                    .score(node.path("score").asInt(0))
                    .reason(node.path("reason").asText("LLM评估未给出原因"))
                    .suggestion(node.path("suggestion").asText(""))
                    .details(node)
                    .build();
        } catch (Exception ex) {
            return EvaluationResult.builder()
                    .passed(false)
                    .score(0)
                    .reason("LLM评估结果解析失败: " + ex.getMessage())
                    .suggestion("请重新生成结构化处理方案")
                    .details(objectMapper.createObjectNode().put("raw", raw == null ? "" : raw))
                    .build();
        }
    }

    private String buildPrompt(EvaluationContext context) {
        JsonNode config = context.getConfig();
        String criteria = config == null ? "" : config.path("criteria").asText("");
        JsonNode target = context.getTargetOutput();
        if (config != null && config.hasNonNull("targetNodeKey")) {
            target = context.getWorkflowContext().path(config.path("targetNodeKey").asText());
        }
        return "你是企业工单处理验收员。\n"
                + "请判断下面的处理方案是否满足要求。\n"
                + "验收标准：\n"
                + (criteria.isBlank() ? "1. 是否回答了用户问题\n2. 是否包含处理步骤\n3. 是否存在高风险操作\n4. 是否需要人工审核\n" : criteria + "\n")
                + "处理方案：\n"
                + (target == null ? "null" : target.toPrettyString())
                + "\n只输出JSON，不要输出Markdown：\n"
                + "{\"passed\":true/false,\"score\":0-100,\"reason\":\"...\",\"suggestion\":\"...\"}";
    }
}
