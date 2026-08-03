# 需求规格说明书（SRS）

## Spring AI 智能助手项目

| 项目 | 内容 |
|------|------|
| 项目名称 | springAITest（Spring AI 智能助手） |
| 文档版本 | V1.0 |
| 编写日期 | 2026-08-04 |
| 仓库地址 | https://github.com/JokeStarsr/springAITest |

---

## 1. 引言

### 1.1 编写目的

本说明书旨在明确「Spring AI 智能助手」项目的功能需求、非功能需求与技术约束，作为系统设计、开发、测试与验收的依据。读者包括：项目负责人、开发工程师、测试工程师及维护人员。

### 1.2 项目背景

随着大语言模型（LLM）的成熟，AI 应用正从"单一对话"向"多 Agent 协作 + 私有知识库"演进。本项目基于 Spring Boot 3.4.5 + Spring AI 1.0.0 GA 构建，集成 Ollama 本地大模型，演示并落地以下三类核心能力：

1. **多 Agent 协作**：通过智能路由将用户任务分发给天气、研究、写作等专职 Agent。
2. **RAG 知识库**：基于向量检索，让大模型"读懂"用户提供的私有文档。
3. **流式对话**：基于 SSE 实时输出，提升交互体验。

### 1.3 术语与缩写

| 术语 | 含义 |
|------|------|
| LLM | Large Language Model，大语言模型 |
| Agent | 智能体，具备特定职责的 AI 处理单元 |
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| Embedding | 向量化，将文本转换为语义向量 |
| Vector Store | 向量库，存储文本向量并支持相似度检索 |
| SSE | Server-Sent Events，服务器推送事件 |
| BOM | Bill of Materials，依赖版本清单 |

---

## 2. 总体描述

### 2.1 产品定位

一个面向开发者的本地 AI 应用样板，提供 Web 体验入口，集成多 Agent 调度、RAG 知识库与流式对话三大模块，可作为企业内部知识助手、智能客服等场景的起步框架。

### 2.2 运行环境

| 类别 | 要求 |
|------|------|
| 操作系统 | Windows / macOS / Linux |
| JDK | Java 17 及以上 |
| 构建工具 | Maven 3.6+ |
| 大模型服务 | Ollama（本地部署，地址 http://localhost:11434） |
| 浏览器 | Chrome / Edge / Firefox 现代版本 |

### 2.3 技术栈

| 层次 | 技术选型 |
|------|----------|
| 基础框架 | Spring Boot 3.4.5 |
| AI 框架 | Spring AI 1.0.0 GA |
| 大模型 | Ollama + qwen2.5:7b-instruct-q6_K（对话） |
| 向量模型 | Ollama + nomic-embed-text（Embedding） |
| 向量库 | SimpleVectorStore（内存型，1.0.0 GA） |
| 文档解析 | Apache Tika（spring-ai-tika-document-reader） |
| Web | Spring Web + 静态 HTML/CSS/JS |
| 日志 | SLF4J + Logback |
| 工具库 | Lombok |

### 2.4 用户特征

| 角色 | 描述 |
|------|------|
| 最终用户 | 通过浏览器访问 Web 入口，使用自然语言与系统交互 |
| 开发者 | 基于本项目扩展新 Agent、接入真实业务 API、替换向量库等 |

---

## 3. 功能需求

### 3.1 模块划分

```
Spring AI 智能助手
├── FR1  多 Agent 协作模块
│   ├── FR1.1 智能路由
│   ├── FR1.2 天气 Agent
│   ├── FR1.3 研究 Agent
│   ├── FR1.4 写作 Agent
│   └── FR1.5 指定 Agent 调用
├── FR2  RAG 知识库模块
│   ├── FR2.1 文档入库
│   └── FR2.2 知识库问答
├── FR3  流式对话模块
│   └── FR3.1 SSE 实时输出
└── FR4  Web 体验入口
    └── FR4.1 统一交互页面
```

### 3.2 FR1 多 Agent 协作模块

#### FR1.1 智能路由

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.1 |
| 优先级 | 高 |
| 描述 | 系统根据用户输入的关键词，自动选择最合适的 Agent 处理任务。 |
| 输入 | 用户自然语言任务（字符串） |
| 路由规则 | 含"天气/气温/温度" → weather-agent；含"分析/研究/为什么/原理/背景" → research-agent；含"写/撰写/创作/文章/报告" → writing-agent；默认 → research-agent |
| 输出 | 选中 Agent 的处理结果（JSON，含 content 字段） |
| 接口 | `POST /api/agent/process`，请求体 `{"task": "..."}` |

#### FR1.2 天气 Agent（weather-agent）

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.2 |
| 优先级 | 中 |
| 描述 | 从用户输入提取城市，查询天气信息，并由 LLM 生成友好回复。 |
| 支持城市 | 北京、上海、广州、深圳、杭州、成都、重庆、武汉 |
| 数据来源 | 模拟数据（WeatherService，后续可替换为真实天气 API） |
| 输出 | LLM 润色后的天气描述 |
| 异常处理 | 未识别到城市时，提示用户指定城市 |

