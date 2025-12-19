package cn.junki.deepresearch.agent;

import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.FilesystemInterceptor;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.LargeResultEvictionInterceptor;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.PatchToolCallsInterceptor;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.SubAgentInterceptor;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.SubAgentSpec;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.contextediting.ContextEditingInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import cn.junki.deepresearch.agent.env.AgentEnv;
import cn.junki.deepresearch.agent.prompt.Prompts;

/**
 * DeepResearch Agent
 *
 * @author Junki
 * @since 2025-12-19
 */
public class DeepResearchAgent {

    /** 系统提示词，包含研究提示和代理基础提示 */
    private final String systemPrompt;

    /** 聊天模型实例，用于与 AI 模型交互 */
    private final ChatModel chatModel;

    // ========== 拦截器（Interceptors） ==========

    /** 大结果驱逐拦截器：当工具返回结果超过指定 token 限制时自动清理 */
    private final LargeResultEvictionInterceptor largeResultEvictionInterceptor;

    /** 文件系统拦截器：控制文件系统操作的权限（读写/只读） */
    private final FilesystemInterceptor filesystemInterceptor;

    /** 任务列表拦截器：管理代理的任务列表 */
    private final TodoListInterceptor todoListInterceptor;

    /** 工具调用补丁拦截器：修复或优化工具调用 */
    private final PatchToolCallsInterceptor patchToolCallsInterceptor;

    /** 上下文编辑拦截器：当上下文过长时自动清理和编辑 */
    private final ContextEditingInterceptor contextEditingInterceptor;

    /** 工具重试拦截器：在工具调用失败时自动重试 */
    private final ToolRetryInterceptor toolRetryInterceptor;

    // ========== 钩子（Hooks） ==========

    /** 摘要钩子：当消息历史过长时自动生成摘要 */
    private final SummarizationHook summarizationHook;

    /** 人工介入钩子：在特定工具调用前请求人工批准 */
    private final HumanInTheLoopHook humanInTheLoopHook;

    /** 工具调用限制钩子：限制代理的最大工具调用次数 */
    private final ToolCallLimitHook toolCallLimitHook;

    /** Shell 工具代理钩子：提供 Shell 命令执行能力 */
    private final ShellToolAgentHook shellToolAgentHook;

    /**
     * 构造函数：初始化 DeepResearch Agent 的所有组件
     */
    public DeepResearchAgent() {
        // 使用环境变量中的 API Key 创建 DashScopeApi 实例
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(AgentEnv.AI_DASHSCOPE_API_KEY).build();
        // 创建 DashScope ChatModel 实例
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();

        // 组合研究提示和代理基础提示作为系统提示词
        this.systemPrompt = Prompts.RESEARCH_PROMPT + "\n\n" + Prompts.AGENT_BASE_PROMPT;

        // ========== 初始化拦截器 ==========

        // 大结果驱逐拦截器：排除文件系统工具，当工具返回结果超过 5000 tokens 时清理
        this.largeResultEvictionInterceptor = LargeResultEvictionInterceptor
                .builder()
                .excludeFilesystemTools()
                .toolTokenLimitBeforeEvict(5000)
                .build();

        // 文件系统拦截器：允许读写操作（非只读模式）
        this.filesystemInterceptor = FilesystemInterceptor.builder()
                .readOnly(false)
                .build();

        // 任务列表拦截器：管理代理的任务列表
        this.todoListInterceptor = TodoListInterceptor.builder().build();

        // 工具调用补丁拦截器：修复或优化工具调用
        this.patchToolCallsInterceptor = PatchToolCallsInterceptor.builder().build();

        // 工具重试拦截器：最多重试 1 次，失败时返回错误消息
        this.toolRetryInterceptor = ToolRetryInterceptor.builder()
                .maxRetries(1).onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .build();

        // ========== 初始化钩子 ==========

        // 摘要钩子：当消息 token 数超过 120000 时生成摘要，保留最近 6 条消息
        // 注意：建议使用另一个模型进行摘要以提高效率
        this.summarizationHook = SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(120000)
                .messagesToKeep(6)
                .build();

        // 人工介入钩子：在调用 "search_web" 工具前请求人工批准
        this.humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("search_web", "Please approve the search_web tool.")
                .build();

        // 工具调用限制钩子：限制代理最多执行 25 次工具调用
        this.toolCallLimitHook = ToolCallLimitHook.builder()
                .runLimit(25)
                .build();

        // Shell 工具代理钩子：提供 Shell 命令执行能力
        this.shellToolAgentHook = ShellToolAgentHook.builder().build();

        // 上下文编辑拦截器：当上下文 token 数达到 10000 时触发清理，
        // 至少清理 6000 tokens，保留最近 4 条消息，排除 "write_todos" 工具
        this.contextEditingInterceptor = ContextEditingInterceptor.builder()
                .trigger(10000)
                .clearAtLeast(6000)
                .keep(4)
                .excludeTools("write_todos")
                .build();

    }

