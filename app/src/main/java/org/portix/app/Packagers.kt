package org.portix.app

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

private const val MODE_FILE = 420  // 0644
private const val MODE_EXEC = 493  // 0755

class TarBuilder(out: OutputStream) : Closeable {
    private val tar = TarArchiveOutputStream(GZIPOutputStream(out)).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    }

    fun add(name: String, bytes: ByteArray, mode: Int = MODE_FILE) {
        val e = TarArchiveEntry(name)
        e.size = bytes.size.toLong()
        e.mode = mode
        tar.putArchiveEntry(e)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    fun add(name: String, file: File, mode: Int = MODE_FILE) {
        val e = TarArchiveEntry(name)
        e.size = file.length()
        e.mode = mode
        tar.putArchiveEntry(e)
        file.inputStream().use { it.copyTo(tar) }
        tar.closeArchiveEntry()
    }

    override fun close() = tar.close()
}

private fun Context.asset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

/** Fitur 1: APK -> paket Linux (installer Waydroid + launcher .desktop). */
object LinuxPackager {
    fun write(ctx: Context, out: OutputStream, r: ApkReport) {
        val label = r.label.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").ifBlank { r.packageName }
        val script = ctx.asset("install_waydroid.sh")
            .replace("@@PKG@@", r.packageName)
            .replace("@@LABEL@@", label)
            .replace("@@GMS@@", if (r.usesGms) "1" else "0")
            .replace("@@ARM@@", if (r.armOnly) "1" else "0")
        val dir = r.packageName
        TarBuilder(out).use { t ->
            t.add("$dir/install.sh", script.toByteArray(), MODE_EXEC)
            t.add("$dir/app.apk", r.file)
            r.iconPng?.let { t.add("$dir/icon.png", it) }
            t.add("$dir/README.txt", (
                "Paket Linux untuk $label ($dir)\n\n" +
                "1. Ekstrak arsip ini di komputer Linux (sesi Wayland disarankan).\n" +
                "2. Jalankan: sh install.sh\n" +
                "3. Aplikasi muncul di menu aplikasi, dijalankan lewat Waydroid.\n\n" +
                "Dibuat oleh Portix. Gunakan hanya APK milik Anda sendiri.\n"
            ).toByteArray())
        }
    }
}

/** Fitur 2: build kit untuk membangun ulang source (mis. dari GitHub) di Linux. */
object SourceKit {
    fun write(ctx: Context, out: OutputStream, r: SourceReport) {
        val build = ctx.asset("build_kit/build.sh")
            .replace("@@REPO_URL@@", r.cloneUrl)
            .replace("@@BRANCH@@", r.branch)
            .replace("@@SYSTEM@@", r.system ?: "unknown")
        val dir = "${r.repo}-linux-kit"
        TarBuilder(out).use { t ->
            t.add("$dir/build.sh", build.toByteArray(), MODE_EXEC)
            t.add("$dir/Dockerfile", ctx.asset("build_kit/Dockerfile").toByteArray())
            t.add("$dir/README.txt", (
                "Build kit Linux untuk ${r.owner}/${r.repo} (sistem build: ${r.system ?: "tidak dikenali"})\n\n" +
                "Langsung di Linux:   sh build.sh\n" +
                "Lewat Docker:        docker build -t portix-build . && " +
                "docker run --rm -v \"\$PWD/dist:/work/dist\" portix-build\n\n" +
                "Hasil build ada di folder dist/. Skrip ini titik awal; sesuaikan bila proyek butuh langkah khusus.\n" +
                "Periksa lisensi proyek sebelum mendistribusikan ulang hasilnya.\n"
            ).toByteArray())
        }
    }
}
