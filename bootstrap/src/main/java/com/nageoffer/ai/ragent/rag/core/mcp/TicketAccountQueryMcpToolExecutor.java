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

package com.nageoffer.ai.ragent.rag.core.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地账号工单查询 MCP 工具
 */
@Component
@RequiredArgsConstructor
public class TicketAccountQueryMcpToolExecutor implements McpToolExecutor {

    private static final String TOOL_NAME = "ticket.account.query";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Tool getToolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ticketId", Map.of("type", "string", "description", "工单编号，例如 T20260520001"));
        properties.put("accountId", Map.of("type", "string", "description", "业务账号 ID，例如 A10001"));
        JsonSchema inputSchema = new JsonSchema("object", properties, List.of(), false, null, null);
        return new Tool(
                TOOL_NAME,
                "账号工单查询",
                "根据工单号或账号ID查询账号、订阅、支付和工单当前状态",
                inputSchema,
                null,
                null,
                Map.of("local", true, "demo", true)
        );
    }

    @Override
    public CallToolResult execute(Map<String, Object> parameters) {
        String ticketId = readText(parameters, "ticketId");
        String accountId = readText(parameters, "accountId");
        if (!StringUtils.hasText(ticketId) && !StringUtils.hasText(accountId)) {
            return error("缺少 ticketId 或 accountId，至少需要提供一个查询条件");
        }

        Map<String, Object> result = query(ticketId, accountId);
        if (result == null) {
            return error("未查询到匹配的账号或工单数据，ticketId=" + nvl(ticketId) + "，accountId=" + nvl(accountId));
        }
        return CallToolResult.builder()
                .content(List.of(new TextContent(toText(result))))
                .isError(false)
                .structuredContent(result)
                .build();
    }

    private Map<String, Object> query(String ticketId, String accountId) {
        String sql = """
                SELECT
                  a.account_id,
                  a.customer_name,
                  a.customer_level,
                  a.account_status,
                  a.owner_user_id,
                  a.risk_flag,
                  a.last_login_time,
                  s.plan_name,
                  s.subscription_status,
                  s.expire_time,
                  s.renewal_due_amount,
                  s.auto_renew_enabled,
                  p.payment_id,
                  p.pay_status,
                  p.failure_reason,
                  p.amount,
                  p.pay_time,
                  t.ticket_id,
                  t.ticket_status,
                  t.ticket_category,
                  t.priority,
                  t.subject,
                  t.description,
                  t.assigned_team,
                  t.latest_note,
                  t.create_time AS ticket_create_time,
                  t.update_time AS ticket_update_time
                FROM t_demo_account a
                LEFT JOIN t_demo_subscription s ON s.account_id = a.account_id
                LEFT JOIN t_demo_payment_order p ON p.account_id = a.account_id
                LEFT JOIN t_demo_ticket t ON t.account_id = a.account_id
                WHERE (:ticketId IS NULL OR t.ticket_id = :ticketId)
                  AND (:accountId IS NULL OR a.account_id = :accountId)
                ORDER BY t.update_time DESC NULLS LAST, p.pay_time DESC NULLS LAST
                LIMIT 1
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ticketId", StringUtils.hasText(ticketId) ? ticketId : null)
                .addValue("accountId", StringUtils.hasText(accountId) ? accountId : null);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .structuredContent(Map.of("error", message))
                .build();
    }

    private String readText(Map<String, Object> parameters, String key) {
        if (parameters == null || parameters.get(key) == null) {
            return null;
        }
        String value = String.valueOf(parameters.get(key)).trim();
        return "null".equalsIgnoreCase(value) || value.isBlank() ? null : value;
    }

    private String toText(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            return String.valueOf(result);
        }
    }

    private String nvl(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
