-- Agent Workflow Runtime schema
-- Adds generic workflow/task/action modeling for Agent orchestration.

CREATE TABLE IF NOT EXISTS t_agent_workflow_definition (
    id              VARCHAR(20)  NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    workflow_type   VARCHAR(64)  NOT NULL,
    harness_type    VARCHAR(32)  NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    input_schema    JSONB,
    output_schema   JSONB,
    config_json     JSONB,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_type ON t_agent_workflow_definition (workflow_type);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_status ON t_agent_workflow_definition (status);
COMMENT ON TABLE t_agent_workflow_definition IS 'Agent Workflow定义表';
COMMENT ON COLUMN t_agent_workflow_definition.workflow_type IS '工作流业务类型，如ticket_process、ops_diagnose、private_qa';
COMMENT ON COLUMN t_agent_workflow_definition.harness_type IS '编排引擎类型，如FLOW、REACT、PAE';
COMMENT ON COLUMN t_agent_workflow_definition.config_json IS '工作流全局配置JSON';

CREATE TABLE IF NOT EXISTS t_agent_workflow_node (
    id                   VARCHAR(20)  NOT NULL PRIMARY KEY,
    workflow_id          VARCHAR(20)  NOT NULL,
    node_key             VARCHAR(64)  NOT NULL,
    node_name            VARCHAR(128) NOT NULL,
    node_type            VARCHAR(32)  NOT NULL,
    action_type          VARCHAR(64),
    skill_id             VARCHAR(20),
    config_json          JSONB,
    input_mapping_json   JSONB,
    output_mapping_json  JSONB,
    retry_limit          INTEGER      DEFAULT 0,
    timeout_ms           BIGINT,
    node_order           INTEGER      DEFAULT 0,
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     DEFAULT 0,
    CONSTRAINT uk_agent_workflow_node_key UNIQUE (workflow_id, node_key)
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_node_workflow ON t_agent_workflow_node (workflow_id);
COMMENT ON TABLE t_agent_workflow_node IS 'Agent Workflow节点定义表';
COMMENT ON COLUMN t_agent_workflow_node.node_type IS '节点类型：ACTION、CONDITION、EVALUATOR、REFLECTION、HUMAN_REVIEW、END';
COMMENT ON COLUMN t_agent_workflow_node.action_type IS '动作类型：NOOP、LLM、RAG_RETRIEVE、MCP_TOOL、CONDITION';

CREATE TABLE IF NOT EXISTS t_agent_workflow_edge (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    workflow_id       VARCHAR(20) NOT NULL,
    source_node_key   VARCHAR(64) NOT NULL,
    target_node_key   VARCHAR(64) NOT NULL,
    edge_type         VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    condition_expr    VARCHAR(1024),
    priority          INTEGER     DEFAULT 0,
    create_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_edge_workflow ON t_agent_workflow_edge (workflow_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_edge_source ON t_agent_workflow_edge (workflow_id, source_node_key);
COMMENT ON TABLE t_agent_workflow_edge IS 'Agent Workflow边定义表';
COMMENT ON COLUMN t_agent_workflow_edge.edge_type IS '边类型：DEFAULT、SUCCESS、FAILED、CONDITION等';

CREATE TABLE IF NOT EXISTS t_agent_workflow_instance (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    workflow_id       VARCHAR(20) NOT NULL,
    workflow_version  INTEGER     NOT NULL,
    harness_type      VARCHAR(32) NOT NULL,
    business_type     VARCHAR(64),
    business_id       VARCHAR(64),
    user_id           VARCHAR(20),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    input_json        JSONB,
    context_json      JSONB,
    output_json       JSONB,
    error_message     TEXT,
    current_node_key  VARCHAR(64),
    started_at        TIMESTAMP,
    completed_at      TIMESTAMP,
    create_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_instance_workflow ON t_agent_workflow_instance (workflow_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_instance_status ON t_agent_workflow_instance (status);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_instance_business ON t_agent_workflow_instance (business_type, business_id);
COMMENT ON TABLE t_agent_workflow_instance IS 'Agent Workflow运行实例表';

CREATE TABLE IF NOT EXISTS t_agent_workflow_node_instance (
    id              VARCHAR(20)  NOT NULL PRIMARY KEY,
    instance_id     VARCHAR(20)  NOT NULL,
    workflow_id     VARCHAR(20)  NOT NULL,
    node_key        VARCHAR(64)  NOT NULL,
    node_name       VARCHAR(128) NOT NULL,
    node_type       VARCHAR(32)  NOT NULL,
    action_type     VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    input_json      JSONB,
    output_json     JSONB,
    error_message   TEXT,
    retry_count     INTEGER      DEFAULT 0,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    duration_ms     BIGINT,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_node_instance_instance ON t_agent_workflow_node_instance (instance_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_node_instance_node ON t_agent_workflow_node_instance (instance_id, node_key);
COMMENT ON TABLE t_agent_workflow_node_instance IS 'Agent Workflow节点运行实例表';

CREATE TABLE IF NOT EXISTS t_agent_workflow_event (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    instance_id       VARCHAR(20) NOT NULL,
    node_instance_id  VARCHAR(20),
    event_type        VARCHAR(64) NOT NULL,
    event_level       VARCHAR(16) NOT NULL DEFAULT 'INFO',
    content           TEXT,
    payload_json      JSONB,
    importance_score  INTEGER     DEFAULT 50,
    create_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_event_instance ON t_agent_workflow_event (instance_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_event_importance ON t_agent_workflow_event (instance_id, importance_score);
COMMENT ON TABLE t_agent_workflow_event IS 'Agent Workflow事件记忆与审计日志表';

CREATE TABLE IF NOT EXISTS t_agent_workflow_checkpoint (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    instance_id       VARCHAR(20) NOT NULL,
    node_key          VARCHAR(64) NOT NULL,
    checkpoint_type   VARCHAR(32) NOT NULL,
    context_json      JSONB,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_checkpoint_instance ON t_agent_workflow_checkpoint (instance_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_checkpoint_node ON t_agent_workflow_checkpoint (instance_id, node_key);
COMMENT ON TABLE t_agent_workflow_checkpoint IS 'Agent Workflow上下文Checkpoint表';

CREATE TABLE IF NOT EXISTS t_agent_workflow_memory_summary (
    id                     VARCHAR(20) NOT NULL PRIMARY KEY,
    instance_id            VARCHAR(20) NOT NULL,
    workflow_id            VARCHAR(20) NOT NULL,
    strategy_type          VARCHAR(32) NOT NULL,
    summary_content        TEXT,
    high_importance_events JSONB,
    compressed_context     JSONB,
    last_event_time        TIMESTAMP,
    event_count            INTEGER DEFAULT 0,
    create_time            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_memory_summary_instance ON t_agent_workflow_memory_summary (instance_id);
CREATE INDEX IF NOT EXISTS idx_agent_workflow_memory_summary_strategy ON t_agent_workflow_memory_summary (instance_id, strategy_type);
COMMENT ON TABLE t_agent_workflow_memory_summary IS 'Agent Workflow任务级记忆摘要表';

CREATE TABLE IF NOT EXISTS t_demo_account (
    account_id       VARCHAR(32)  NOT NULL PRIMARY KEY,
    customer_name    VARCHAR(64)  NOT NULL,
    customer_level   VARCHAR(32)  NOT NULL,
    account_status   VARCHAR(32)  NOT NULL,
    owner_user_id    VARCHAR(32),
    risk_flag        BOOLEAN      DEFAULT FALSE,
    last_login_time  TIMESTAMP,
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE t_demo_account IS 'Workflow演示账号表';

CREATE TABLE IF NOT EXISTS t_demo_subscription (
    id                   VARCHAR(32) NOT NULL PRIMARY KEY,
    account_id           VARCHAR(32) NOT NULL,
    plan_name            VARCHAR(64) NOT NULL,
    subscription_status  VARCHAR(32) NOT NULL,
    expire_time          TIMESTAMP,
    renewal_due_amount   NUMERIC(12, 2) DEFAULT 0,
    auto_renew_enabled   BOOLEAN DEFAULT FALSE,
    create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_demo_subscription_account ON t_demo_subscription (account_id);
COMMENT ON TABLE t_demo_subscription IS 'Workflow演示订阅表';

CREATE TABLE IF NOT EXISTS t_demo_payment_order (
    payment_id      VARCHAR(32) NOT NULL PRIMARY KEY,
    account_id      VARCHAR(32) NOT NULL,
    pay_status      VARCHAR(32) NOT NULL,
    failure_reason  VARCHAR(256),
    amount          NUMERIC(12, 2) DEFAULT 0,
    pay_time        TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_demo_payment_account ON t_demo_payment_order (account_id);
COMMENT ON TABLE t_demo_payment_order IS 'Workflow演示支付订单表';

CREATE TABLE IF NOT EXISTS t_demo_ticket (
    ticket_id        VARCHAR(32) NOT NULL PRIMARY KEY,
    account_id       VARCHAR(32) NOT NULL,
    ticket_status    VARCHAR(32) NOT NULL,
    ticket_category  VARCHAR(64) NOT NULL,
    priority         VARCHAR(16) NOT NULL,
    subject          VARCHAR(128) NOT NULL,
    description      TEXT,
    assigned_team    VARCHAR(64),
    latest_note      TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_demo_ticket_account ON t_demo_ticket (account_id);
COMMENT ON TABLE t_demo_ticket IS 'Workflow演示工单表';

INSERT INTO t_demo_account (account_id, customer_name, customer_level, account_status, owner_user_id, risk_flag, last_login_time)
VALUES
    ('A10001', '杭州青杉贸易有限公司', 'VIP', 'ACTIVE', 'U90001', TRUE, '2026-05-20 10:18:00'),
    ('A10002', '上海岚石设计工作室', 'STANDARD', 'ACTIVE', 'U90002', FALSE, '2026-05-19 16:42:00')
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO t_demo_subscription (id, account_id, plan_name, subscription_status, expire_time, renewal_due_amount, auto_renew_enabled)
VALUES
    ('SUB10001', 'A10001', '企业专业版', 'PAST_DUE', '2026-05-18 23:59:59', 2999.00, TRUE),
    ('SUB10002', 'A10002', '团队基础版', 'ACTIVE', '2026-07-31 23:59:59', 699.00, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO t_demo_payment_order (payment_id, account_id, pay_status, failure_reason, amount, pay_time)
VALUES
    ('PAY10001', 'A10001', 'FAILED', '银行卡扣款被银行风控拒绝', 2999.00, '2026-05-20 09:12:00'),
    ('PAY10002', 'A10002', 'SUCCESS', NULL, 699.00, '2026-04-30 11:05:00')
ON CONFLICT (payment_id) DO NOTHING;

INSERT INTO t_demo_ticket (ticket_id, account_id, ticket_status, ticket_category, priority, subject, description, assigned_team, latest_note, create_time, update_time)
VALUES
    ('T20260520001', 'A10001', 'OPEN', '续费失败', 'P1', 'VIP客户投诉续费失败', '客户反馈企业专业版续费失败，担心业务中断，要求尽快给出处理建议。', '售后支持', '已确认最近一次自动扣款失败，待核对扣款渠道。', '2026-05-20 09:25:00', '2026-05-20 10:20:00'),
    ('T20260520002', 'A10002', 'PROCESSING', '账号咨询', 'P3', '用户咨询某账户续费状态', '用户对账户订阅状态有疑问，希望确认是否会影响团队成员使用。', '客户成功', '订阅仍有效，但未开启自动续费。', '2026-05-20 13:10:00', '2026-05-20 13:35:00')
ON CONFLICT (ticket_id) DO NOTHING;