#### FR1.3 研究 Agent（research-agent）

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.3 |
| 优先级 | 高 |
| 描述 | 对复杂问题进行深度分析，提供背景、要点与建议。 |
| 实现方式 | 调用 ChatClient，system prompt 约束为"专业研究员"角色 |
| 输出 | 结构化分析文本（纯文本，非 JSON） |

#### FR1.4 写作 Agent（writing-agent）

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.4 |
| 优先级 | 中 |
| 描述 | 根据用户要求撰写文案、文章、报告等内容。 |
| 实现方式 | 调用 ChatClient，system prompt 约束为"专业文案写手"角色 |
| 输出 | 创作内容文本 |

#### FR1.5 指定 Agent 调用

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.5 |
| 优先级 | 中 |
| 描述 | 允许用户绕过智能路由，直接指定某个 Agent 处理任务。 |
| 接口 | `POST /api/agent/execute/{agentName}`，请求体 `{"task": "..."}` |
| 异常处理 | agentName 不存在时返回提示 JSON |

#### FR1.6 Agent 列表查询

| 项 | 内容 |
|----|------|
| 需求 ID | FR1.6 |
| 优先级 | 低 |
| 描述 | 返回当前已注册的所有 Agent 名称与描述，供前端动态渲染。 |
| 接口 | `GET /api/agent/agents` |
| 输出 | `{"weather-agent":"...","research-agent":"...","writing-agent":"..."}` |

### 3.3 FR2 RAG 知识库模块

#### FR2.1 文档入库

| 项 | 内容 |
|----|------|
| 需求 ID | FR2.1 |
| 优先级 | 高 |
| 描述 | 读取 `src/main/resources/materials/` 目录下的文档，分块向量化后存入向量库。 |
| 支持格式 | .txt / .md / .pdf / .doc / .docx（基于 Apache Tika） |
| 处理流程 | 扫描目录 → 跳过 README* → Tika 解析 → TokenTextSplitter 分块 → Embedding 向量化 → 存入 SimpleVectorStore |
| 接口 | `POST /rag/ingest` |
| 输出 | `{"status":"ok","chunksIngested":N}` |
| 约束 | 内存向量库重启后数据丢失，需重新入库 |

#### FR2.2 知识库问答

| 项 | 内容 |
|----|------|
| 需求 ID | FR2.2 |
| 优先级 | 高 |
| 描述 | 基于已入库的私有文档回答用户问题。 |
| 实现方式 | ChatClient + QuestionAnswerAdvisor（topK=4），先检索相似文档块，再交由 LLM 生成回答 |
| 接口 | `GET /rag/query?question=xxx` |
| 输出 | `{"question":"...","answer":"..."}` |

### 3.4 FR3 流式对话模块

#### FR3.1 SSE 实时输出

| 项 | 内容 |
|----|------|
| 需求 ID | FR3.1 |
| 优先级 | 中 |
| 描述 | 用户提问后，模型逐字实时返回，无需等待完整回复。 |
| 接口 | `GET /chat/stream?msg=xxx`，Content-Type: text/event-stream |
| 实现 | ChatClient.stream() 返回 Flux<String> |
| 前端处理 | 通过 ReadableStream 逐块解析 `data:` 前缀并追加显示 |

### 3.5 FR4 Web 体验入口

#### FR4.1 统一交互页面

| 项 | 内容 |
|----|------|
| 需求 ID | FR4.1 |
| 优先级 | 高 |
| 描述 | 提供单页 Web 入口，集成三大模块的体验。 |
| 访问地址 | http://localhost:8080/ |
| 页面结构 | 顶部标题 + 3 个 Tab（Agent 对话 / RAG 知识库 / 流式对话） |
| Agent 对话 Tab | 动态加载 Agent 下拉、示例按钮、对话气泡、响应时间统计 |
| RAG 知识库 Tab | 一键入库按钮、问题输入、结果展示区 |
| 流式对话 Tab | 终端风格输出区、示例按钮 |
| 交互特性 | Ctrl+Enter 发送、打字动画、状态提示、响应时间统计 |

---

## 4. 接口清单

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 智能路由 | POST | /api/agent/process | 自动选择 Agent 处理任务 |
| 指定 Agent | POST | /api/agent/execute/{agentName} | 指定 Agent 处理任务 |
| Agent 列表 | GET | /api/agent/agents | 获取所有已注册 Agent |
| 普通对话 | GET | /chat?msg=xxx | 单轮同步对话 |
| 多轮对话 | POST | /chat/context | 带 system prompt 对话 |
| 流式对话 | GET | /chat/stream?msg=xxx | SSE 流式输出 |
| RAG 入库 | POST | /rag/ingest | 文档向量化入库 |
| RAG 查询 | GET | /rag/query?question=xxx | 知识库问答 |

---

## 5. 非功能需求

### 5.1 性能需求

| 指标 | 要求 |
|------|------|
| 普通对话响应 | 单次同步请求 < 5s（取决于本地模型） |
| 流式首字延迟 | < 1s |
| RAG 入库 | 100 个文档块 < 30s |
| 并发 | 支持单机 10 并发会话 |

