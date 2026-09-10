# OmniStudy Kubernetes 部署

这套清单将 Spring Boot 拆成 API 和 AI Worker 两类 Deployment。API 与 Worker 默认各两个副本，配置滚动更新、健康探针、资源限制、HPA 和 PodDisruptionBudget。

数据库和 Redis 应使用云厂商高可用服务或独立集群，不把单副本数据库塞进应用命名空间。AI Worker 依赖 PostgreSQL 的 `FOR UPDATE SKIP LOCKED` 抢占任务，因此可以安全运行多个副本。

## 部署前准备

1. 将 `ghcr.io/your-org/omnistudy-backend:0.1.0` 替换为实际镜像地址。
2. 根据 `secret.example.yaml` 创建真实 Secret，不要提交真实密钥。
3. 将 Ingress 域名和 TLS Secret 替换为实际配置。
4. 集群需要 metrics-server；Ingress 需要 ingress-nginx 或兼容控制器。

```bash
kubectl apply -f deploy/k8s/base/namespace.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -k deploy/k8s/base
kubectl -n omnistudy rollout status deployment/omnistudy-api
kubectl -n omnistudy rollout status deployment/omnistudy-worker
kubectl -n omnistudy get pods,hpa,pdb,ingress
```

管理端口 `9091` 只通过 ClusterIP Service 暴露给集群内 Prometheus，不要添加到 Ingress。正式环境建议用 External Secrets 或云厂商 Secret Manager 替代手工 Secret 文件。

## Docker Desktop 本地验证

本地 Overlay 使用 Docker Desktop 镜像存储中的 `omnistudy-backend:local`，删除公网 Ingress，并通过 `host.docker.internal` 访问 Compose 中的 PostgreSQL（5433）和 Redis（6379）。Secret 由部署命令直接写入集群，不落盘。

```bash
./scripts/k8s-local-up
```

本地 Overlay 使用 Docker Desktop 的 LoadBalancer 支持，入口为 `http://localhost:8083`；API 和 Worker 管理指标分别为 9191、9192，仅供本机 Prometheus 抓取。Docker Compose API 仍使用 8082，开发模式 Spring Boot 仍使用 8080，三者互不冲突。

HPA 需要 metrics-server。Docker Desktop kind 的本地 kubelet 证书没有 IP SAN，因此仅在这个本地测试集群为 metrics-server 添加 `--kubelet-insecure-tls`；正式集群不应使用这个参数。

## 生产环境发布

生产部署由三部分组成：GitHub Actions 构建不可变后端镜像、云托管 PostgreSQL/Redis 保存状态、Kubernetes 运行无状态 API 与异步 Worker。API 和 Worker 各两个副本，并尽量分散到不同节点；单个 Pod 退出时，Service 会把流量转发给健康副本。

1. 在 GitHub 仓库的 Actions 页面手动运行 `publish-backend-image`，或推送 `v*` 标签。工作流会发布 `ghcr.io/<owner>/omnistudy-backend:<git-sha>`；生产环境优先使用不可变的 Git SHA 标签。
2. 创建云 PostgreSQL 数据库并启用自动备份/PITR，创建云 Redis 并启用高可用。安全组只允许 Kubernetes 节点或私网访问。数据库账号必须具有执行现有 Flyway 迁移及创建 `vector` 扩展所需的权限。
3. 使用至少两个工作节点的集群，安装 Ingress Controller、metrics-server 和证书控制器，并在 `omnistudy` 命名空间为 API 域名准备 TLS Secret。单节点集群只能验证功能，无法抵抗节点故障。
4. 复制环境模板到仓库外，填入真实值后执行部署：

```bash
cp deploy/k8s/production.env.example ../omnistudy-production.env
set -a
source ../omnistudy-production.env
set +a
./scripts/k8s-production-deploy
```

脚本会校验当前 `kubectl` context，避免误部署到其他集群；Secret 直接发送给 Kubernetes，不生成明文 YAML 文件。生产环境建议进一步接入 External Secrets/云 Secret Manager。`JWT_SECRET` 与 `AI_CREDENTIAL_ENCRYPTION_KEY` 必须长期稳定保存：后者一旦丢失或更换，数据库中用户自行配置的加密 API Key 将无法解密。

若 GHCR Package 是私有的，需要先在 `omnistudy` 命名空间创建拉取凭证 Secret，并把名称填入可选的 `IMAGE_PULL_SECRET`；部署脚本会把它绑定到 ServiceAccount。开源项目也可以把镜像 Package 设为 Public，从而无需分发拉取凭证。

扩展发布后，把 `CORS_ALLOWED_ORIGINS` 设置为准确的 `chrome-extension://<扩展ID>`，不要在公网继续使用通配符。然后把扩展的后端地址构建为 `https://<API域名>` 并重新提交扩展商店。

当前视频截图只在一次模型请求期间以内存/Base64 形式传递，没有保存到数据库或对象存储，因此第一版上线不需要 OSS/S3。用户、问题、笔记、Agent 会话、AI 任务和加密后的个人 API Key 保存在 PostgreSQL；任务协调和短期缓存使用 Redis。

部署完成后至少检查：

```bash
curl -fsS "https://${OMNISTUDY_API_HOST}/health"
kubectl -n omnistudy get pods,hpa,pdb,ingress
kubectl -n omnistudy logs deployment/omnistudy-api --tail=100
kubectl -n omnistudy logs deployment/omnistudy-worker --tail=100
```
