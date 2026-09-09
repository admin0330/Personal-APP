# Ym1r 图标系统规范与上游映射文档 (Icon System Specification)

## 1. 上游来源与协议 (Upstream Source & License)
- **图标上游源**：[Lucide Icons](https://github.com/lucide-icons/lucide) (v0.475.0)
- **开源许可证**：ISC License (Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT). All other copyright 2022-2026 Lucide Contributors)
- **原始矢量存储**：`android/app/src/main/assets/icons/lucide/*.svg` 存放对应真实原始 SVG 矢量文件。

## 2. 渲染规约 (Rendering Specification)
- **原生实现**：Compose `ImageVector`，完全运行在 Android 原生渲染管道中，零 Node/Webview 依赖。
- **几何参数**：
  - 视口尺寸：`24 x 24` (viewportWidth = 24f, viewportHeight = 24f, defaultWidth = 24.dp, defaultHeight = 24.dp)
  - 描边线宽：`2.0f` (strokeLineWidth = 2f)
  - 线端样式：`StrokeCap.Round`
  - 拐角连接：`StrokeJoin.Round`
- **颜色渲染**：动态绑定 `LocalContentColor` / 主题色，支持选中高亮与深浅色模式切换。

## 3. 功能映射清单 (Feature-to-Icon Mapping)
| 功能 / 业务用途 | 上游 Lucide 图标 | 本地代码标识 | 原始 SVG 路径 |
| :--- | :--- | :--- | :--- |
| 返回导航 | arrow-left | `Ym1rIcons.ArrowLeft` | `assets/icons/lucide/arrow-left.svg` |
| 前进导航 | arrow-right | `Ym1rIcons.ArrowRight` | `assets/icons/lucide/arrow-right.svg` |
| 上移项 | arrow-up | `Ym1rIcons.ArrowUp` | `assets/icons/lucide/arrow-up.svg` |
| 下移项 | arrow-down | `Ym1rIcons.ArrowDown` | `assets/icons/lucide/arrow-down.svg` |
| 二级展开 / 详情跳转 | chevron-right | `Ym1rIcons.ChevronRight` | `assets/icons/lucide/chevron-right.svg` |
| 下拉展开 / 折叠 | chevron-down | `Ym1rIcons.ChevronDown` | `assets/icons/lucide/chevron-down.svg` |
| 上拉收起 | chevron-up | `Ym1rIcons.ChevronUp` | `assets/icons/lucide/chevron-up.svg` |
| 邮件与全局搜索 | search | `Ym1rIcons.Search` | `assets/icons/lucide/search.svg` |
| 关闭 / 清空搜索 | x | `Ym1rIcons.X` | `assets/icons/lucide/x.svg` |
| 新建 / 添加 | plus | `Ym1rIcons.Plus` | `assets/icons/lucide/plus.svg` |
| 选中 / 完成 | check | `Ym1rIcons.Check` | `assets/icons/lucide/check.svg` |
| 全选 / 双确认 | check-check | `Ym1rIcons.CheckCheck` | `assets/icons/lucide/check-check.svg` |
| 编辑 / 重命名 | pencil | `Ym1rIcons.Pencil` | `assets/icons/lucide/pencil.svg` |
| 删除 / 移除 | trash-2 | `Ym1rIcons.Trash2` | `assets/icons/lucide/trash-2.svg` |
| 复制密钥 / 文本 | copy | `Ym1rIcons.Copy` | `assets/icons/lucide/copy.svg` |
| 刷新 / 同步 | refresh-cw | `Ym1rIcons.RefreshCw` | `assets/icons/lucide/refresh-cw.svg` |
| 邮件 / 发件箱 | mail | `Ym1rIcons.Mail` | `assets/icons/lucide/mail.svg` |
| 收件箱 | inbox | `Ym1rIcons.Inbox` | `assets/icons/lucide/inbox.svg` |
| 附件 | paperclip | `Ym1rIcons.Paperclip` | `assets/icons/lucide/paperclip.svg` |
| 显示密码 | eye | `Ym1rIcons.Eye` | `assets/icons/lucide/eye.svg` |
| 隐藏密码 | eye-off | `Ym1rIcons.EyeOff` | `assets/icons/lucide/eye-off.svg` |
| API Key / 邀请码 | key | `Ym1rIcons.Key` | `assets/icons/lucide/key.svg` |
| 安全锁定 / 密码 | lock | `Ym1rIcons.Lock` | `assets/icons/lucide/lock.svg` |
| 平台管理 / 代理防护 | shield | `Ym1rIcons.Shield` | `assets/icons/lucide/shield.svg` |
| 系统与应用设置 | settings | `Ym1rIcons.Settings` | `assets/icons/lucide/settings.svg` |
| 个人中心 / 用户 | user | `Ym1rIcons.User` | `assets/icons/lucide/user.svg` |
| 工作空间成员 | users | `Ym1rIcons.Users` | `assets/icons/lucide/users.svg` |
| 文件夹 / 邮箱分组 | folder | `Ym1rIcons.Folder` | `assets/icons/lucide/folder.svg` |
| 导出 / 备份 / 更新 | download | `Ym1rIcons.Download` | `assets/icons/lucide/download.svg` |
| 导入账号 | upload | `Ym1rIcons.Upload` | `assets/icons/lucide/upload.svg` |
| 便签 / 审计日志 | file-text | `Ym1rIcons.FileText` | `assets/icons/lucide/file-text.svg` |
| 记账账本 | wallet | `Ym1rIcons.Wallet` | `assets/icons/lucide/wallet.svg` |
| 任务指标 / 同步健康 | activity | `Ym1rIcons.Activity` | `assets/icons/lucide/activity.svg` |
| 警告提示 / 异常信息 | alert-circle | `Ym1rIcons.AlertCircle` | `assets/icons/lucide/alert-circle.svg` |
| 服务器节点 / 存储 | server | `Ym1rIcons.Server` | `assets/icons/lucide/server.svg` |
| 偏好配置 / 状态筛选 | sliders | `Ym1rIcons.Sliders` | `assets/icons/lucide/sliders.svg` |
| 停用账号 | ban | `Ym1rIcons.Ban` | `assets/icons/lucide/ban.svg` |
| 节点正常 / 状态成功 | check-circle | `Ym1rIcons.CheckCircle` | `assets/icons/lucide/check-circle.svg` |
| 云端同步正常 | cloud-check | `Ym1rIcons.CloudCheck` | `assets/icons/lucide/cloud-check.svg` |

## 4. 底部液态玻璃 Dock 栏图标隔离保护 (Dock Icons Isolation)
底部 Dock 栏的 4 个基础图标已在 `DockIcons.kt` 中独立冻结保护（Inbox、Dashboard、FileText、Wallet），拥有专属液态玻璃光学着色与折射管道，与通用功能图标解耦，杜绝任何外部干扰。
