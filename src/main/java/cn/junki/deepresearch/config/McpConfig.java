package cn.junki.deepresearch.config;

import cn.junki.deepresearch.agent.env.AgentEnv;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            builder.header("Authorization", "Bearer " + AgentEnv.JINA_API_KEY);
            builder.timeout(java.time.Duration.ofSeconds(120));
        };
    }

}
