# DeepResearch Agent

基于 Spring AI 和阿里云 DashScope 的深度研究 Agent，支持多代理协作、工具调用和上下文管理。

## ✨ 特性

- 🔍 **深度研究能力**：通过子代理模式进行多层次的深度研究
- 🤖 **多代理协作**：包含研究代理（Research Agent）和批判代理（Critique Agent）
- 🛠️ **工具集成**：通过 MCP (Model Context Protocol) 集成外部工具（如 Jina 搜索）
- 📝 **智能报告生成**：自动生成结构化的 Markdown 研究报告
- 🔄 **迭代优化**：通过批判代理反馈不断优化报告质量
- 💾 **上下文管理**：智能的上下文编辑和结果清理机制
- 🎯 **任务管理**：内置待办事项列表管理
- 🔁 **工具重试**：自动重试失败的工具调用

## 🏗️ 架构

### 核心组件

- **DeepResearchAgent**: 主研究代理，协调整个研究流程
- **Research Agent**: 子代理，负责深度研究特定主题
- **Critique Agent**: 子代理，负责批判和优化研究报告

### 拦截器（Interceptors）

- `TodoListInterceptor`: 任务列表管理
- `FilesystemInterceptor`: 文件系统操作
- `LargeResultEvictionInterceptor`: 大结果清理
- `PatchToolCallsInterceptor`: 工具调用补丁
- `ContextEditingInterceptor`: 上下文编辑
- `ToolRetryInterceptor`: 工具重试
- `SubAgentInterceptor`: 子代理调用

### 钩子（Hooks）

- `SummarizationHook`: 对话摘要
- `HumanInTheLoopHook`: 人工审核
- `ToolCallLimitHook`: 工具调用限制
- `ShellToolAgentHook`: Shell 工具支持

## 🚀 快速开始

### 前置要求

- Java 17+
- Maven 3.6+
- 阿里云 DashScope API Key
- Jina API Key（可选，用于搜索功能）

### 环境变量配置

在运行前，请设置以下环境变量：

```bash
export AI_DASHSCOPE_API_KEY=your_dashscope_api_key
export JINA_API_KEY=your_jina_api_key  # 可选
```

### 构建和运行

```bash
# 克隆项目
git clone <repository-url>
cd spring-ai-alibaba-deepresearch

# 构建项目
mvn clean package

# 运行应用
mvn spring-boot:run
```

应用启动后，访问 `http://localhost:8080/chatui/index.html` 与 Agent 交互。

## 👥 贡献

欢迎提交 Issue 和 Pull Request！
