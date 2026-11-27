package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyAddressTest {
    @Test
    fun parsesHostPortAndScheme() {
        assertEquals(ProxyAddress("127.0.0.1", 7890), parseProxyAddress("127.0.0.1:7890"))
        assertEquals(ProxyAddress("127.0.0.1", 7890), parseProxyAddress("http://127.0.0.1:7890"))
        assertEquals(ProxyAddress("proxy.local", 8080), parseProxyAddress("HTTP://proxy.local:8080/"))
        assertEquals(ProxyAddress("10.0.0.2", 1080), parseProxyAddress("  10.0.0.2:1080  "))
    }

    @Test
    fun blankMeansDirect() {
        assertNull(parseProxyAddress(""))
        assertNull(parseProxyAddress("   "))
    }

    @Test
    fun rejectsInvalidFormats() {
        listOf(
            "127.0.0.1",          // 缺端口
            "127.0.0.1:0",        // 端口越界
            "127.0.0.1:70000",
            "127.0.0.1:abc",      // 非数字端口
            ":7890",              // 缺主机
            "socks5://127.0.0.1:1080", // 非 HTTP 代理
            "http://127.0.0.1",   // 带 scheme 但缺端口
        ).forEach { raw ->
            assertTrue(runCatching { parseProxyAddress(raw) }.isFailure, "应拒绝：$raw")
        }
    }
}