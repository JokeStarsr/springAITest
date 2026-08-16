# springAITest 优化方案（2026-08-17）

> 基于对全部源码的逐文件审查。分 P0（安全/正确性，必须修）、P1（工程质量）、P2（成本与体验）。实施完成率以 git 提交为准。

## 背景发现：两个已处置的安全事件

1. **服务器 root 密码已泄露**：`ssh_exec.sh`（含 `115.159.221.62` root 密码）在 `f795f88` 被推送到 GitHub 公开仓库历史。处置：移出版本控制 + `.gitignore` 添加（commit 478712b），密码已于 2026-08-17 轮换。历史中的旧密码无法从 GitHub 彻底抹除，已通过轮换使旧密码失效。
2. `application-local.yml` 曾含明文 API key，gitignore 已覆盖，本次进一步改为环境变量引用（本地无 key 时自动跳过密钥字段，仅本地开发用，不入库）。

## P0 安全修复

| # | 问题 | 修复 |
|---|------|------|
| S1 | `/api/admin/*`（deploy/health）**无任何鉴权**，公网任何人可触发服务器部署脚本 | 加 `X-Admin-Token` 请求头校验（token 从环境变量 `ADMIN_TOKEN` 读，未设置时仅本机可用），并下沉到 `AdminTokenFilter` 统一拦截 `/api/admin/**` |
| S2 | 知识库名未过滤 → `dataRoot + "/" + name` **路径穿越**（name=`../..` 可读写任意目录、delete 可删任意目录） | name 白名单校验（`[\w一-龥-]{1,50}`），磁盘目录改用 `kb-{id}` 与 name 解耦 |
| S3 | 文件上传**无大小/类型限制**，Tika 解压可放大（zip/PPT 炸弹） | `app.max-upload-mb`（默认 20MB）+ multipart 限制 + 扩展名白名单 |
| S4 | `application.yml`/docker-compose 数据库**默认弱口令**，5432 端口映射宿主 | 默认值改为从环境变量强制注入，docker-compose 改为生成随机密码（首次启动时） |
| S5 | `chat`/`chat/context` 无参数校验与限流 | 参数校验 + 简单每 IP 令牌桶（可配置，默认 30 次/分钟），防 API 费用被刷 |

## P0 并发正确性（核心重构）

| # | 问题 | 修复 |
|---|------|------|
| C1 | `CoordinatorAgent` 是单例 Bean，`AgentContext` 用 `setContext()` 塞字段——**并发请求会话串台**（会话 A 的上下文会被 B 覆盖），多会话同时调用必然互相污染 | Context 改为**方法参数传递**：`BaseAgent.execute(userInput, context)`，Agent 恢复无状态；Coordinator 构造临时调用链 |
| C2 | `AgentController.sessions` 用非线程安全 `LinkedHashMap` | 换 `ConcurrentHashMap` + 独立容量控制（保留 50 上限语义） |
| C3 | "会话"形同虚设——多轮对话每轮都是独立 LLM 调用，**无历史** | 会话级消息历史（球内存 List，上限 20 条/会话），`BaseAgent` 注入历史 |
| C4 | `AdminController.deploy()` 的 `deploying` 标志在启动脚本后立即释放（nohup 异步），并发可重复部署 | 改用后台线程持有锁直到部署结束，并记录部署状态供查询 |

## P1 工程质量

| # | 问题 | 修复 |
|---|------|------|
| E1 | 无全局异常处理器：业务异常返回 500 + 堆栈，前端难读 | `@RestControllerAdvice`：业务异常 → 400/JSON `{code,message}`；未知异常 → 500 统一格式 |
| E2 | 双通道技能机制：旧 `Skill.execute(Map)`/`ToolResult`/`parametersSchema` 是上一代 ReAct 循环死代码，与 `@Tool` 新通道并存但从未被调用 | 只留 `@Tool` 单通道，`Skill` 接口降为空标记，删除 `ToolResult` |
| E3 | `WeatherService`（模拟天气）/`WeatherRequest`/`WeatherResponse` 死代码（无任何引用） | 删除 |
| E4 | `/chat/context` 注释自诩"带上下文"实为裸调用 | 真·会话多轮（复用 C3 的历史机制） |
| E5 | 路径硬编码 `/opt/springaitest/...` 散落 FileGen/Admin | 统一 `app.data-dir`/`app.generated-files-dir` 配置化，本地默认 `./data` |
| E6 | 生成文件**无清理策略**，磁盘持续增长 | 每日清理 >7 天的生成文件（可配置 `app.gen-files-retention-days`） |
| E7 | `UsageTracker` 用字符粗估 token | 改用 Spring AI `ChatResponse` 的 `TokenUsage` 精确计量（DeepSeek 返回真实 usage），字符估算仅作回退 |
| E8 | 死代码/混乱：`AgentResponse` 未使用、`RagService` 与 `KnowledgeBaseService` 重复、字段注入与构造器注入混用、pom 残留注释 | 删除未用类；RAG 统一走 `KnowledgeBaseService`（RagController 作为默认库别名）；全部改构造器注入 |
| E9 | 索引/测试不强：`AgentTest` 依赖真实 API 与网络（会真实调用花钱） | 拆出离线单测（token 估算、路由、技能解析），AI 集成测试加 `@Tag("integration")` 并默认排除 |
| E10 | README 一行；docs 与代码脱节 | 重写 README（架构、快速启动、配置、API、部署、安全规范） |

## P2 成本与体验

| # | 方案 | 说明 |
|---|------|------|
| Q1 | **快慢模型路由**：协调/汇总/文件骨架等结构任务走 fastModel，写作/研究正文走主模型 | 结构任务 token 量大但对质量不敏感，估算可省 50%+ 输出成本；`app.fast-model` 可配置 |
| Q2 | 前端错误展示规范化：统一 `{code,message}` 解析、RAG 查询 loading 态 | 配合 E1 |

## 实施顺序

1. P0 安全（S1–S4）
2. P0 并发（C1–C4）
3. P1（E1–E10，其中 E2/E3/E5/E8 与 C1/C3 交互，一并改）
4. P2（Q1–Q2）
5. `mvn compile` + 离线单测通过 → 提交推送 → 服务器部署验证

## 验证方式

- 每次提交前 `mvn -q compile` 通过
- 安全项用单元/手动 HTTP 验证（路径穿越用例、越权访问返回 401）
- 并发正确性用两个并发 curl 会话验证互不污染
- 线上部署走 `deploy.sh`（先本地验证后重启）