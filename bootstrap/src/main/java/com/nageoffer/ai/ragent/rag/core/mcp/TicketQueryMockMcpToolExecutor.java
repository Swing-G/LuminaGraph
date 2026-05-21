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

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地工单查询 Mock MCP 工具
 */
@Component
public class TicketQueryMockMcpToolExecutor implements McpToolExecutor {

    private static final String TOOL_NAME = "ticket.query";

    private final Tool toolDefinition;

    public TicketQueryMockMcpToolExecutor() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ticketId", Map.of(
                "type", "string",
                "description", "工单编号，例如 T20260520001"
        ));
        JsonSchema inputSchema = new JsonSchema("object", properties, List.of("ticketId"), false, null, null);
        this.toolDefinition = new Tool(
                TOOL_NAME,
                "工单查询",
                "根据工单编号查询工单摘要、风险等级和处理状态",
                inputSchema,
                null,
                null,
                Map.of("mock", true)
        );
    }

    @Override
    public Tool getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public CallToolResult execute(Map<String, Object> parameters) {
        String ticketId = parameters == null ? null : String.valueOf(parameters.get("ticketId"));
        if (!StringUtils.hasText(ticketId) || "null".equals(ticketId)) {
            return CallToolResult.builder()
                    .content(List.of(new TextContent("缺少必填参数 ticketId")))
                    .isError(true)
                    .build();
        }
        String riskLevel = ticketId.endsWith("001") ? "HIGH" : "LOW";
        String content = "工单 " + ticketId + " 查询成功：当前状态=处理中，风险等级=" + riskLevel + "，责任团队=售后支持，建议动作=优先跟进客户反馈。";
        return CallToolResult.builder()
                .content(List.of(new TextContent(content)))
                .isError(false)
                .structuredContent(Map.of(
                        "ticketId", ticketId,
                        "status", "PROCESSING",
                        "riskLevel", riskLevel,
                        "ownerTeam", "售后支持"
                ))
                .build();
    }
}
