package cn.junki.deepresearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.junki.deepresearch.agent.env.AgentEnv;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;

/**
 * MCP自定义配置类
 *
 * @author Junki
 * @since 2025-12-19
 */
@Configuration
public class McpConfig {

    @Bean
    public McpSyncHttpClientRequestCustomizer mcpSyncHttpClientRequestCustomizer() {
        return (builder, method, endpoint, body, context) -> {
            // 请求头添加jina服务的api_key
            builder.header("Authorization", "Bearer " + AgentEnv.JINA_API_KEY);
            builder.timeout(java.time.Duration.ofSeconds(120));
        };
    }

}
