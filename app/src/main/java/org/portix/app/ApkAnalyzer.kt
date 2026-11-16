package org.portix.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class ApkReport(
    val file: File,
    val packageName: String,
    val label: String,
    val versionName: String?,
    val minSdk: Int,
    val targetSdk: Int,
    val abis: Set<String>,
    val usesGms: Boolean,
    val usesIntegrity: Boolean,
    val score: Int,
    val notes: List<String>,
    val iconPng: ByteArray?,
) {
    val armOnly: Boolean get() = abis.isNotEmpty() && abis.none { it.startsWith("x86") }
}

object ApkAnalyzer {
    private val DEX = Regex("classes\\d*\\.dex")

    fun analyze(ctx: Context, uri: Uri): ApkReport {
        val file = File(ctx.cacheDir, "input.apk")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> file.outputStream().use { i.copyTo(it) } }

        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(file.path, PackageManager.GET_PERMISSIONS)
            ?: error("Bukan APK yang valid (XAPK/APKS belum didukung)")
        val app = info.applicationInfo!!
        app.sourceDir = file.path
        app.publicSourceDir = file.path

        val abis = linkedSetOf<String>()
        var gms = false
        var integrity = false
        ZipFile(file).use { zip ->
            for (e in zip.entries().asSequence()) {
                if (e.isDirectory) continue
                if (e.name.startsWith("lib/")) e.name.split('/').getOrNull(1)?.let { abis += it }
                if (DEX.matches(e.name)) {
                    if (!gms) gms = zip.getInputStream(e).use { it.contains("com/google/android/gms".toByteArray()) }
                    if (!integrity) integrity = zip.getInputStream(e).use { it.contains("com/google/android/play/core/integrity".toByteArray()) ||
                            it.contains("com/google/android/play/integrity".toByteArray()) }
                }
            }
        }

        var score = 100
        val notes = mutableListOf<String>()
        val armOnly = abis.isNotEmpty() && abis.none { it.startsWith("x86") }
        if (armOnly) { score -= 20; notes += "Library native hanya ARM: di PC x86_64 perlu lapisan translasi (libhoudini/libndk) di Waydroid." }
        if (gms) { score -= 20; notes += "Memakai Google Play Services: image Waydroid perlu GApps." }
        if (integrity) { score -= 40; notes += "Memakai Play Integrity: kemungkinan besar menolak berjalan di Waydroid." }
        if (notes.isEmpty()) notes += "Tidak ada hambatan yang terdeteksi."

        return ApkReport(
            file = file,
            packageName = info.packageName,
            label = app.loadLabel(pm).toString(),
            versionName = info.versionName,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            abis = abis,
            usesGms = gms,
            usesIntegrity = integrity,
            score = score.coerceAtLeast(0),
            notes = notes,
            iconPng = runCatching { app.loadIcon(pm).toPng() }.getOrNull(),
        )
    }

    private fun Drawable.toPng(): ByteArray {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bmp))
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun InputStream.contains(needle: ByteArray): Boolean {
        val buf = ByteArray(64 * 1024)
        var carry = ByteArray(0)
        while (true) {
            val n = read(buf)
            if (n < 0) return false
            val chunk = carry + buf.copyOf(n)
            if (indexOf(chunk, needle) >= 0) return true
            carry = chunk.copyOfRange(maxOf(0, chunk.size - needle.size + 1), chunk.size)
        }
    }

    private fun indexOf(h: ByteArray, nd: ByteArray): Int {
        outer@ for (i in 0..h.size - nd.size) {
            for (j in nd.indices) if (h[i + j] != nd[j]) continue@outer
            return i
        }
        return
      -1
    }
}
