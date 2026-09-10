# 本地数据导出、导入与备份

OmniStudy 的本地备份是实例级完整备份，适合重装系统、换电脑、升级前保护数据，也可以作为本地数据导出/导入方式。

## 导出/备份

保持 PostgreSQL 容器运行，然后执行：

```bash
./scripts/local-backup
```

默认生成：

```text
backups/omnistudy-backup-<UTC时间>.tar.gz
```

也可以指定输出位置：

```bash
./scripts/local-backup /Volumes/MyDisk/omnistudy-backup.tar.gz
```

归档包含 PostgreSQL 自定义格式转储、备份版本信息和 `.env.local` 中的运行密钥。用户 API Key 在数据库里是 AES-GCM 密文，必须同时保存原加密密钥才能在新电脑解密。

备份归档等同于密码文件：不要上传公开网盘、不要提交 Git，建议保存到加密磁盘或加密压缩包中。

## 导入/恢复

恢复会替换当前数据库和本地运行密钥，因此必须显式传入 `--yes`：

```bash
./scripts/local-restore /path/to/omnistudy-backup.tar.gz --yes
./scripts/local-up
```

恢复脚本会：

1. 拒绝包含未知路径或符号链接的归档；
2. 停止 API 和 Worker，避免恢复期间继续写入；
3. 自动保留当前 `.env.local.before-restore-<时间>`；
4. 恢复数据库与匹配的加密密钥；
5. 重新启动 API 和 Worker。

恢复完成后应登录插件，检查笔记、问题和模型配置。确认无误后再安全删除旧的 `.env.local.before-restore-*`。

## 当前边界

这是完整实例迁移，不会把一个用户的数据合并进另一个正在使用的实例。多人共享部署若需要按用户导入导出，应使用单独的用户级归档接口，而不是数据库覆盖恢复。
