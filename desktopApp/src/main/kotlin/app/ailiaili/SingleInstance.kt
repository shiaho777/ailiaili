package app.ailiaili

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import javax.swing.SwingUtilities
import app.ailiaili.core.data.DesktopHost

/**
 * 同一用户会话只跑一个桌面进程。第二个实例把「请显形」发给已在跑的那个后退出。
 */
object SingleInstance {
    private var lockFile: RandomAccessFile? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /** 握手魔数：端口文件可能陈旧指向无关进程，必须验明正身才算通知成功。 */
    private const val MAGIC = 0xA1

    fun tryAcquire(): Boolean {
        val dir = File(System.getProperty("user.home"), ".ailiaili").apply { mkdirs() }
        val lockPath = File(dir, "instance.lock")
        val portPath = File(dir, "instance.port")
        val raf = try {
            RandomAccessFile(lockPath, "rw")
        } catch (_: Exception) {
            // 锁文件都打不开（权限/磁盘异常）：放行让用户能用，属罕见环境故障。
            return true
        }
        val got = try {
            raf.channel.tryLock()
        } catch (_: Exception) {
            raf.close()
            return true
        }
        if (got == null) {
            raf.close()
            signal(portPath)
            return false
        }
        return try {
            lockFile = raf
            lock = got
            val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            server = ss
            portPath.writeText(ss.localPort.toString())
            Thread({
                while (!ss.isClosed) {
                    try {
                        ss.accept().use { socket ->
                            // 只认带魔数的握手：陈旧端口指向的无关进程不会
                            // 被误当成「请显形」信号，也不会被我们注入字节。
                            if (socket.getInputStream().read() == MAGIC) {
                                socket.getOutputStream().write(MAGIC)
                                SwingUtilities.invokeLater { DesktopHost.showWindow?.invoke() }
                            }
                        }
                    } catch (_: Exception) {
                        try {
                            Thread.sleep(400)
                        } catch (_: InterruptedException) {
                            return@Thread
                        }
                    }
                }
            }, "ailiaili-single").apply { isDaemon = true }.start()
            true
        } catch (_: Exception) {
            // 拿到锁却起不了监听：释放锁让下次启动能接管，本次仍按主实例运行。
            runCatching { got.release() }
            runCatching { raf.close() }
            lock = null
            lockFile = null
            true
        }
    }

    private fun signal(portFile: File) {
        repeat(20) {
            val port = runCatching { portFile.readText().trim().toInt() }.getOrNull()
            if (port != null && port > 0) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                        socket.soTimeout = 400
                        socket.getOutputStream().write(MAGIC)
                        // 对端回同一魔数才确认是 ailiaili 主实例；
                        // 无关进程不会回，按失败重试（等主实例写好新端口）。
                        if (socket.getInputStream().read() == MAGIC) return
                    }
                } catch (_: Exception) {
                }
            }
            try {
                Thread.sleep(80)
            } catch (_: InterruptedException) {
                return
            }
        }
    }
}
