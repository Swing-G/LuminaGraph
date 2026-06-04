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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.skill.dao.entity.AgentSkillDO;
import com.nageoffer.ai.ragent.agent.skill.dao.mapper.AgentSkillMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 加载器
 * <p>
 * 启动时扫描 resources/skills/*.md，解析 YAML frontmatter + Markdown body，
 * 差分更新到数据库（新增或更新 source_file 匹配的 Skill）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillLoader {

    private final AgentSkillMapper skillMapper;
    private final ObjectMapper objectMapper;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    @PostConstruct
    public void load() {
        log.info("开始扫描 skills 目录...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources;
            try {
                resources = resolver.getResources("classpath*:skills/*.md");
            } catch (Exception dirEx) {
                log.warn("skills 目录不存在或无法扫描: {}", dirEx.getMessage());
                return;
            }
            if (resources.length == 0) {
                log.info("未找到 Skill 文件，跳过加载");
                return;
            }
            log.info("找到 {} 个 Skill 文件", resources.length);

            for (Resource resource : resources) {
                try {
                    String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    Map<String, String> frontmatter = parseFrontmatter(content);
                    if (frontmatter == null || !frontmatter.containsKey("id")) {
                        log.warn("跳过无效 Skill 文件: {}", resource.getFilename());
                        continue;
                    }

                    String skillKey = frontmatter.get("id");
                    String body = extractBody(content);
                    Map<String, String> sections = parseBodySections(body);

                    // 找已有的 (by skill_key + source_file)
                    AgentSkillDO existing = skillMapper.selectBySkillKey(skillKey);

                    AgentSkillDO skill = existing != null ? existing : AgentSkillDO.builder().build();
                    skill.setSkillKey(skillKey);
                    skill.setName(frontmatter.getOrDefault("name", skillKey));
                    skill.setDescription(frontmatter.getOrDefault("description", ""));
                    skill.setVersion(frontmatter.getOrDefault("version", "1.0.0"));
                    skill.setCategory(frontmatter.getOrDefault("category", ""));
                    skill.setTags(toJsonArray(frontmatter.get("tags")));
                    skill.setTools(toJsonArray(frontmatter.get("tools")));
                    skill.setSopContent(sections.get("SOP"));
                    skill.setDomainRules(sections.get("领域规则"));
                    skill.setPromptTemplate(sections.get("提示词模板"));
                    skill.setOutputSpec(sections.get("输出规范"));
                    skill.setSourceFile(resource.getFilename());
                    skill.setStatus(frontmatter.getOrDefault("status", "ENABLED").toUpperCase());
                    skill.setCreatedBy("SYSTEM");

                    if (existing != null) {
                        skillMapper.updateById(skill);
                        log.info("Skill 已更新: {} (v{})", skillKey, skill.getVersion());
                    } else {
                        skillMapper.insert(skill);
                        log.info("Skill 已新增: {} (v{})", skillKey, skill.getVersion());
                    }
                } catch (Exception e) {
                    log.error("加载 Skill 文件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("Skill 扫描失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseFrontmatter(String content) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        if (!m.find()) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : m.group(1).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                // 去掉引号和方括号
                value = value.replaceAll("^\"|\"$", "").replaceAll("^\\[|\\]$", "");
                result.put(key, value);
            }
        }
        return result;
    }

    private String extractBody(String content) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        return m.find() ? m.group(2).trim() : content;
    }

    private Map<String, String> parseBodySections(String body) {
        Map<String, String> sections = new LinkedHashMap<>();
        String[] parts = body.split("\n## ");
        for (String part : parts) {
            int newline = part.indexOf('\n');
            if (newline > 0) {
                String title = part.substring(0, newline).trim();
                String text = part.substring(newline + 1).trim();
                sections.put(title, text);
            }
        }
        return sections;
    }

    private String toJsonArray(String csv) {
        if (csv == null || csv.isBlank()) return "[]";
        try {
            List<String> items = Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
