-- Structured Skill schema
-- Skill 定义 + 自进化变更建议

CREATE TABLE IF NOT EXISTS t_agent_skill (
    id               VARCHAR(20)  NOT NULL PRIMARY KEY,
    skill_key        VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    description      VARCHAR(512),
    version          VARCHAR(16)  NOT NULL DEFAULT '1.0.0',
    category         VARCHAR(64),
    tags             JSONB        DEFAULT '[]',
    tools            JSONB        DEFAULT '[]',
    sop_content      TEXT,
    domain_rules     TEXT,
    prompt_template  TEXT,
    output_spec      TEXT,
    source_file      VARCHAR(256),                 -- 来源 .md 文件路径（文件定义时有值，管理员创建为 null）
    status           VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    created_by       VARCHAR(64),
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT     DEFAULT 0,
    CONSTRAINT uk_agent_skill_key UNIQUE (skill_key)
);
CREATE INDEX IF NOT EXISTS idx_agent_skill_category ON t_agent_skill (category);
CREATE INDEX IF NOT EXISTS idx_agent_skill_status ON t_agent_skill (status);
COMMENT ON TABLE t_agent_skill IS 'Agent Skill定义表';
COMMENT ON COLUMN t_agent_skill.skill_key IS 'Skill唯一标识，对应 .md 文件 id';
COMMENT ON COLUMN t_agent_skill.source_file IS '来源 .md 文件路径，管理员手动创建的为 null';
COMMENT ON COLUMN t_agent_skill.sop_content IS '标准处理流程（SOP）';
COMMENT ON COLUMN t_agent_skill.domain_rules IS '领域规则';
COMMENT ON COLUMN t_agent_skill.prompt_template IS '提示词模板';
COMMENT ON COLUMN t_agent_skill.output_spec IS '输出规范';

CREATE TABLE IF NOT EXISTS t_agent_skill_suggestion (
    id               VARCHAR(20)  NOT NULL PRIMARY KEY,
    skill_id         VARCHAR(20)  NOT NULL,
    suggestion_type  VARCHAR(32)  NOT NULL DEFAULT 'UPDATE',  -- UPDATE / ADD / DELETE
    field_path       VARCHAR(128),                              -- 要修改的字段: sop_content / domain_rules / prompt_template
    original_text    TEXT,
    suggested_text   TEXT,
    reason           TEXT,                                      -- LLM 给出的修改理由
    confidence       DECIMAL(3,2) DEFAULT 0.50,                 -- 置信度 0.00-1.00
    source_instance  VARCHAR(20),                               -- 触发这次建议的 workflow instance ID
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING',   -- PENDING / APPROVED / REJECTED / APPLIED
    reviewed_by      VARCHAR(64),
    review_comment   TEXT,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT     DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_skill_suggestion_skill ON t_agent_skill_suggestion (skill_id);
CREATE INDEX IF NOT EXISTS idx_agent_skill_suggestion_status ON t_agent_skill_suggestion (status);
COMMENT ON TABLE t_agent_skill_suggestion IS 'Skill自进化变更建议表';
COMMENT ON COLUMN t_agent_skill_suggestion.suggestion_type IS '建议类型: UPDATE(修改)/ADD(新增)/DELETE(删除)';
COMMENT ON COLUMN t_agent_skill_suggestion.field_path IS '修改的目标字段名';
COMMENT ON COLUMN t_agent_skill_suggestion.confidence IS 'LLM 给出的置信度 0.00-1.00';
COMMENT ON COLUMN t_agent_skill_suggestion.source_instance IS '触发建议的 workflow instance ID';
