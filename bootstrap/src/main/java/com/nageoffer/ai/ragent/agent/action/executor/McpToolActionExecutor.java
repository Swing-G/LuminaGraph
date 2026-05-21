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

package com.nageoffer.ai.ragent.agent.action.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.ragent.agent.action.domain.ActionConfig;
import com.nageoffer.ai.ragent.agent.action.domain.ActionContext;
import com.nageoffer.ai.ragent.agent.action.domain.ActionResult;
import com.nageoffer.ai.ragent.agent.action.enums.ActionType;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP工具动作执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolActionExecutor implements AgentActionExecutor {

    private final McpToolRegistry mcpToolRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public String actionType() {
        return ActionType.MCP_TOOL.name();
    }

    @Override
    public ActionResult execute(ActionContext context, ActionConfig config) {
        JsonNode actionConfig = config.getConfig();
        String toolName = actionConfig == null ? null : actionConfig.path("toolName").asText(null);
        if (!StringUtils.hasText(toolName)) {
            throw new ClientException("MCP工具名称不能为空");
        }
        Map<String, Object> parameters = readParameters(context, actionConfig);
        long startMs = System.currentTimeMillis();
        McpToolExecutor executor = mcpToolRegistry.getExecutor(toolName)
                .orElseThrow(() -> new ClientException("未找到MCP工具: " + toolName));
        CallToolResult toolResult = executor.execute(parameters);
        boolean isError = Boolean.TRUE.equals(toolResult.isError());

        ObjectNode output = objectMapper.createObjectNode();
        output.put("isError", isError);
        output.set("content", objectMapper.valueToTree(toolResult.content()));
        output.set("structuredContent", objectMapper.valueToTree(toolResult.structuredContent()));

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("actionType", actionType());
        metadata.put("toolName", toolName);
        metadata.put("elapsedMs", System.currentTimeMillis() - startMs);
        metadata.set("parameters", objectMapper.valueToTree(parameters));

        log.info("Workflow MCP Action执行完成, instanceId={}, nodeKey={}, toolName={}, parameters={}, isError={}",
                context.getInstanceId(), context.getNodeKey(), toolName, parameters, isError);

        return isError ? ActionResult.fail("MCP工具调用失败", metadata) : ActionResult.success(output, metadata);
    }

    private Map<String, Object> readParameters(ActionContext context, JsonNode actionConfig) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        JsonNode parametersNode = actionConfig.path("parameters");
        if (parametersNode.isObject()) {
            parameters.putAll(objectMapper.convertValue(parametersNode, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)));
        }
        JsonNode inputParametersNode = actionConfig.path("inputParameters");
        if (inputParametersNode.isArray()) {
            for (JsonNode nameNode : inputParametersNode) {
                String name = nameNode.asText(null);
                if (StringUtils.hasText(name) && context.getInput() != null && context.getInput().has(name)) {
                    JsonNode value = context.getInput().path(name);
                    if (!value.isNull() && !value.isMissingNode()) {
                        parameters.put(name, value.isValueNode() ? value.asText() : objectMapper.convertValue(value, Object.class));
                    }
                }
            }
        }
        return parameters;
    }
}