    /**
     * 创建评审代理（Critique Agent）的配置
     *
     * @return 评审代理的配置规范
     */
    private static SubAgentSpec createCritiqueAgent() {
        return SubAgentSpec.builder()
                .name("critique-agent")
                .description("Used to critique the final report. Provide information about " +
                        "how you want the report to be critiqued.")
                .systemPrompt(Prompts.SUB_CRITIQUE_PROMPT)
                .enableLoopingLog(true)
                .build();
    }

    /**
     * 创建研究代理（Research Agent）的配置
     *
     * @param toolsFromMcp 从 MCP（Model Context Protocol）获取的工具列表
     * @return 研究代理的配置规范
     */
    private static SubAgentSpec createResearchAgent(List<ToolCallback> toolsFromMcp) {
        return SubAgentSpec.builder()
                .name("research-agent")
                .description("Used to research in-depth questions. Only give one topic at a time. " +
                        "Break down large topics into components and call multiple research agents "
                        +
                        "in parallel for each sub-question.")
                .systemPrompt(Prompts.SUB_RESEARCH_PROMPT)
                .tools(toolsFromMcp)
                .enableLoopingLog(true)
                .build();
    }

    /**
     * 获取配置好的研究代理实例
     *
     * @param toolsFromMcp 从 MCP（Model Context Protocol）获取的工具列表
     * @return 配置完成的 ReactAgent 实例
     */
    public ReactAgent getResearchAgent(List<ToolCallback> toolsFromMcp) {
        // 构建包含所有拦截器的 ReactAgent
        return ReactAgent.builder()
                .name("DeepResearchAgent")
                .model(chatModel)
                .tools(toolsFromMcp)
                .systemPrompt(systemPrompt)
                .enableLogging(true)
                // 配置所有拦截器：任务列表、文件系统、大结果驱逐、工具调用补丁、
                // 上下文编辑、工具重试，以及子代理拦截器
                .interceptors(todoListInterceptor,
                        filesystemInterceptor,
                        largeResultEvictionInterceptor,
                        patchToolCallsInterceptor,
                        contextEditingInterceptor,
                        toolRetryInterceptor,
                        subAgentAsInterceptors(toolsFromMcp))
                // 配置所有钩子：人工介入、摘要、工具调用限制、Shell 工具
                .hooks(humanInTheLoopHook, summarizationHook, toolCallLimitHook, shellToolAgentHook)
                // 使用内存保存器保存检查点
                .saver(new MemorySaver())
                .build();

    }

    /**
     * 将子代理转换为拦截器
     *
     * @param toolsFromMcp 从 MCP 获取的工具列表，用于配置研究代理
     * @return 配置好的子代理拦截器
     */
    private Interceptor subAgentAsInterceptors(List<ToolCallback> toolsFromMcp) {
        // 创建研究代理和评审代理的配置
        SubAgentSpec researchAgent = createResearchAgent(toolsFromMcp);
        SubAgentSpec critiqueAgent = createCritiqueAgent();

        // 构建子代理拦截器，配置默认模型、拦截器和钩子
        SubAgentInterceptor.Builder subAgentBuilder = SubAgentInterceptor.builder()
                .defaultModel(chatModel)
                // 子代理默认使用的拦截器
                .defaultInterceptors(
                        todoListInterceptor,
                        filesystemInterceptor,
                        contextEditingInterceptor,
                        patchToolCallsInterceptor,
                        largeResultEvictionInterceptor)
                // 子代理默认使用的钩子
                .defaultHooks(humanInTheLoopHook, summarizationHook, toolCallLimitHook,
                        shellToolAgentHook)
                // 添加研究代理
                .addSubAgent(researchAgent)
                // 包含通用目的代理
                .includeGeneralPurpose(true)
                // 添加评审代理
                .addSubAgent(critiqueAgent);
        return subAgentBuilder.build();
    }

}
