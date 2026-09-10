# Redis 分布式限流与权限缓存

## 作用

当 `REDIS_ENABLED=true` 时，所有后端实例通过同一组 Redis 键执行固定窗口限流，因此增加 Spring Boot Pod 不会把单个用户的限额成倍放大。Lua 脚本在 Redis 内原子完成计数和过期时间设置。

Redis 同时缓存 JWT 鉴权所需的用户角色与账号状态，默认 TTL 为 60 秒，避免每个已登录请求都查询 `users` 表。管理员修改角色或状态后，会在数据库事务提交成功后删除对应缓存。

## 默认策略

| 接口类别 | 主体 | 默认阈值 |
| --- | --- | --- |
| 登录 | 来源地址 | 10 次/分钟 |
| 注册 | 来源地址 | 5 次/小时 |
| 刷新 Token | 来源地址 | 30 次/分钟 |
| Agent、视频拦截、AI 笔记与学习材料 | 登录用户 | 30 次/分钟 |

触发限流时返回 HTTP `429`、`Retry-After`、`X-RateLimit-Limit` 和 `X-RateLimit-Remaining`。这层瞬时流量保护不会替代数据库中的每日安全用量上限；两者会同时生效。

## 降级行为

- `REDIS_ENABLED=false`：使用实例内固定窗口限流，不启用权限缓存，适合本地开发。
- Redis 短暂不可用：自动回退到实例内限流；JWT 权限信息直接回源 PostgreSQL。
- Redis 故障不会阻断核心业务，所以 Redis 未加入 Kubernetes readiness 判定；Redis 自身应由独立监控告警。

## 本地启用

```bash
docker compose up -d postgres redis
export REDIS_ENABLED=true
export REDIS_PASSWORD=omnistudy
cd packages/backend
mvn spring-boot:run
```

生产环境至少应设置 `REDIS_HOST`、`REDIS_PASSWORD` 和随机的 `REDIS_KEY_PREFIX`。多套环境不能共用相同前缀。
生产 Profile 默认使用 Tomcat 的 `NATIVE` 转发头策略；部署时应禁止公网绕过 Ingress 直接访问后端，并按实际网段配置可信代理。
