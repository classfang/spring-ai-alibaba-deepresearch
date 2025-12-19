package cn.junki.deepresearch.agent.env;

/**
 * 环境变量
 *
 * @author Junki
 * @since 2025-12-19
 */
public interface AgentEnv {

    String AI_DASHSCOPE_API_KEY = System.getenv("AI_DASHSCOPE_API_KEY");
    String JINA_API_KEY = System.getenv("JINA_API_KEY");

}
