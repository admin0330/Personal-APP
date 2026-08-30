# Ym1r · Personal-APP

个人自托管的邮箱聚合与记账应用。由三部分组成：

- **Go 后端**：多租户邮箱托管（Gmail / Outlook / QQ / 163 等），统一收信、令牌刷新、代理与配额
- **React Web**：网页管理端
- **Android 客户端（Ym1r）**：Material You 个人客户端——收件、笔记、记账、令牌管理与桌面组件

> 本项目派生自开源项目 [MasterAlanLab/emailbox](https://github.com/MasterAlanLab/emailbox)，在此基础上面向个人使用做了大量定制。

## 核心设计

**服务器取信、客户端只读。**

```text
Gmail / Outlook / 163 …
      ↓ 服务器侧代理统一出网
Emailbox 后端（凭据与邮件数据的唯一事实源）
      ↓ HTTPS（只读接口）
Android App / Web
```

- 邮箱凭据、Refresh Token 与代理配置**只存在服务端**，客户端不直连任何邮件服务商
- 客户端通过只读 API 或一次性短码兑换的设备令牌访问，权限最小化、可随时撤销
- 邮件正文在客户端沙箱渲染，默认阻断远程图片（防追踪像素）
- 记账采用「邮件识别 → 待确认 → 手动入账」流程，未入账账单不计入结余
- 离线数据本地加密缓存，登录态切换即清空

## 功能一览

- **邮箱**：多服务商聚合收件箱、域名分类、验证码提取、全局搜索、附件下载（单个/打包 ZIP）
- **令牌**：批量令牌刷新任务、SSE 实时进度、断线重连、刷新统计与日志
- **记账**：快速记一笔（可自定义记账时间）、邮件账单识别为待确认建议、已入账/未入账管理、月度汇总
- **笔记**：轻量个人笔记
- **同步健康中心**：服务器代理与同步状态可视、多节点切换
- **应用内更新**：多更新通道（稳定/测试）、断点续传、SHA-256 校验
- **安全**：指纹/设备密码解锁、Android Keystore 加密缓存、凭据永不下发客户端

## 技术栈

| 部分 | 技术 |
|---|---|
| 后端 | Go 1.25+、Echo v5、sqlc + database/sql（SQLite / PostgreSQL 双引擎）、log/slog、go-imap、Microsoft Graph |
| Web | React 19、TypeScript、Vite、Bun、Tailwind CSS v4 |
| Android | Kotlin 2.0、Jetpack Compose、Material 3（Material You 动态取色）、Retrofit + OkHttp、kotlinx.serialization |

## 本地开发

```bash
# 后端 + 前端（需要 Go 1.25+ 与 Bun）
make dev            # 后端 :1323，前端热更新

# 数据库查询变更后重新生成（需要 sqlc v1.30）
make sqlc-generate && make sqlc-verify

# 前端单独构建
cd web && bun install && bun run build

# Android（Android Studio 或命令行）
cd android
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:assembleRelease    # 发布包（需要自配签名）
```

## 部署

单容器部署（前端静态文件与后端二进制打包在同一镜像内）：

```bash
docker compose up -d --build
```

环境变量与配置项见 `.env.example` 与 `configs/`。数据库迁移在应用启动时自动执行
（支持 SQLite 与 PostgreSQL，双引擎迁移成对提供）。

## 安全模型

- 所有敏感凭据经 AES-256-GCM 加密落库，接口只回传 `has_*` 布尔位，密文永不出后端
- 写操作全量审计；导出类接口独立权限 + 审计 + 限流
- 多租户隔离：每条业务 SQL 强制携带租户条件，跨租户访问一律 404
- 公开注册默认关闭，新用户由已登录用户生成一次性邀请码开通

## 相关仓库

- 上游项目：[MasterAlanLab/emailbox](https://github.com/MasterAlanLab/emailbox)
