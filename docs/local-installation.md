# OmniStudy 本地安装

OmniStudy 默认采用 Local-first + BYOK 模式：后端、数据库和队列都运行在用户电脑上，用户在扩展中配置自己的 DashScope API Key。项目不会提供或消耗作者的共享 Key。

## 普通用户安装

只需要准备：

- Docker Desktop（保持 Docker Engine 为 Running）
- Chrome 或 Chromium 浏览器
- macOS/Linux 终端；Windows 推荐使用 WSL2 执行同样的命令

下载源码后，在项目根目录运行：

```bash
./scripts/local-up
```

首次执行会自动完成：

1. 生成 `.env.local`，其中包含随机数据库密码、Redis 密码、JWT 密钥和 API Key 加密密钥；
2. 构建 Spring Boot 后端镜像；
3. 启动 PostgreSQL、Redis、API 和 AI Worker；
4. 由 Flyway 按顺序创建或升级数据库，不需要执行 SQL；
5. 等待 `/health` 真正可用后再提示启动成功。

需要 Prometheus 和 Grafana 时执行：

```bash
./scripts/local-up --monitoring
```

默认地址：

- API：`http://localhost:8082`
- 健康检查：`http://localhost:8082/health`
- Prometheus（可选）：`http://localhost:9090`
- Grafana（可选）：`http://localhost:3000`

Grafana 的随机初始密码会在启动成功后显示，也可在本机 `.env.local` 中查看。不要把该文件发送给别人或提交到 Git。

## 安装 Chrome 扩展

从 GitHub Releases 下载 `omnistudy-extension-v*.zip`，解压到一个固定目录。然后：

1. 打开 `chrome://extensions/`；
2. 开启右上角“开发者模式”；
3. 点击“加载已解压的扩展程序”；
4. 选择刚解压的目录；
5. 打开任意 B 站视频，点击 OmniStudy 图标打开侧边栏；
6. 注册本地账号，在“模型”页保存并测试自己的 DashScope API Key。

Chrome 开发者模式不能直接加载 ZIP，因此需要先解压。正式上架 Chrome Web Store 后才能获得点击安装体验。

开发者也可以从源码生成 ZIP：

```bash
./scripts/package-extension
```

产物位于 `artifacts/`，默认连接 `http://localhost:8082`。如需连接私有服务器：

```bash
VITE_API_BASE_URL=https://api.example.com ./scripts/package-extension
```

## 停止、再次启动和更新

停止服务但保留全部数据：

```bash
./scripts/local-down
```

以后再次执行 `./scripts/local-up` 即可继续使用。升级代码后再次运行相同命令，Flyway 会自动执行尚未应用的新迁移。

不要使用 `docker compose down -v`，`-v` 会删除 PostgreSQL、Redis、Prometheus 和 Grafana 数据卷。

## 数据保存在哪里

- PostgreSQL Docker Volume：用户、课程、问题、笔记、Agent 会话和 AI 任务；
- Redis Docker Volume：队列协调和临时缓存；
- `.env.local`：数据库密码、JWT 密钥和用户 API Key 的本地加密密钥；
- 浏览器扩展存储：登录状态和少量 UI 配置。

完整备份和迁移方法见 [local-backup.md](local-backup.md)。
