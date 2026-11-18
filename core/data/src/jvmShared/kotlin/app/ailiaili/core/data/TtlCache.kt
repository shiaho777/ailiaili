package app.ailiaili.core.data

import app.ailiaili.core.model.epochMs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 轻量 TTL 缓存，只缓存成功结果。
 *
 * 用于播放页/空间页这类“返回再进入”很频繁的只读数据，避免每次重新走一遍网络。
 * 失败不缓存，过期条目在写入时顺手清理，容量有上限，避免长期驻留。
 *
 * 同 key 的并发未命中会合并成一次 load（单飞）：双栏/快速切页时，
 * 同一视频的详情不会被同时请求两遍。首个调用者取消/失败时等待者各自回退加载。
 */
internal class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxEntries: Int = 64,
    private val clock: () -> Long = ::epochMs,
) {
    private data class Entry<V>(val value: V, val at: Long)

    private val lock = Mutex()
    private val entries = LinkedHashMap<K, Entry<V>>()
    private val inFlight = HashMap<K, CompletableDeferred<Result<V>>>()

    suspend fun get(key: K, load: suspend () -> Result<V>): Result<V> {
        val now = clock()
        // 查缓存与注册单飞必须在同一个锁段里：分成两段的话，并发未命中会
        // 各自成为 owner 重复请求，后注册者还会覆盖前者的 deferred。
        val (deferred, own) = lock.withLock {
            val hit = entries[key]
            if (hit != null) {
                if (now - hit.at <= ttlMs) return Result.success(hit.value)
                entries.remove(key)
            }
            val existing = inFlight[key]
            if (existing != null) {
                existing to null
            } else {
                val fresh = CompletableDeferred<Result<V>>()
                inFlight[key] = fresh
                null to fresh
            }
        }
        if (own == null) {
            return try {
                deferred!!.await()
            } catch (_: Throwable) {
                load()
            }
        }
        return try {
            val result = load()
            result.onSuccess { value ->
                val at = clock()
                lock.withLock {
                    entries.keys.removeAll { key0 ->
                        val entry = entries[key0] ?: return@removeAll true
                        at - entry.at > ttlMs
                    }
                    entries.remove(key)
                    entries[key] = Entry(value, at)
                    while (entries.size > maxEntries) {
                        val oldest = entries.keys.firstOrNull() ?: break
                        entries.remove(oldest)
                    }
                }
            }
            own.complete(result)
            result
        } catch (t: Throwable) {
            own.completeExceptionally(t)
            throw t
        } finally {
            // 取消路径下 withLock 会因挂起抛 CancellationException 跳过清理，
            // inFlight 残留异常态 deferred 让该 key 的单飞永久失效。必须不可取消。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                lock.withLock { if (inFlight[key] === own) inFlight.remove(key) }
            }
        }
    }

    suspend fun invalidate(key: K) {
        lock.withLock { entries.remove(key) }
    }

    suspend fun clear() {
        lock.withLock { entries.clear() }
    }
}
