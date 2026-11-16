package org.portix.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SourceReport(
    val owner: String,
    val repo: String,
    val branch: String,
    val license: String?,
    val description: String?,
    val system: String?,
    val notes: List<String>,
) {
    val cloneUrl: String get() = "https://github.com/$owner/$repo.git"
}

object SourceAnalyzer {
    private val URL_RE = Regex("github\\.com[/:]([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)")
    private val BRANCH_OK = Regex("[A-Za-z0-9._/-]+")

    // urutan = prioritas
    private val SYSTEMS = listOf(
        "flutter" to listOf("pubspec.yaml"),
        "cargo" to listOf("Cargo.toml"),
        "go" to listOf("go.mod"),
        "cmake" to listOf("CMakeLists.txt"),
        "meson" to listOf("meson.build"),
        "node" to listOf("package.json"),
        "gradle" to listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"),
        "maven" to listOf("pom.xml"),
        "make" to listOf("Makefile"),
        "python" to listOf("pyproject.toml", "setup.py"),
    )

    fun analyze(input: String): SourceReport {
        val m = URL_RE.find(input) ?: error("URL GitHub tidak valid")
        val owner = m.groupValues[1]
        val repo = m.groupValues[2].removeSuffix(".git")

        val info = JSONObject(get("https://api.github.com/repos/$owner/$repo"))
        val branch = info.str("default_branch")?.takeIf { BRANCH_OK.matches(it) } ?: "main"
        val arr = JSONArray(get("https://api.github.com/repos/$owner/$repo/contents"))
        val files = (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }.toSet()

        val system = SYSTEMS.firstOrNull { (_, marks) -> marks.any { it in files } }?.first
        val license = info.optJSONObject("license")?.str("spdx_id")

        val notes = mutableListOf<String>()
        when (system) {
            null -> notes += "Sistem build tidak dikenali di root repo; cek subfolder atau README."
            "gradle" -> notes += "Proyek Gradle sering khusus Android; pastikan ada target desktop/JVM."
            "node" -> notes += "Aplikasi Node/web bisa jalan di Linux; untuk aplikasi desktop biasanya perlu Electron/Tauri."
            "flutter" -> if ("linux" !in files) notes += "Folder linux/ tidak ada; jalankan `flutter create --platforms=linux .` lebih dulu."
        }
        if (license == null || license == "NOASSERTION") notes += "Lisensi tidak terdeteksi: cek izin distribusi ulang."
        if (notes.isEmpty()) notes += "Struktur proyek terlihat cocok untuk dibangun di Linux."

        return SourceReport(owner, repo, branch, license, info.str("description"), system, notes)
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Portix")
        c.connectTimeout = 15000
        c.readTimeout = 15000
        if (c.responseCode !in 200..299) {
            error("GitHub membalas ${c.responseCode} (repo privat/tidak ada, atau batas permintaan tercapai)")
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
