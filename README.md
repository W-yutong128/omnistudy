# OmniStudy

苏格拉底式网课学习伴侣

## 技术栈与亮点

- **后端**：Spring Boot 3 + Spring Security/JWT + JPA + PostgreSQL + Redis + Flyway
- **扩展**：Chrome Extension Manifest V3 + React + TypeScript + Vite
- **Agent Harness**：AgentScope Java 2.0.1 + PostgreSQL AgentStateStore + 上下文压缩，并保留自研单步 Harness 作为降级路径
- **RAG**：笔记/字幕分块、PostgreSQL FTS + 384 维稠密召回、RRF 混合排序；pgvector 可用时自动创建 HNSW 列
- **Skills**：当前课程辅导、复习教练、编程课程教练三套版本化行为约束
- **质量工程**：离线路由 Evals、JUnit、真实 MV3 Playwright E2E、GitHub Actions
- **安全 handoff**：写代码场景需用户确认后，才通过 allowlist Native Messaging 桥接 IDEA
- **Token 管理**：12k 上下文预算、最多 3 轮工具调用、20 条消息触发压缩，并持久化输入/输出/缓存 Token
- **严格 BYOK**：注册、JWT/Refresh Token、USER/ADMIN 权限、用户级 DashScope Key 与 AES-GCM 加密存储；服务器不提供共享模型 Key
- **流量治理**：Redis Lua 分布式限流、JWT 权限短缓存、Redis 故障时实例内安全降级
- **可观测性**：Prometheus 指标采集 + 自动 Provisioning 的 Grafana 系统总览面板
- **服务拆分**：同一后端镜像按配置运行 API 或持久化 AI Worker，支持独立扩容
- **Local-first 隐私**：默认在用户电脑运行，严格 BYOK，不包含遥测或作者共享模型 Key

## 快速启动

### 普通用户：本地一键运行

默认是 Local-first + BYOK 模式。只需安装并启动 Docker Desktop，然后在项目根目录执行：

```bash
./scripts/local-up
```

首次启动会生成随机本地密钥、构建容器、启动 PostgreSQL/Redis/API/AI Worker，并由 Flyway 自动初始化数据库。需要同时启动监控：

```bash
./scripts/local-up --monitoring
```

从 GitHub Releases 下载 `omnistudy-extension-v*.zip`，解压后在 `chrome://extensions/` 开启开发者模式并“加载已解压的扩展程序”。默认后端地址为 `http://localhost:8082`。

完整步骤、停止和升级方式见 [本地安装指南](docs/local-installation.md)。

### 数据导出、导入和备份

```bash
./scripts/local-backup
./scripts/local-restore /path/to/omnistudy-backup.tar.gz --yes
```

备份同时保存数据库和 API Key 加密密钥，属于敏感文件。详见 [备份与恢复](docs/local-backup.md)。

### 开发者源码运行

开发环境需要 JDK 21、Maven 3.9+、Node.js 20+ 和 pnpm 9+：

```bash
./scripts/dev-up
```

生成可发布的本地 Chrome ZIP：

```bash
./scripts/package-extension
```

生产/私有服务器构建时通过环境变量写入 HTTPS 后端地址：

```bash
VITE_API_BASE_URL=https://api.example.com ./scripts/package-extension
```

完整 Docker Compose 拓扑默认地址：

- API：`http://localhost:8082`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`

Grafana 用户名默认为 `admin`，密码由首次启动随机生成到 `.env.local`。启动后进入 `OmniStudy / OmniStudy 系统总览` 即可查看 API、JVM、限流和 AI Worker 指标。

### 一键验证

```bash
./scripts/verify
```

IDEA 桥接的可选安装方式见 [docs/idea-bridge.md](docs/idea-bridge.md)。它不会被 Agent 自动执行。
AgentScope 的运行边界和降级策略见 [docs/agentscope.md](docs/agentscope.md)。
管理员初始化、BYOK 和安全用量限制见 [docs/users-and-billing.md](docs/users-and-billing.md)。

AI 笔记的持久化任务、重试、去重与多实例领取机制见 [docs/ai-job-queue.md](docs/ai-job-queue.md)。
Redis 分布式限流、权限缓存和降级边界见 [docs/redis-rate-limiting.md](docs/redis-rate-limiting.md)。
Prometheus、Grafana、API/Worker 容器拓扑见 [docs/observability.md](docs/observability.md)。
Kubernetes 双副本、滚动更新、HPA、PDB 与 Ingress 部署清单见 [deploy/k8s/README.md](deploy/k8s/README.md)。
自动化演示视频与手工答辩录制脚本见 [docs/demo-video.md](docs/demo-video.md)。

生产后端使用 `SPRING_PROFILES_ACTIVE=prod` 启动；该 Profile 会关闭测试用户、把 Access Token 默认缩短为 30 分钟，并要求显式提供数据库、JWT、凭证加密密钥和 CORS 来源。服务器不读取模型 Key，每位用户必须在插件“模型”页配置自己的 Key。Kubernetes 探针可分别访问 `/actuator/health/liveness` 与 `/actuator/health/readiness`。

### 打开 B 站视频

访问任意 B 站视频页面，点击扩展图标打开侧边栏。

## 示例题库与外部数据

Flyway V9 会创建独立的 `question_bank`，启动后端时会自动导入 4 道 OmniStudy 原创示例题。检索不会返回正确答案和解析，题目投放给用户后才进入现有的 `questions -> attempts -> knowledge_points` 复习闭环。

- `GET /api/question-bank?query=TCP&subject=计算机网络&limit=20`：按全文、标签和科目检索，排除用户已经作答的题。
- `POST /api/question-bank/{id}/deliver`：将题库题目投放为当前用户的复习题。

项目保留 PDF 导入工具用于用户处理自己拥有授权的数据：

```bash
python packages/backend/scripts/import_2009_408_pdf.py /path/to/authorized.pdf /path/to/output.json
```

不要提交授权状态不明确的真题、书籍、字幕、图片或解析。公开数据必须附带可验证的再分发许可。

## 安全、隐私与贡献

- [隐私说明](PRIVACY.md)
- [安全报告流程](SECURITY.md)
- [贡献指南](CONTRIBUTING.md)
- [公开发布检查清单](docs/release-checklist.md)

发布前运行 `./scripts/audit-release`。本项目采用 [Apache License 2.0](LICENSE)，使用、修改和分发时须遵守许可证条款。

## 项目结构

```
omnistudy/
├── packages/
│   ├── shared/          # TS 类型定义（两端共用）
│   ├── backend/        # Spring Boot 后端
│   └── extension/       # Chrome 扩展
├── pnpm-workspace.yaml
└── package.json
```
