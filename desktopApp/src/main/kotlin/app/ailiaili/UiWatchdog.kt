package app.ailiaili

import java.io.File
import java.lang.management.ManagementFactory

/**
 * 界面卡死诊断探针（临时调试设施，定位「连环功能失效」后移除）。
 *
 * 原理：AWT 事件线程与 Compose/Skiko 渲染共用消息泵，界面线程被阻塞时
 * 心跳探针无法执行。守护线程每 2 秒投一个探针，8 秒没执行就判定卡死，
 * 立刻把全线程栈 dump 到 ~/.ailiaili/ui-stall.log——不需要卡死时人工抓栈。
 *
 * 只写本机诊断文件，不发网络、不碰用户数据。
 */
internal object UiWatchdog {
    private const val PROBE_INTERVAL_MS = 2_000L
    private const val STALL_THRESHOLD_MS = 8_000L

    fun start() {
        val logFile = File(File(System.getProperty("user.home"), ".ailiaili"), "ui-stall.log")
        val lastBeat = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val thread = Thread({
            var dumpedForCurrentStall = false
            while (true) {
                try {
                    Thread.sleep(PROBE_INTERVAL_MS)
                    java.awt.EventQueue.invokeLater { lastBeat.set(System.currentTimeMillis()) }
                    val silentMs = System.currentTimeMillis() - lastBeat.get()
                    if (silentMs > STALL_THRESHOLD_MS) {
                        if (!dumpedForCurrentStall) {
                            dumpedForCurrentStall = true
                            runCatching {
                                logFile.parentFile?.mkdirs()
                                val sb = StringBuilder()
                                sb.append("==== UI stall detected: ").append(silentMs).append("ms silent @ ")
                                    .append(java.time.LocalDateTime.now()).append(" ====\n")
                                val mx = ManagementFactory.getThreadMXBean()
                                mx.dumpAllThreads(true, true).forEach { info ->
                                    sb.append(info.toString()).append('\n')
                                }
                                logFile.appendText(sb.toString())
                            }
                        }
                    } else {
                        dumpedForCurrentStall = false
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                    // 探针自身永不影响应用。
                }
            }
        }, "ailiaili-ui-watchdog")
        thread.isDaemon = true
        thread.start()
    }
}