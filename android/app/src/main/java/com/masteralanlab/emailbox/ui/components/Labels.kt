package com.masteralanlab.emailbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

object Labels {

    fun accountStatus(s: String?) = when (s) {
        "active" -> "正常"
        "disabled" -> "已停用"
        "banned" -> "已封禁"
        else -> s?.takeIf { it.isNotBlank() } ?: "未知"
    }

    fun refreshStatus(s: String?) = when (s) {
        "never" -> "未刷新"
        "success" -> "成功"
        "failed" -> "失败"
        else -> s?.takeIf { it.isNotBlank() } ?: "未刷新"
    }

    fun jobStatus(s: String?) = when (s) {
        "pending" -> "排队中"
        "running" -> "进行中"
        "stopping" -> "停止中"
        "succeeded" -> "已完成"
        "partial" -> "部分失败"
        "failed" -> "失败"
        "stopped" -> "已停止"
        "interrupted" -> "已中断"
        else -> s?.takeIf { it.isNotBlank() } ?: "未知"
    }

    fun jobItemStatus(s: String?) = when (s) {
        "pending" -> "排队中"
        "running" -> "进行中"
        "success" -> "成功"
        "failed" -> "失败"
        "skipped" -> "已跳过"
        else -> s?.takeIf { it.isNotBlank() } ?: "未知"
    }

    /** 上游错误分类的中文说明，直接展示给使用者。 */
    fun errorKind(kind: String?) = when (kind) {
        null, "" -> ""
        "auth_failed" -> "凭据失效，需重新授权"
        "banned" -> "账号被服务商封禁"
        "consent_required" -> "应用权限不足，需重新授权"
        "proxy_failed" -> "代理不可用"
        "network" -> "网络错误"
        "rate_limited" -> "上游限流"
        "folder_unavailable" -> "邮件夹不可用"
        "provider_error" -> "服务商错误"
        "canceled" -> "请求超时或已取消"
        else -> kind
    }

    fun channel(c: String?) = when (c) {
        "graph" -> "Graph"
        "imap_new" -> "IMAP 新版"
        "imap_old" -> "IMAP 旧版"
        "imap" -> "IMAP"
        else -> c?.takeIf { it.isNotBlank() } ?: "—"
    }

    fun accountType(t: String?) = when (t) {
        "outlook" -> "Outlook"
        "imap" -> "IMAP"
        else -> (t?.takeIf { it.isNotBlank() } ?: "—").replaceFirstChar { it.uppercase() }
    }

    fun provider(p: String?) = when (p) {
        "outlook" -> "Outlook"
        "gmail" -> "Gmail"
        "qq" -> "QQ 邮箱"
        "163" -> "163"
        "126" -> "126"
        "yahoo" -> "Yahoo"
        "aliyun" -> "阿里邮箱"
        "2925" -> "2925"
        "custom" -> "自定义"
        else -> p?.takeIf { it.isNotBlank() } ?: "—"
    }

    fun auditAction(a: String?) = when (a) {
        "account.list" -> "查看账号列表"
        "account.read" -> "查看账号"
        "account.create" -> "新建账号"
        "account.update" -> "修改账号"
        "account.delete" -> "删除账号"
        "account.import" -> "导入账号"
        "account.batch" -> "批量操作账号"
        "account.export" -> "导出账号"
        "message.read" -> "读取邮件"
        "message.write" -> "修改邮件"
        "group.write" -> "修改分组"
        "api_key.reset" -> "重置 API Key"
        "token.refresh" -> "刷新令牌"
        "token.reauthorize" -> "重新授权"
        "job.submit" -> "提交任务"
        "job.stop" -> "停止任务"
        "user.update" -> "修改用户"
        "user.delete" -> "删除用户"
        "user.reset_password" -> "重置用户密码"
        "plan.create" -> "新建套餐"
        "plan.update" -> "修改套餐"
        "plan.delete" -> "删除套餐"
        "quota.update" -> "调整配额"
        else -> a ?: "—"
    }

    fun actorKind(k: String?) = when (k) {
        "user" -> "用户"
        "admin" -> "平台管理员"
        "api_key" -> "API Key"
        "system" -> "系统"
        else -> k ?: "—"
    }

    fun tenantRole(r: String?) = when (r) {
        "owner" -> "所有者"
        "admin" -> "管理员"
        "member" -> "成员"
        else -> r ?: "—"
    }
}

/** 分组颜色：与后端 GroupColor 枚举一致。 */
fun groupColorKey(color: String?): String = color?.takeIf { it.isNotBlank() } ?: "gray"

@Composable
fun groupTint(color: String?): Color = when (groupColorKey(color)) {
    "blue" -> MaterialTheme.colorScheme.primary
    "green" -> MaterialTheme.colorScheme.tertiary
    "amber" -> MaterialTheme.colorScheme.secondary
    "red" -> MaterialTheme.colorScheme.error
    "purple" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun statusContainer(status: String?): Color = when (status) {
    "active", "success", "succeeded" -> MaterialTheme.colorScheme.secondaryContainer
    "failed", "banned", "partial" -> MaterialTheme.colorScheme.errorContainer
    "running", "pending", "stopping" -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun statusContent(status: String?): Color = when (status) {
    "active", "success", "succeeded" -> MaterialTheme.colorScheme.onSecondaryContainer
    "failed", "banned", "partial" -> MaterialTheme.colorScheme.onErrorContainer
    "running", "pending", "stopping" -> MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
