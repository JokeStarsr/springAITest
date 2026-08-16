# springAITest

Spring Boot 3 + Spring AI 1.0 的**多 Agent 协作 + RAG + 文件生成**演示项目，模型走 DeepSeek（OpenAI 兼容协议），embedding 走智谱，向量库用 PostgreSQL + pgvector（HNSW + COSINE）。

## 架构

```
用户输入
  │
  ▼
┌────────────────────┐   快速路由(关键词/零成本)     ┌──────────────┐
│  CoordinatorAgent  │ ──────────────────────────▶ │ WeatherAgent │
│  (任务拆解/分派/汇总) │ ─ ─ ─ ─ LLM 计划 ─ ─ ─ ─ ▶ │ ResearchAgent │
└────────────────────┘                              │ WritingAgent  │
   │                                                └──────────────┘
   └──▶ ChatClient(@Tool Tool Calling)               各 Agent 通过
        └──▶ 技能: web_search / weather_query       AgentContext 共享
             calculator / statistics / text_analysis  数据与消息
```

- **无状态 Agent 单例**：会话上下文以参数传递（`execute(input, context)`），并发请求互不串台
- **会话管理**：`ConversationManager` 按 sessionId 存储 AgentContext（黑板）+ 多轮历史（最近 8 轮自动注入），容量上限 50 自动驱逐
- **双模型路由**：协调/汇总/计划等结构任务可走 fast 模型（`app.fast-model-enabled=true`），正文任务走主模型
- **精确用量**：基于 Spring AI `TokenUsage` 记录真实 token（无 usage 时回退字符估算），`/api/usage/stats` 可查

## 功能模块

| 模块 | 端点 | 说明 |
|---|---|---|
| 多 Agent | `POST /api/agent/process` `POST /api/agent/execute/{agentName}` | 智能路由 / 指定 Agent（带会话历史） |
| 快聊 | `GET /chat` `POST /chat/context` `GET /chat/stream` | 单轮 / 真多轮(带 sessionId) / SSE 流式 |
| RAG | `POST /rag/ingest` `GET /rag/query` | 默认知识库（id=1）问答 |
| 知识库 | `/api/knowledge-bases/*` | 多库 CRUD、文件上传入库、逐库查询 |
| 文件生成 | `POST /api/files/generate` `GET /api/files/types` | AI 生成 PPT/Word/Excel 并可下载 |
| 用量 | `/api/usage/*` | token 与费用统计 |
| 管理 | `/api/admin/*`（需鉴权） | 健康检查 / 触发部署 / 定时拉取部署 |

## 快速启动（本地，无数据库）

```bash
# 1. 配置环境变量
export DEEPSEEK_API_KEY=sk-xxx        # 主模型 (Windows: $env:DEEPSEEK_API_KEY="sk-xxx")
export ZHIPU_API_KEY=xxx              # embedding
export ADMIN_TOKEN=xxx                # 管理端点 token（可选）

# 2. 启动（local profile 禁用数据库/RAG，用本地输出目录）
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. 打开体验中心
open http://localhost:8080
```

完整模式（RAG + pgvector）：
```bash
docker compose up -d    # 启动 pgvector (端口 ${DB_PORT:-5432})
mvn spring-boot:run
```

## 关键配置（application.yml，均可环境变量覆盖）

| 配置 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `spring.ai.openai.api-key` | `DEEPSEEK_API_KEY` | 无 | DeepSeek/OpenAI 兼容 key |
| `spring.ai.openai.chat.options.model` | 见配置 | `deepseek-v4-flash` | 主模型 |
| `app.admin-token` | `ADMIN_TOKEN` | 空（仅本机） | 管理端点鉴权 |
| `app.rate-limit.per-minute` | `APP_RATE_LIMIT_PER_MINUTE` | 30 | AI 接口每 IP 限流 |
| `app.max-upload-mb` | `APP_MAX_UPLOAD_MB` | 20 | 知识库单文件上限 |
| `app.gen-files-retention-days` | — | 7 | 生成文件保留天数（每日 03:30 自动清理） |
| `app.fast-model-enabled` | `APP_FAST_MODEL_ENABLED` | false | 协调任务走快模型路由 |
| `app.fast-model` | `APP_FAST_MODEL` | `deepseek-v4-flash` | 快模型名 |
| 天气/搜索 | `WEATHER_API_KEY` `SEARCH_API_KEY` | 空 | OpenWeatherMap / Brave |

## 安全规范（重要）

- **禁止**把任何 key/密码/凭据提交到仓库——`application-local.yml`、`ssh_exec.sh` 已在 `.gitignore`，不要删除该忽略规则
- 服务器 root 密码曾因 `ssh_exec.sh` 入库而泄露（2026-08-17 已轮换）；历史提交中的旧密码必须在服务端轮换使其失效
- 管理端点 `/api/admin/**` 生产环境必须设置 `ADMIN_TOKEN`，否则仅限本机访问

## 测试

```bash
mvn test                          # 离线单测（路由纯函数、用量统计），不调用任何外部 API
mvn test -Dgroups=integration     # 集成测试（真实 DeepSeek 调用，会消耗额度，需先配 key）
```

## 部署（服务器）

服务器 115.159.221.62 已配置 `systemd` 服务 + `AdminController` 自动部署：
- 手动：`POST /api/admin/deploy`（携带 `X-Admin-Token`）
- 自动：每 5 分钟 `git fetch origin` 检查新提交 → 后台执行 `deploy.sh`（git pull → mvn package → systemctl restart）
- 健康检查：`GET /api/admin/health`

## 文档

- [优化方案（2026-08-17，含安全/并发重构/成本优化依据）](docs/OPTIMIZATION-PLAN.md)
- [需求规格说明书](docs/SRS-需求规格说明书.md)（早期版本，架构演进后部分内容已过时，以代码为准）