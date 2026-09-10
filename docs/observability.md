# Prometheus、Grafana 与 API/Worker 拆分

## 本地生产化拓扑

`docker compose up -d --build` 会启动：

- `backend-api`：接收浏览器扩展请求，不消费 AI 队列，主机端口 `8082`。
- `backend-worker`：消费 PostgreSQL `ai_jobs`，运行字幕清理任务，不暴露业务端口。
- `postgres`：持久化业务、Agent 状态和 AI 队列，主机端口 `5433`，避免与本机 PostgreSQL 冲突。
- `redis`：分布式限流和权限缓存，主机端口 `6379`。
- `prometheus`：每 5 秒抓取 API 与 Worker 的独立管理端口，保留 7 天数据，主机端口 `9090`。
- `grafana`：自动加载 Prometheus 数据源和 OmniStudy 面板，主机端口 `3000`。

API 与 Worker 使用同一个不可变镜像，通过 `AI_JOBS_ENABLED`、`MAINTENANCE_ENABLED`、`QUESTION_BANK_SEED_ENABLED` 和 `AGENTSCOPE_ENABLED` 划分职责。两者的 Actuator 运行在容器网络内的 `9091`，不映射到主机。

## Grafana

打开 `http://localhost:3000`，本地默认账号为 `admin / omnistudy-admin`。仪表盘位于 `OmniStudy / OmniStudy 系统总览`，包含：

- API 请求速率、P95 延迟和 5xx 错误率
- API/Worker JVM Heap 和运行时间
- Redis 限流拒绝次数
- AI 队列 `pending/running/failed` 数量
- AI Worker 成功、重试、终止失败与 rerun 速率
- 当前触发告警数量和每条告警状态

对外部署前必须设置新的 `GRAFANA_ADMIN_PASSWORD`、`JWT_SECRET`、`DB_PASSWORD` 和 `REDIS_PASSWORD`。Compose 中的默认值只用于本机开发。

## Prometheus

打开 `http://localhost:9090/targets` 可以确认 `omnistudy-api` 与 `omnistudy-worker` 均为 `UP`。抓取配置在 `monitoring/prometheus/prometheus.yml`。

Spring Boot 的 `/actuator/prometheus` 对容器网络开放；正式 Kubernetes 环境中不要通过 Ingress 暴露管理端口，只允许 Prometheus 所在命名空间访问。

告警规则位于 `monitoring/prometheus/rules/omnistudy-alerts.yml`，当前覆盖实例不可用、API 5xx 比例、P95 延迟、AI 队列积压、终止失败和 Worker 停止消费。这里先在 Grafana 面板中集中查看；需要短信、邮件或 IM 通知时，再接 Alertmanager 或 Grafana Contact Point。

## Kubernetes

`deploy/k8s/base` 提供 API/Worker 双副本、滚动更新、启动/存活/就绪探针、资源限制、HPA、PDB 和 TLS Ingress。部署说明见 `deploy/k8s/README.md`。

Kubernetes 清单只部署无状态 API 与 Worker。正式环境的 PostgreSQL/pgvector 和 Redis 应使用高可用托管服务或独立集群，这样 Pod 重建、节点迁移和应用滚动升级不会影响持久化数据。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend-api backend-worker
docker compose restart prometheus grafana
docker compose down
```

不要在需要保留数据时执行 `docker compose down -v`，该参数会删除 PostgreSQL、Redis、Prometheus 和 Grafana 数据卷。
