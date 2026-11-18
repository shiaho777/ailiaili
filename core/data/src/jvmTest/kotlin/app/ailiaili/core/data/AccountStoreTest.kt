package app.ailiaili.core.data

import app.ailiaili.core.model.SavedAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountStoreTest {
    private fun store(): AccountStore {
        val dir = java.nio.file.Files.createTempDirectory("ailiaili-accounts-test").toFile()
        dir.deleteOnExit()
        return AccountStore(AppFile(java.io.File(dir, "accounts.json").absolutePath))
    }

    private fun account(uid: String, name: String = "") = SavedAccount(
        uid = uid,
        name = name,
        sessData = "sess-$uid",
        biliJct = "jct-$uid",
    )

    @Test
    fun upsertDedupesAndPutsNewestFirst() {
        val s = store()
        s.upsert(account("1", "甲"))
        s.upsert(account("2", "乙"))
        s.upsert(account("1", "甲改"))
        assertEquals(listOf("1", "2"), s.accounts.value.map { it.uid })
        assertEquals("甲改", s.accounts.value.first().name)
    }

    @Test
    fun persistsAcrossReload() {
        val dir = java.nio.file.Files.createTempDirectory("ailiaili-accounts-test").toFile()
        dir.deleteOnExit()
        val file = AppFile(java.io.File(dir, "accounts.json").absolutePath)
        AccountStore(file).apply { upsert(account("7", "丙")) }
        val reloaded = AccountStore(file).apply { load() }
        assertEquals(1, reloaded.accounts.value.size)
        assertEquals("丙", reloaded.accounts.value.single().name)
        assertEquals("sess-7", reloaded.accounts.value.single().sessData)
    }

    @Test
    fun updateProfileKeepsCredentials() {
        val s = store()
        s.upsert(account("3"))
        s.updateProfile("3", "新名", "https://face")
        val a = s.accounts.value.single()
        assertEquals("新名", a.name)
        assertEquals("https://face", a.face)
        assertEquals("sess-3", a.sessData)
        assertEquals("jct-3", a.biliJct)
    }

    @Test
    fun removeAndCap() {
        val s = store()
        repeat(10) { s.upsert(account(it.toString())) }
        assertEquals(AccountStore.MAX_ACCOUNTS, s.accounts.value.size)
        // 最新的在前：0..9 依次 upsert 后保留 9..2
        assertEquals("9", s.accounts.value.first().uid)
        s.remove("9")
        assertTrue(s.accounts.value.none { it.uid == "9" })
    }

    @Test
    fun ignoresBlankCredentials() {
        val s = store()
        s.upsert(SavedAccount(uid = "", sessData = "x", biliJct = "y"))
        s.upsert(SavedAccount(uid = "5", sessData = "", biliJct = "y"))
        assertTrue(s.accounts.value.isEmpty())
    }
}