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

package com.nageoffer.ai.ragent.agent.skill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.skill.controller.request.SkillCreateRequest;
import com.nageoffer.ai.ragent.agent.skill.controller.vo.SkillSuggestionVO;
import com.nageoffer.ai.ragent.agent.skill.controller.vo.SkillVO;
import com.nageoffer.ai.ragent.agent.skill.dao.entity.AgentSkillDO;
import com.nageoffer.ai.ragent.agent.skill.dao.entity.AgentSkillSuggestionDO;
import com.nageoffer.ai.ragent.agent.skill.dao.mapper.AgentSkillMapper;
import com.nageoffer.ai.ragent.agent.skill.dao.mapper.AgentSkillSuggestionMapper;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class SkillController {

    private final AgentSkillMapper skillMapper;
    private final AgentSkillSuggestionMapper suggestionMapper;
    private final ObjectMapper objectMapper;

    // ─── Skill CRUD ──────────

    @PostMapping("/agent/skills")
    public Result<SkillVO> create(@RequestBody SkillCreateRequest req) {
        AgentSkillDO d = new AgentSkillDO();
        d.setSkillKey(req.getSkillKey()); d.setName(req.getName());
        d.setDescription(req.getDescription()); d.setVersion(req.getVersion());
        d.setCategory(req.getCategory()); d.setSopContent(req.getSopContent());
        d.setDomainRules(req.getDomainRules()); d.setPromptTemplate(req.getPromptTemplate());
        d.setOutputSpec(req.getOutputSpec()); d.setStatus(req.getStatus()); d.setCreatedBy("ADMIN");
        try { d.setTags(objectMapper.writeValueAsString(req.getTags())); } catch (Exception ignored) {}
        try { d.setTools(objectMapper.writeValueAsString(req.getTools())); } catch (Exception ignored) {}
        skillMapper.insert(d);
        return Results.success(toVO(d));
    }

    @PutMapping("/agent/skills/{id}")
    @Transactional
    public Result<SkillVO> update(@PathVariable String id, @RequestBody SkillCreateRequest req) {
        AgentSkillDO d = skillMapper.selectById(id);
        if (d == null) throw new ClientException("Skill 不存在", BaseErrorCode.CLIENT_ERROR);
        d.setSkillKey(req.getSkillKey()); d.setName(req.getName());
        d.setDescription(req.getDescription()); d.setVersion(req.getVersion());
        d.setCategory(req.getCategory()); d.setSopContent(req.getSopContent());
        d.setDomainRules(req.getDomainRules()); d.setPromptTemplate(req.getPromptTemplate());
        d.setOutputSpec(req.getOutputSpec()); d.setStatus(req.getStatus());
        try { d.setTags(objectMapper.writeValueAsString(req.getTags())); } catch (Exception ignored) {}
        try { d.setTools(objectMapper.writeValueAsString(req.getTools())); } catch (Exception ignored) {}
        skillMapper.updateById(d);
        return Results.success(toVO(d));
    }

    @GetMapping("/agent/skills/{id}")
    public Result<SkillVO> get(@PathVariable String id) {
        AgentSkillDO d = skillMapper.selectById(id);
        if (d == null) throw new ClientException("Skill 不存在", BaseErrorCode.CLIENT_ERROR);
        return Results.success(toVO(d));
    }

    @GetMapping("/agent/skills")
    public Result<IPage<SkillVO>> page(@RequestParam(defaultValue = "1") int pageNo,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<AgentSkillDO> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) w.like(AgentSkillDO::getName, keyword);
        w.orderByDesc(AgentSkillDO::getUpdateTime);
        Page<AgentSkillDO> pg = new Page<>(pageNo, pageSize);
        return Results.success(skillMapper.selectPage(pg, w).convert(this::toVO));
    }

    @DeleteMapping("/agent/skills/{id}")
    public Result<Void> delete(@PathVariable String id) {
        skillMapper.deleteById(id);
        return Results.success();
    }

    // ─── Suggestion review ────

    @GetMapping("/agent/skills/{skillId}/suggestions")
    public Result<List<SkillSuggestionVO>> suggestions(@PathVariable String skillId) {
        return Results.success(suggestionMapper.selectPendingBySkillId(skillId).stream()
                .map(this::toVO).toList());
    }

    @PutMapping("/agent/skills/{skillId}/suggestions/{suggestionId}/approve")
    @Transactional
    public Result<Void> approve(@PathVariable String skillId, @PathVariable String suggestionId) {
        AgentSkillSuggestionDO s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new ClientException("建议不存在", BaseErrorCode.CLIENT_ERROR);
        // 应用变更到 Skill
        AgentSkillDO skill = skillMapper.selectById(s.getSkillId());
        if (skill != null) {
            String field = s.getFieldPath();
            String newText = s.getSuggestedText();
            if ("sop_content".equals(field)) skill.setSopContent(newText);
            else if ("domain_rules".equals(field)) skill.setDomainRules(newText);
            else if ("prompt_template".equals(field)) skill.setPromptTemplate(newText);
            else if ("output_spec".equals(field)) skill.setOutputSpec(newText);
            skillMapper.updateById(skill);
        }
        s.setStatus("APPROVED");
        suggestionMapper.updateById(s);
        return Results.success();
    }

    @PutMapping("/agent/skills/{skillId}/suggestions/{suggestionId}/reject")
    public Result<Void> reject(@PathVariable String skillId, @PathVariable String suggestionId) {
        AgentSkillSuggestionDO s = suggestionMapper.selectById(suggestionId);
        if (s == null) throw new ClientException("建议不存在", BaseErrorCode.CLIENT_ERROR);
        s.setStatus("REJECTED");
        suggestionMapper.updateById(s);
        return Results.success();
    }

    @SuppressWarnings("unchecked")
    private SkillVO toVO(AgentSkillDO d) {
        SkillVO v = new SkillVO();
        v.setId(d.getId()); v.setSkillKey(d.getSkillKey()); v.setName(d.getName());
        v.setDescription(d.getDescription()); v.setVersion(d.getVersion());
        v.setCategory(d.getCategory()); v.setSopContent(d.getSopContent());
        v.setDomainRules(d.getDomainRules()); v.setPromptTemplate(d.getPromptTemplate());
        v.setOutputSpec(d.getOutputSpec()); v.setSourceFile(d.getSourceFile());
        v.setStatus(d.getStatus()); v.setCreateTime(d.getCreateTime()); v.setUpdateTime(d.getUpdateTime());
        try { v.setTags(objectMapper.readValue(d.getTags(), List.class)); } catch (Exception ignored) {}
        try { v.setTools(objectMapper.readValue(d.getTools(), List.class)); } catch (Exception ignored) {}
        return v;
    }

    private SkillSuggestionVO toVO(AgentSkillSuggestionDO d) {
        SkillSuggestionVO v = new SkillSuggestionVO();
        v.setId(d.getId()); v.setSkillId(d.getSkillId()); v.setSuggestionType(d.getSuggestionType());
        v.setFieldPath(d.getFieldPath()); v.setOriginalText(d.getOriginalText());
        v.setSuggestedText(d.getSuggestedText()); v.setReason(d.getReason());
        v.setConfidence(d.getConfidence()); v.setSourceInstance(d.getSourceInstance());
        v.setStatus(d.getStatus()); v.setCreateTime(d.getCreateTime());
        return v;
    }
}
