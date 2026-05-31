package com.urugus.tsuzuri.data.vault

/** テスト用のインメモリ vault。書き込み内容をそのまま保持する。 */
class FakeVaultStorage(
    initial: Map<String, String> = emptyMap(),
) : VaultStorage {
    val files = LinkedHashMap<String, String>(initial)

    override suspend fun readText(fileName: String): String? = files[fileName]

    override suspend fun writeText(fileName: String, content: String) {
        files[fileName] = content
    }

    override suspend fun listFileNames(): List<String> = files.keys.toList()

    override suspend fun delete(fileName: String): Boolean = files.remove(fileName) != null
}
