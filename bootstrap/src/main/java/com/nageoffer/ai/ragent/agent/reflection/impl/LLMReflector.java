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

package com.nageoffer.ai.ragent.agent.reflection.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.nageoffer.ai.ragent.agent.evaluator.domain.EvaluationResult;
import com.nageoffer.ai.ragent.agent.reflection.core.AgentReflector;
import com.nageoffer.ai.ragent.agent.reflection.domain.ReflectionContext;
import com.nageoffer.ai.ragent.agent.reflection.domain.ReflectionResult;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于LLM的最小Reflection实现
 */
@Component
@RequiredArgsConstructor
public class LLMReflector implements AgentReflector {

    private final LLMService llmService;

    @Override
    public ReflectionResult reflect(ReflectionContext context) {
        EvaluationResult evaluation = context.getEvaluationResult();
        String suggestion = evaluation == null ? "请补充完整处理方案" : evaluation.getSuggestion();
        String prompt = buildPrompt(context, suggestion);
        String revisedPrompt = llmService.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.2D)
                .topP(0.5D)
                .thinking(false)
                .build());
        return ReflectionResult.builder()
                .retry(true)
                .suggestion(suggestion)
                .revisedPrompt(revisedPrompt)
                .build();
    }

    private String buildPrompt(ReflectionContext context, String suggestion) {
        JsonNode originalInput = context.getOriginalInput();
        JsonNode previousOutput = context.getPreviousOutput();
        EvaluationResult evaluation = context.getEvaluationResult();
        return "你是企业工单处理方案反思器。\n"
                + "请根据验收失败原因，生成一段用于重新执行上一个LLM节点的修正提示。\n"
                + "原始输入：\n" + (originalInput == null ? "null" : originalInput.toPrettyString()) + "\n"
                + "上次输出：\n" + (previousOutput == null ? "null" : previousOutput.toPrettyString()) + "\n"
                + "失败原因：" + (evaluation == null ? "未知" : evaluation.getReason()) + "\n"
                + "修正建议：" + suggestion + "\n"
                + "请只输出修正提示文本，不要输出Markdown。";
    }
}
