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

package com.nageoffer.ai.ragent.agent.skill.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.skill.dao.entity.AgentSkillDO;
import com.nageoffer.ai.ragent.agent.skill.dao.mapper.AgentSkillMapper;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent Skill 服务
 * <p>
 * CRUD + 基于 LLM 的语义匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private final AgentSkillMapper skillMapper;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户问题，用 LLM 匹配最合适的 Skill。
     * 返回匹配到的 Skill（含完整 SOP/规则/模板），如果没有匹配到则返回 null。
     */
    public AgentSkillDO matchSkill(String userQuestion) {
        // 1. 加载所有启用的 Skill 摘要（只取 key + name + tags + category，不取正文）
        List<AgentSkillDO> all = skillMapper.selectList(new LambdaQueryWrapper<AgentSkillDO>()
                .eq(AgentSkillDO::getStatus, "ENABLED"));
        if (all.isEmpty()) return null;

        // 2. 构建匹配 prompt
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < all.size(); i++) {
            AgentSkillDO s = all.get(i);
            catalog.append(i + 1).append(". **").append(s.getSkillKey()).append("** - ").append(s.getName());
            if (s.getDescription() != null && !s.getDescription().isBlank()) {
                catalog.append(" (").append(s.getDescription()).append(")");
            }
            catalog.append("\n   标签: ").append(s.getTags());
            catalog.append(" | 分类: ").append(s.getCategory()).append("\n");
        }

        String prompt = "以下是可用的 Skill 列表。根据用户问题，判断最合适的 Skill（只返回 skillKey），"
                + "如果都不匹配返回 NONE。\n\n"
                + "## Skill 列表\n" + catalog + "\n"
                + "## 用户问题\n" + userQuestion + "\n\n"
                + "只回复一个 skillKey 或 NONE：";

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.0)
                    .maxTokens(50)
                    .build();
            String response = llmService.chat(request).trim();
            log.info("Skill 匹配结果: question='{}' → {}", userQuestion.substring(0, Math.min(50, userQuestion.length())), response);

            // 3. 查找匹配的 Skill
            for (AgentSkillDO s : all) {
                if (response.contains(s.getSkillKey())) {
                    return s;
                }
            }
        } catch (Exception e) {
            log.warn("Skill 匹配失败，退回标签匹配", e);
        }

        // 4. 兜底：简单关键词匹配
        for (AgentSkillDO s : all) {
            if (hasKeywordMatch(userQuestion, s)) {
                return s;
            }
        }
        return null;
    }

    private boolean hasKeywordMatch(String question, AgentSkillDO skill) {
        try {
            List<String> tags = objectMapper.readValue(skill.getTags(), List.class);
            for (String tag : tags) {
                if (question.contains(tag.trim())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