### 5.2 可靠性需求

- 大模型服务不可用时，返回友好错误提示，不导致服务崩溃。
- RAG 入库失败时，明确返回失败原因（如向量模型未拉取）。
- Agent 路由未命中时，默认走 research-agent，保证有响应。

### 5.3 可维护性需求

- Agent 采用接口（IAgent）+ 注解（@Component）模式，新增 Agent 零侵入，自动注入 AgentOrchestrator。
- 配置集中在 application.yml，模型、端口、日志级别可外部化调整。
- 向量库可替换：当前为 SimpleVectorStore，可平滑切换至 PgVector 等。

### 5.4 安全性需求

- 本项目为本地演示，不对外暴露；生产部署需增加鉴权（如 Spring Security）。
- application.yml 中不应硬编码敏感密钥；如接入 OpenAI，API Key 通过环境变量注入。
- RAG materials 目录需校验文件类型，防止恶意文件解析。

### 5.5 可扩展性需求

- 新增 Agent：实现 IAgent 接口 + @Component 即可。
- 新增向量库：替换 RagConfig 的 VectorStore Bean。
- 新增文档源：扩展 RagService 的资源加载方式（如 file: 外部目录）。

---

## 6. 数据模型

### 6.1 请求/响应模型

**ProcessRequest**（Agent 任务请求）
```
{ "task": "string" }
```

**AgentResponse**（Agent 统一响应）
```
{ "content": "string", "type": "text" }
```

**WeatherResponse**（天气详情）
```
{ "city": "string", "temperature": "string", "condition": "string", "humidity": "string" }
```

**ChatRequest**（多轮对话请求）
```
{ "message": "string" }
```

### 6.2 向量库数据

SimpleVectorStore 内存存储，每个文档块包含：
- id：唯一标识
- content：文本内容
- embedding：768 维向量（nomic-embed-text）
- metadata：来源文件名等

---

## 7. 部署与运行

### 7.1 前置准备

1. 安装 Ollama 并启动服务（默认 http://localhost:11434）。
2. 拉取模型：
   ```bash
   ollama pull qwen2.5:7b-instruct-q6_K   # 对话模型
   ollama pull nomic-embed-text            # 向量模型（RAG 必需）
   ```

### 7.2 启动应用

```bash
mvn spring-boot:run
```

访问 http://localhost:8080/ 进入体验页面。

### 7.3 RAG 使用流程

1. 将文档放入 `src/main/resources/materials/`。
2. 启动应用，点击「📥 一键入库」或调用 `POST /rag/ingest`。
3. 在 RAG Tab 提问，或调用 `GET /rag/query?question=xxx`。

---

## 8. 约束与假设

### 8.1 约束

- 必须使用 Java 17+。
- Spring Boot 与 Spring AI 版本必须匹配（3.4.5 ↔ 1.0.0 GA）。
- Spring AI 1.0.0 GA 中 starter 已重命名为 `spring-ai-starter-model-ollama`，类 `SimpleInMemoryVectorStore` 已重命名为 `SimpleVectorStore`。

### 8.2 假设

- 天气数据为模拟数据，后续可替换为真实天气 API。
- 向量库为内存型，重启丢失；生产环境应替换为持久化向量库。
- 单机部署，未做集群与负载均衡。

---

## 9. 待办与演进

| 编号 | 事项 | 优先级 |
|------|------|--------|
| TODO-1 | 接入真实天气 API（替换 WeatherService 模拟数据） | 中 |
| TODO-2 | 切换持久化向量库（PgVector / Redis Stack） | 中 |
| TODO-3 | 增加用户鉴权（Spring Security + JWT） | 高 |
| TODO-4 | 新增更多 Agent（计算器、翻译、代码生成） | 低 |
| TODO-5 | 支持外部 materials 目录（file: 资源） | 低 |
| TODO-6 | 增加对话历史记忆（ChatMemory） | 中 |
| TODO-7 | 接入可观测性（Micrometer + Actuator） | 低 |

---

## 10. 验收标准

| 需求 ID | 验收标准 |
|---------|----------|
| FR1.1 | 输入"北京天气"返回 weather-agent 处理结果；输入"分析AI"返回 research-agent 结果 |
| FR1.2 | 输入"北京天气"返回包含天气信息的友好回复 |
| FR1.3 | 输入"分析AI趋势"返回结构化分析文本 |
| FR1.4 | 输入"写一篇散文"返回创作内容 |
| FR1.5 | 指定 agentName 调用对应 Agent；错误 agentName 返回提示 |
| FR1.6 | GET /api/agent/agents 返回 3 个 Agent 信息 |
| FR2.1 | materials 放入文档后调用入库接口，返回 chunksIngested > 0 |
| FR2.2 | 入库后提问，返回基于文档内容的回答 |
| FR3.1 | 流式接口逐字返回，前端实时显示 |
| FR4.1 | 浏览器访问 8080，3 个 Tab 功能正常 |

---

*文档结束*