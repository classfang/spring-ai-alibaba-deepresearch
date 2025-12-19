package cn.junki.deepresearch.agent.loader;

import cn.junki.deepresearch.agent.DeepResearchAgent;
import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 静态代理加载程序提供的代理：这个加载器预先创建了一个Agent实例的静态列表。
 *
 * @author Junki
 * @since 2025-12-19
 */
@Slf4j
@Component
class AgentStaticLoader implements AgentLoader {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentStaticLoader(ToolCallbackProvider toolCallbackProvider) {

        List<ToolCallback> toolCallbacks = Arrays.asList(toolCallbackProvider.getToolCallbacks());

        log.info("Loaded MCP tool callbacks: {}", toolCallbacks.size());

        ReactAgent researchAgent = new DeepResearchAgent().getResearchAgent(toolCallbacks);
        GraphRepresentation representation = researchAgent.getAndCompileGraph().stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);

        log.info("DeepResearchAgent Graph: {}", representation.content());

        this.agents.put("research_agent", researchAgent);
    }

    @Override
    @Nonnull
    public List<String> listAgents() {
        return agents.keySet().stream().toList();
    }

    @Override
    public Agent loadAgent(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name cannot be null or empty");
        }

        Agent agent = agents.get(name);
        if (agent == null) {
            throw new NoSuchElementException("Agent not found: " + name);
        }

        return agent;
    }
}
