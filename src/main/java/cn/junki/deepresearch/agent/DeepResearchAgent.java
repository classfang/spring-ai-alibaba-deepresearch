package cn.junki.deepresearch.agent;

import cn.junki.deepresearch.agent.env.AgentEnv;
import cn.junki.deepresearch.agent.prompt.Prompts;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.extension.interceptor.*;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.contextediting.ContextEditingInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * DeepResearch Agent
 *
 * @author Junki
 * @since 2025-12-19
 */
public class DeepResearchAgent {

    private final String systemPrompt;
    private final ChatModel chatModel;

    // Interceptors
    private final LargeResultEvictionInterceptor largeResultEvictionInterceptor;
    private final FilesystemInterceptor filesystemInterceptor;
    private final TodoListInterceptor todoListInterceptor;
    private final PatchToolCallsInterceptor patchToolCallsInterceptor;
    private final ContextEditingInterceptor contextEditingInterceptor;
    private final ToolRetryInterceptor toolRetryInterceptor;

    // Hooks
    private final SummarizationHook summarizationHook;
    private final HumanInTheLoopHook humanInTheLoopHook;
    private final ToolCallLimitHook toolCallLimitHook;
    private final ShellToolAgentHook shellToolAgentHook;

    public DeepResearchAgent() {
        // Create DashScopeApi instance using the API key from environment variable
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(AgentEnv.AI_DASHSCOPE_API_KEY).build();
        // Create DashScope ChatModel instance
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();

        this.systemPrompt = Prompts.RESEARCH_PROMPT + "\n\n" + Prompts.AGENT_BASE_PROMPT;

        // Initialize interceptors
        this.largeResultEvictionInterceptor = LargeResultEvictionInterceptor
                .builder()
                .excludeFilesystemTools()
                .toolTokenLimitBeforeEvict(5000)
                .build();

        this.filesystemInterceptor = FilesystemInterceptor.builder()
                .readOnly(false)
                .build();

        this.todoListInterceptor = TodoListInterceptor.builder().build();

        this.patchToolCallsInterceptor = PatchToolCallsInterceptor.builder().build();

        this.toolRetryInterceptor = ToolRetryInterceptor.builder()
                .maxRetries(1).onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
                .build();

        // Initialize hooks
        this.summarizationHook = SummarizationHook.builder()
                // should use another model for summarization
                .model(chatModel)
                .maxTokensBeforeSummary(120000)
                .messagesToKeep(6)
                .build();

        this.humanInTheLoopHook = HumanInTheLoopHook.builder()
                .approvalOn("search_web", "Please approve the search_web tool.")
                .build();

        this.toolCallLimitHook = ToolCallLimitHook.builder()
                .runLimit(25)
                .build();

        this.shellToolAgentHook = ShellToolAgentHook.builder().build();

        this.contextEditingInterceptor = ContextEditingInterceptor.builder()
                .trigger(10000)
                .clearAtLeast(6000)
                .keep(4)
                .excludeTools("write_todos")
                .build();

    }

    private static SubAgentSpec createCritiqueAgent() {
        return SubAgentSpec.builder()
                .name("critique-agent")
                .description("Used to critique the final report. Provide information about " +
                        "how you want the report to be critiqued.")
                .systemPrompt(Prompts.SUB_CRITIQUE_PROMPT)
                .enableLoopingLog(true)
                .build();
    }

    private static SubAgentSpec createResearchAgent(List<ToolCallback> toolsFromMcp) {
        return SubAgentSpec.builder()
                .name("research-agent")
                .description("Used to research in-depth questions. Only give one topic at a time. " +
                        "Break down large topics into components and call multiple research agents " +
                        "in parallel for each sub-question.")
                .systemPrompt(Prompts.SUB_RESEARCH_PROMPT)
                .tools(toolsFromMcp)
                .enableLoopingLog(true)
                .build();
    }

    public ReactAgent getResearchAgent(List<ToolCallback> toolsFromMcp) {
        // Build the ReactAgent with all interceptors
        return ReactAgent.builder()
                .name("DeepResearchAgent")
                .model(chatModel)
                .tools(toolsFromMcp)
                .systemPrompt(systemPrompt)
                .enableLogging(true)
                .interceptors(todoListInterceptor,
                        filesystemInterceptor,
                        largeResultEvictionInterceptor,
                        patchToolCallsInterceptor,
                        contextEditingInterceptor,
                        toolRetryInterceptor,
                        subAgentAsInterceptors(toolsFromMcp))
                .hooks(humanInTheLoopHook, summarizationHook, toolCallLimitHook, shellToolAgentHook)
                .saver(new MemorySaver())
                .build();

    }

    private Interceptor subAgentAsInterceptors(List<ToolCallback> toolsFromMcp) {
        SubAgentSpec researchAgent = createResearchAgent(toolsFromMcp);
        SubAgentSpec critiqueAgent = createCritiqueAgent();

        SubAgentInterceptor.Builder subAgentBuilder = SubAgentInterceptor.builder()
                .defaultModel(chatModel)
                .defaultInterceptors(
                        todoListInterceptor,
                        filesystemInterceptor,
                        contextEditingInterceptor,
                        patchToolCallsInterceptor,
                        largeResultEvictionInterceptor
                )
                .defaultHooks(humanInTheLoopHook, summarizationHook, toolCallLimitHook, shellToolAgentHook)
                .addSubAgent(researchAgent)
                .includeGeneralPurpose(true)
                .addSubAgent(critiqueAgent);
        return subAgentBuilder.build();
    }

}
