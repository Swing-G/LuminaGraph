import { useEffect, useState, useCallback } from "react";
import { Loader2, Plus, Save, Trash2, Search, BookOpen, Lightbulb, Check, X, ChevronDown, ChevronRight } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { getErrorMessage } from "@/utils/error";
import { getAvailableTools } from "@/services/multiAgentService";
import {
  getSkills, getSkill, createSkill, updateSkill, deleteSkill, getSuggestions, approveSuggestion, rejectSuggestion,
  FIELD_LABELS, type Skill, type SkillCreatePayload, type SkillSuggestion, type PageResult
} from "@/services/skillService";

const emptyPage: PageResult<Skill> = { records: [], total: 0, size: 10, current: 1, pages: 0 };

export function SkillManagePage() {
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(emptyPage);
  const [skillId, setSkillId] = useState("");
  const [skill, setSkill] = useState<Skill | null>(null);
  const [suggestions, setSuggestions] = useState<SkillSuggestion[]>([]);
  const [availableTools, setAvailableTools] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [showSuggestionTab, setShowSuggestionTab] = useState(false);

  // Editor state
  const [sKey, setSKey] = useState(""); const [sName, setSName] = useState(""); const [sDesc, setSDesc] = useState("");
  const [sCategory, setSCategory] = useState(""); const [sTags, setSTags] = useState("");
  const [sTools, setSTools] = useState(""); const [sSop, setSSop] = useState("");
  const [sRules, setSRules] = useState(""); const [sPrompt, setSPrompt] = useState("");
  const [sOutput, setSOutput] = useState(""); const [sStatus, setSStatus] = useState("ENABLED");

  const loadSkills = useCallback(async (pageNo = 1) => {
    setBusy(true);
    try { setPage(await getSkills({ pageNo, pageSize: page.size, keyword: keyword.trim() || undefined })); }
    catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  }, [keyword, page.size]);

  useEffect(() => { loadSkills(1); getAvailableTools().then(setAvailableTools).catch(() => {}); }, []);

  const loadSkill = async (id: string) => {
    setBusy(true); setSkillId(id);
    try {
      const s = await getSkill(id); setSkill(s);
      setSKey(s.skillKey); setSName(s.name); setSDesc(s.description || ""); setSCategory(s.category || "");
      setSTags((s.tags || []).join(", ")); setSTools((s.tools || []).join(", "));
      setSSop(s.sopContent || ""); setSRules(s.domainRules || ""); setSPrompt(s.promptTemplate || "");
      setSOutput(s.outputSpec || ""); setSStatus(s.status);
      setSuggestions(await getSuggestions(id)); setShowSuggestionTab(false);
    } catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  };

  const resetForm = () => { setSkillId(""); setSkill(null); setSuggestions([]);
    setSKey(""); setSName(""); setSDesc(""); setSCategory(""); setSTags(""); setSTools("");
    setSSop(""); setSRules(""); setSPrompt(""); setSOutput(""); setSStatus("ENABLED"); };

  const save = async () => {
    if (!sKey.trim() || !sName.trim()) return toast.error("Skill Key 和名称不能为空");
    const payload: SkillCreatePayload = {
      skillKey: sKey, name: sName, description: sDesc, version: "1.0.0", category: sCategory,
      tags: sTags.split(",").map(t => t.trim()).filter(Boolean),
      tools: sTools.split(",").map(t => t.trim()).filter(Boolean),
      sopContent: sSop, domainRules: sRules, promptTemplate: sPrompt, outputSpec: sOutput, status: sStatus
    };
    setBusy(true);
    try {
      const result = skillId ? await updateSkill(skillId, payload) : await createSkill(payload);
      setSkillId(result.id); setSkill(result);
      toast.success("已保存"); await loadSkills(1);
    } catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  };

  const del = async () => {
    if (!skillId) return; if (!window.confirm("确认删除？")) return;
    setBusy(true);
    try { await deleteSkill(skillId); resetForm(); await loadSkills(1); toast.success("已删除"); }
    catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  };

  const handleApprove = async (suggestionId: string) => {
    setBusy(true);
    try { await approveSuggestion(skillId, suggestionId); setSuggestions(await getSuggestions(skillId)); toast.success("已应用变更"); }
    catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  };
  const handleReject = async (suggestionId: string) => {
    setBusy(true);
    try { await rejectSuggestion(skillId, suggestionId); setSuggestions(await getSuggestions(skillId)); toast.success("已拒绝"); }
    catch (e) { toast.error(getErrorMessage(e)); } finally { setBusy(false); }
  };

  return (
    <div className="admin-page space-y-6">
      <section className="rounded-2xl bg-gradient-to-br from-slate-950 to-indigo-950 p-6 text-white">
        <div className="inline-flex items-center gap-2 rounded-lg border border-white/15 bg-white/10 px-3 py-1 text-xs"><BookOpen className="h-3.5 w-3.5" /> Skill Management</div>
        <h1 className="mt-4 text-3xl font-semibold">Skill 管理与审核</h1>
        <p className="mt-2 text-sm text-slate-300">管理业务 Skill 库，审核 AI 生成的进化建议。</p>
      </section>

      <div className="grid gap-6 xl:grid-cols-[340px_minmax(0,1fr)]">
        {/* Left: List */}
        <Card>
          <CardHeader><CardTitle>Skill 列表</CardTitle></CardHeader>
          <CardContent className="space-y-3">
            <div className="flex gap-2"><Input value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="搜索" /><Button variant="outline" onClick={() => loadSkills(1)}><Search className="h-4 w-4" /></Button></div>
            <div className="max-h-[500px] overflow-auto space-y-2">
              {page.records.map(s => (
                <button key={s.id} onClick={() => loadSkill(s.id)}
                  className={cn("w-full rounded-xl border p-3 text-left", s.id === skillId ? "border-indigo-200 bg-indigo-50" : "hover:bg-slate-50")}>
                  <div className="flex justify-between"><b className="text-sm">{s.name}</b><Badge variant="outline">{s.status}</Badge></div>
                  <div className="text-xs text-slate-400 mt-1">{s.skillKey} · v{s.version}</div>
                  {s.sourceFile && <div className="text-[10px] text-slate-300 mt-0.5">📄 {s.sourceFile}</div>}
                </button>
              ))}
            </div>
            <Button className="w-full" variant="outline" onClick={resetForm}><Plus className="mr-2 h-4 w-4" />新建 Skill</Button>
          </CardContent>
        </Card>

        {/* Right: Editor + Suggestions */}
        <div className="space-y-6">
          <Card>
            <CardHeader className="flex flex-row justify-between items-center">
              <CardTitle>{skillId ? "编辑 Skill" : "新建 Skill"}</CardTitle>
              <div className="flex gap-2">
                {skillId && <Button variant="outline" className="border-red-200 text-red-700" onClick={del}><Trash2 className="mr-2 h-4 w-4" />删除</Button>}
                <Button onClick={save} disabled={busy}><Save className="mr-2 h-4 w-4" />保存</Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-3 md:grid-cols-3">
                <div><Label>Skill Key *</Label><Input value={sKey} onChange={e => setSKey(e.target.value)} placeholder="如 vip_renewal_anomaly" disabled={!!skill?.sourceFile} /></div>
                <div><Label>名称 *</Label><Input value={sName} onChange={e => setSName(e.target.value)} placeholder="VIP续费异常处理" /></div>
                <div><Label>分类</Label><Input value={sCategory} onChange={e => setSCategory(e.target.value)} placeholder="ticket_handling" /></div>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <div><Label>描述</Label><Input value={sDesc} onChange={e => setSDesc(e.target.value)} /></div>
                <div>
                  <Label>状态</Label>
                  <select className="w-full h-10 rounded-md border px-3 text-sm" value={sStatus} onChange={e => setSStatus(e.target.value)}>
                    <option value="ENABLED">启用</option><option value="DISABLED">禁用</option><option value="DRAFT">草稿</option>
                  </select>
                </div>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <div><Label>标签 (逗号分隔)</Label><Input value={sTags} onChange={e => setSTags(e.target.value)} placeholder="VIP, 续费, 风控" /></div>
                <div>
                  <Label>工具绑定 <span className="text-xs text-slate-400">({availableTools.length}个可用)</span></Label>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {availableTools.map(t => {
                      const selected = sTools.split(",").map(x => x.trim()).includes(t);
                      return <Badge key={t} variant={selected ? "default" : "outline"} className="cursor-pointer text-xs"
                        onClick={() => {
                          const list = sTools.split(",").map(x => x.trim()).filter(Boolean);
                          setSTools(selected ? list.filter(x => x !== t).join(", ") : [...list, t].join(", "));
                        }}>{t} {selected ? "✓" : "+"}</Badge>;
                    })}
                  </div>
                </div>
              </div>
              <hr />
              <div><Label>SOP (标准处理流程)</Label><Textarea className="min-h-[100px] font-mono text-xs" value={sSop} onChange={e => setSSop(e.target.value)} /></div>
              <div><Label>领域规则</Label><Textarea className="min-h-[80px] font-mono text-xs" value={sRules} onChange={e => setSRules(e.target.value)} /></div>
              <div><Label>提示词模板</Label><Textarea className="min-h-[80px] font-mono text-xs" value={sPrompt} onChange={e => setSPrompt(e.target.value)} /></div>
              <div><Label>输出规范</Label><Textarea className="min-h-[60px] font-mono text-xs" value={sOutput} onChange={e => setSOutput(e.target.value)} /></div>
            </CardContent>
          </Card>

          {/* Suggestions */}
          {skillId && (
            <Card>
              <CardHeader className="flex flex-row justify-between items-center cursor-pointer" onClick={() => setShowSuggestionTab(!showSuggestionTab)}>
                <CardTitle className="flex items-center gap-2"><Lightbulb className="h-5 w-5 text-amber-500" />进化建议 ({suggestions.length})</CardTitle>
                {showSuggestionTab ? <ChevronDown className="h-5 w-5" /> : <ChevronRight className="h-5 w-5" />}
              </CardHeader>
              {showSuggestionTab && (
                <CardContent className="space-y-4">
                  {suggestions.length === 0 && <p className="text-sm text-slate-400">暂无待审核建议</p>}
                  {suggestions.map(s => (
                    <div key={s.id} className="rounded-xl border-2 border-amber-200 bg-amber-50/30 p-4">
                      <div className="flex items-center justify-between mb-2">
                        <Badge className="bg-amber-100 text-amber-700">{FIELD_LABELS[s.fieldPath] || s.fieldPath}</Badge>
                        <div className="flex items-center gap-2">
                          <Badge variant="outline" className="text-xs">置信度 {(s.confidence * 100).toFixed(0)}%</Badge>
                          <Button size="sm" variant="outline" className="border-red-200 text-red-700" onClick={() => handleReject(s.id)} disabled={busy}><X className="h-3 w-3 mr-1" />拒绝</Button>
                          <Button size="sm" className="bg-emerald-600 hover:bg-emerald-700" onClick={() => handleApprove(s.id)} disabled={busy}><Check className="h-3 w-3 mr-1" />通过</Button>
                        </div>
                      </div>
                      {s.reason && <p className="text-sm text-slate-600 mb-2">理由: {s.reason}</p>}
                      {s.originalText && <div className="mb-2"><Label className="text-xs text-red-500">原文</Label><div className="text-xs bg-red-50 rounded p-2 mt-1 line-through text-slate-500">{s.originalText.substring(0, 200)}</div></div>}
                      <div><Label className="text-xs text-emerald-600">建议改为</Label><div className="text-xs bg-emerald-50 rounded p-2 mt-1">{s.suggestedText.substring(0, 300)}</div></div>
                    </div>
                  ))}
                </CardContent>
              )}
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
