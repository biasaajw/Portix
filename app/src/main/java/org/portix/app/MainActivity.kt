package org.portix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { App() } } }
    }
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("APK → Linux") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bangun dari source") })
        }
        if (tab == 0) ApkTab() else SourceTab()
    }
}

@Composable
fun ApkTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ApkReport?>(null) }
    var status by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = "Menganalisis…"
            val res = withContext(Dispatchers.IO) { runCatching { ApkAnalyzer.analyze(ctx, uri) } }
            res.onSuccess { report = it; status = "" }.onFailure { status = "Gagal: ${it.message}" }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        val r = report
        if (uri != null && r != null) scope.launch {
            status = "Membuat paket…"
            val res = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { LinuxPackager.write(ctx, it, r) } }
            }
            status = res.fold({ "Paket tersimpan. Salin ke Linux lalu jalankan: sh install.sh" }, { "Gagal: ${it.message}" })
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pilih APK, lalu ekspor paket Linux yang menjalankannya lewat Waydroid.")
        Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pilih APK") }
        if (status.isNotEmpty()) Text(status)
        report?.let { r ->
            Text("${r.label} (${r.packageName})", style = MaterialTheme.typography.titleMedium)
            Text("Versi ${r.versionName ?: "-"} · minSdk ${r.minSdk} · targetSdk ${r.targetSdk}")
            Text("ABI: " + r.abis.ifEmpty { setOf("tanpa library native") }.joinToString())
            Text("Skor kompatibilitas Linux: ${r.score}/100", style = MaterialTheme.typography.titleSmall)
            r.notes.forEach { Text("• $it") }
            Button(onClick = { export.launch("${r.packageName}-linux.tar.gz") }) { Text("Ekspor paket Linux") }
        }
    }
}

@Composable
fun SourceTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<SourceReport?>(null) }
    var status by remember { mutableStateOf("") }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        val r = report
        if (uri != null && r != null) scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { SourceKit.write(ctx, it, r) } }
            }
            status = res.fold({ "Build kit tersimpan. Salin ke Linux lalu jalankan: sh build.sh" }, { "Gagal: ${it.message}" })
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Masukkan URL repo GitHub. Portix mendeteksi sistem build dan membuat build kit untuk Linux.")
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text("https://github.com/pemilik/repo") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            scope.launch {
                status = "Menganalisis…"
                val res = withContext(Dispatchers.IO) { runCatching { SourceAnalyzer.analyze(url.trim()) } }
                res.onSuccess { report = it; status = "" }.onFailure { report = null; status = "Gagal: ${it.message}" }
            }
        }) { Text("Analisis repo") }
        if (status.isNotEmpty()) Text(status)
        report?.let { r ->
            Text("${r.owner}/${r.repo}", style = MaterialTheme.typography.titleMedium)
            r.description?.let { Text(it) }
            Text("Branch: ${r.branch} · Lisensi: ${r.license ?: "-"}")
            Text("Sistem build: ${r.system ?: "tidak dikenali"}")
            r.notes.forEach { Text("• $it") }
            Button(onClick = { export.launch("${r.repo}-linux-kit.tar.gz") }) { Text("Ekspor build kit") }
        }
    }
}
