# 演示视频

仓库提供可重复执行的 UI 录制流程，避免每次发布手工录制不同版本的界面。

首次运行前安装 Playwright Chromium：

```bash
pnpm exec playwright install chromium
./scripts/record-demo
```

输出文件：

```text
artifacts/demo/omnistudy-ui-demo.webm
```

每次推送与扩展版本一致的 `v*` 标签时，Release 工作流会自动运行测试、构建扩展 ZIP、录制 UI walkthrough，并把 ZIP 和 WebM 一起放入 GitHub Release。

自动视频覆盖登录、注册、学习工作区、Agent、笔记和 BYOK 模型配置页面。它使用隔离的演示登录状态，不发送真实模型请求。

用于项目答辩或简历展示时，建议另录一段 90 秒真实功能视频：

1. 10 秒：展示 `./scripts/local-up` 最终的健康状态；
2. 15 秒：安装扩展、注册并测试个人 API Key；
3. 25 秒：播放 B 站课程，触发苏格拉底问题并作答；
4. 20 秒：展示实时笔记与一次 Agent 查询；
5. 10 秒：打开 Grafana 系统总览；
6. 10 秒：展示 AI Worker、持久化任务和 Kubernetes 清单。

录制前请遮挡 API Key、密码、Access Token 和浏览器个人信息。
