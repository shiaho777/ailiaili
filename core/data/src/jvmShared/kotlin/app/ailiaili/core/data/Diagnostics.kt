package app.ailiaili.core.data

import app.ailiaili.core.model.USER_AGENT

/**
 * 问题诊断信息（借鉴 FocuBili 的诊断导出）：一段可复制的脱敏文本，
 * 用户反馈播放/风控问题时贴上它，排查不再靠猜。
 * 绝不包含 Cookie、SESSDATA、完整 buvid 等凭据；账号只留是否登录。
 */
object Diagnostics {
    /** 最近一次播放的关键参数，由播放器起播时写入。 */
    @Volatile var lastPlayInfo: String = "（本次会话还没有播放记录）"

    fun build(appVersion: String): String = buildString {
        appendLine("== ailiaili 诊断信息 ==")
        appendLine("版本：$appVersion")
        appendLine("平台：${hostKind()} / ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("Java：${System.getProperty("java.version")}")
        val settings = AppGraph.settings.current
        val session = AppGraph.repo.session.value
        appendLine("登录：${if (session.loggedIn) "已登录" else "未登录"}")
        appendLine("buvid：${maskId(session.buvid3)} / ${maskId(session.buvid4)}")
        appendLine("线路模式：${settings.cdnMode}${if (settings.cdnMode.name == "Manual") "（${settings.cdnManualId}）" else ""}")
        appendLine("屏蔽 PCDN：${settings.blockPcdn}")
        appendLine("缓冲：${if (settings.bufferAuto) "自动" else "${settings.networkCachingMs}ms"}")
        appendLine("默认画质：${settings.defaultQn}；免登录 1080P：${settings.noLogin1080}")
        appendLine("UA：${if (settings.customUserAgent.isBlank()) "默认（$USER_AGENT）" else "自定义（${settings.customUserAgent}）"}")
        appendLine("CDN 测速：${AppGraph.repo.cdnReport.value}")
        appendLine("最近播放：$lastPlayInfo")
        appendLine("== 凭据已脱敏，可放心分享 ==")
    }

    /** 只留前 4 后 4，中间打码；空值显示「无」。 */
    private fun maskId(value: String?): String {
        if (value.isNullOrBlank()) return "无"
        if (value.length <= 8) return "****"
        return "${value.take(4)}…${value.takeLast(4)}"
    }
}
