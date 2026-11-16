package app.ailiaili.core.network

import app.ailiaili.core.model.epochMs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 传输层瞬时失败才值得重试：连接抖动、网关 5xx、限流 429。
 * B 站业务错误码（-412 风控 / -101 未登录等）重试只会更糟，一律不重试。
 */
fun isTransientHttpStatus(code: Int): Boolean =
    code == 408 || code == 429 || code in 500..599

/** 指数退避：300ms → 900ms，带 ±20% 抖动防止同步重试风暴。 */
fun retryBackoffMs(attempt: Int, jitter: Double = 0.0): Long {
    val base = when (attempt) {
        0 -> 300L
        1 -> 900L
        else -> 2_000L
    }
    val factor = 1.0 + jitter.coerceIn(-0.2, 0.2)
    return (base * factor).toLong().coerceAtLeast(50L)
}

/**
 * 单飞合并：同 key 的并发调用只发一次真实请求，其余等同一份结果。
 *
 * 首个调用者被取消或失败时，等待者各自回退执行 load——合并是尽力而为的
 * 优化，正确性不依赖它。页面快速切换/双栏同时打开时不再重复打接口。
 */
class SingleFlight<K : Any, V> {
    private val lock = Mutex()
    private val inFlight = HashMap<K, CompletableDeferred<V>>()

    suspend fun run(key: K, load: suspend () -> V): V {
        val (deferred, owner) = lock.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val fresh = CompletableDeferred<V>()
                inFlight[key] = fresh
                fresh to true
            }
        }
        if (owner) {
            try {
                val value = load()
                deferred.complete(value)
                return value
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
                throw t
            } finally {
                lock.withLock { inFlight.remove(key) }
            }
        }
        return try {
            deferred.await()
        } catch (_: Throwable) {
            // 首个调用者取消/失败：自己再试一次，不把别人的取消当成自己的失败。
            load()
        }
    }
}

/**
 * 只读接口的小容量 TTL 缓存。只存成功值；过期条目写入时顺手清理。
 * 与数据层 TtlCache 同一语义，放在网络层供客户端内部使用（不反向依赖数据层）。
 */
class ShortTtlCache<K : Any, V : Any>(
    private val maxEntries: Int = 96,
    private val clock: () -> Long = ::epochMs,
) {
    private data class Entry<V>(val value: V, val expireAt: Long)

    private val lock = Mutex()
    private val entries = LinkedHashMap<K, Entry<V>>()

    suspend fun get(key: K): V? = lock.withLock {
        val hit = entries[key] ?: return@withLock null
        if (clock() < hit.expireAt) hit.value else {
            entries.remove(key)
            null
        }
    }

    suspend fun put(key: K, value: V, ttlMs: Long) {
        val now = clock()
        lock.withLock {
            entries.keys.removeAll { k -> entries[k]?.let { now >= it.expireAt } ?: true }
            entries.remove(key)
            entries[key] = Entry(value, now + ttlMs)
            while (entries.size > maxEntries) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)
            }
        }
    }

    suspend fun clear() {
        lock.withLock { entries.clear() }
    }
}
