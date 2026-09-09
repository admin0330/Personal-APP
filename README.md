# Ym1r · Personal APP

**简体中文** | [English](README.en.md)

Ym1r 是基于 Emailbox 持续开发的个人应用，包含 Android 原生客户端、Go 后端和 React Web 管理界面，将邮件聚合、笔记、记账与账号管理放在同一套系统中。

当前公开源码版本：**1.5.2 / versionCode 79**。这是脱敏后的开发源码快照，源码同步不代表已经发布对应稳定版 APK，也不代表完成了该版本的真机验收。

## 主要功能

- 邮件：多账号与分组、列表与正文、附件、搜索、OAuth 重新授权及同步状态。
- 本地优先：笔记、记账与邮件缓存；敏感缓存和凭据按各模块的安全策略处理。
- Android 界面：Jetpack Compose、清透与经典主题、手势侧栏、液态玻璃 Dock、滚动数字动效。
- 安全边界：租户隔离、权限校验、操作审计、凭据加密与邮件正文隔离渲染。
- 最新邮件改动：取消过期请求、优先显示缓存与正文、减少批量正文预取、限制后台轮询并发及中断超时 IMAP 连接。

## 项目结构

| 路径 | 内容 |
| --- | --- |
| `android/` | Kotlin / Jetpack Compose 客户端及测试 |
| `api/`、`pkg/`、`configs/` | Go / Echo 后端、业务逻辑与配置 |
| `db/` | SQLite / PostgreSQL 迁移、查询与 sqlc 生成代码 |
| `web/` | React 19 / TypeScript / Vite 管理界面 |
| `docs/` | 配置说明与历史设计记录；当前行为以源码为准 |
| `scripts/` | 构建、代码检查与密钥生成工具 |

## 本地启动

准备 Go（版本见 `go.mod`）、Bun（版本见 `web/package.json`），然后：

```sh
git clone https://github.com/admin0330/Personal-APP.git
cd Personal-APP
cp .env.example .env
go run ./scripts/genkey
```

将生成的密钥填入本地 `.env` 的 `ENCRYPTION_KEY`，自行设置管理员用户名和初始密码。仓库不提供可用的管理员密码。然后分别启动后端与 Web：

```sh
go run .
```

```sh
cd web
bun install --frozen-lockfile
bun run dev
```

默认后端端口为 `1323`，Web 开发端口为 `5173`，Vite 将 `/api` 转发到本地后端。Windows PowerShell 可使用 `Copy-Item .env.example .env` 复制配置。

生产部署需设置 `APP_ENV=production`、独立加密密钥和 HTTPS Cookie 配置，并检查注册策略、反向代理与 OAuth 回调。配置说明见 [docs/configuration.md](docs/configuration.md)。`docker-compose.yml` 是本地开发示例，使用 Docker 时必须显式传入自己的环境配置。

## Android 构建

准备 JDK 17 或更高版本（本地验证使用 JDK 21）、Android SDK Platform 37，通过 `ANDROID_HOME` 或未纳入版本控制的 `android/local.properties` 指定 SDK。

此公开副本将服务域名替换为 `example.com`，并使用系统 DNS。当前登录界面使用内置服务器地址；自行部署时请先修改：

- `android/app/src/main/java/com/masteralanlab/emailbox/data/Prefs.kt` 中的 `DEFAULT_SERVER`。
- `data/remote/ApiClient.kt` 中与默认域名对应的路径规范化规则。
- `update/UpdateManager.kt` 中的稳定版和测试版清单地址；需要自己托管更新文件。
- `data/remote/ApiModels.kt` 中的图片域名映射（如不使用双域名，可返回原地址）。

以上后两项路径均相对于同一 `com/masteralanlab/emailbox/` 包目录；修改域名时同步更新对应单元测试。Android 连接地址要求 HTTPS。

```sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows 使用 `./gradlew.bat`。产物位于 `android/app/build/outputs/apk/`。正式构建使用 `:app:assembleRelease`；自己的签名配置写入未跟踪的 `android/keystore.properties`：

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_PRIVATE_KEY_PASSWORD
```

未提供签名配置时构建脚本使用本机调试签名，不能用作正式发布签名或覆盖安装已有正式应用。发布者还应设置自己的应用 ID 与更新渠道。

应用图标原图、各密度图层及单色图层均纳入源码。原图在 `android/app/src/main/assets/branding/app_icon_source.png`；生成及 APK 检查脚本位于 `android/tools/`，运行需安装 Pillow。

## 检查与脱敏范围

```sh
go test ./...
go vet ./...
cd web
bun run lint
bun run test
bun run build
```

完整检查入口为 `make lint` 与 `make test`。PostgreSQL 对照测试需要单独配置测试数据库。

本次公开快照排除了运行数据库、邮件内容、日志、截图、APK、构建缓存、签名私钥、本机 SDK 配置、真实环境变量及私人运维交接资料。服务域名使用示例值；源站 IP 定向解析已移除。此处理发生在独立公开副本中，不改变私人部署配置。请勿将生产密钥或运行数据提交到仓库。

## 来源与许可

基于 [MasterAlanLab/emailbox](https://github.com/MasterAlanLab/emailbox) 开发，保留现有仓库的 [MIT 许可证](LICENSE)。第三方代码和资源仍遵循各自许可。

液态玻璃使用 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 相关依赖；当前图标映射说明见 [android/MORPHICONS.md](android/MORPHICONS.md)（实际资源为 Lucide）。滚动数字为 Compose 实现，设计参考 [Rolling](https://rolling.kitlangton.dev/)，并非直接集成 Swift 库。
