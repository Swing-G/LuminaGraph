import { api } from "@/services/api";
import type { PageResult } from "@/services/workflowService";

export interface Skill {
  id: string;
  skillKey: string;
  name: string;
  description?: string;
  version: string;
  category?: string;
  tags?: string[];
  tools?: string[];
  sopContent?: string;
  domainRules?: string;
  promptTemplate?: string;
  outputSpec?: string;
  sourceFile?: string;
  status: string;
  createTime?: string;
  updateTime?: string;
}

export interface SkillCreatePayload {
  skillKey: string;
  name: string;
  description?: string;
  version?: string;
  category?: string;
  tags?: string[];
  tools?: string[];
  sopContent?: string;
  domainRules?: string;
  promptTemplate?: string;
  outputSpec?: string;
  status?: string;
}

export interface SkillSuggestion {
  id: string;
  skillId: string;
  suggestionType: string;
  fieldPath: string;
  originalText?: string;
  suggestedText: string;
  reason?: string;
  confidence: number;
  sourceInstance?: string;
  status: string;
  createTime?: string;
}

export const FIELD_LABELS: Record<string, string> = {
  sop_content: "SOP流程", domain_rules: "领域规则", prompt_template: "提示词模板", output_spec: "输出规范"
};

export async function getSkills(params: { pageNo?: number; pageSize?: number; keyword?: string } = {}) {
  return api.get<PageResult<Skill>>("/agent/skills", { params: { pageNo: params.pageNo ?? 1, pageSize: params.pageSize ?? 10, keyword: params.keyword || undefined } });
}
export async function getSkill(id: string) { return api.get<Skill>(`/agent/skills/${id}`); }
export async function createSkill(payload: SkillCreatePayload) { return api.post<Skill>("/agent/skills", payload); }
export async function updateSkill(id: string, payload: SkillCreatePayload) { return api.put<Skill>(`/agent/skills/${id}`, payload); }
export async function deleteSkill(id: string) { return api.delete<void>(`/agent/skills/${id}`); }
export async function getSuggestions(skillId: string) { return api.get<SkillSuggestion[]>(`/agent/skills/${skillId}/suggestions`); }
export async function approveSuggestion(skillId: string, suggestionId: string) { return api.put<void>(`/agent/skills/${skillId}/suggestions/${suggestionId}/approve`); }
export async function rejectSuggestion(skillId: string, suggestionId: string) { return api.put<void>(`/agent/skills/${skillId}/suggestions/${suggestionId}/reject`); }
export async function reloadSkills() { return api.post<string>("/agent/skills/reload"); }
