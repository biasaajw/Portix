# Portix (Android)

Aplikasi open source untuk membantu porting aplikasi ke Linux.

1. **APK → Linux**: analisis APK, lalu ekspor paket `.tar.gz` berisi installer Waydroid + launcher `.desktop`.
2. **Bangun dari source**: analisis repo GitHub, lalu ekspor build kit (`build.sh` + `Dockerfile`) untuk dibangun di Linux.

Buka folder ini di Android Studio (Ladybug atau lebih baru), tunggu Gradle sync, lalu Run.

## Build APK tanpa Android Studio
Unggah proyek ini ke repo GitHub, buka tab **Actions** > **Build APK** > **Run workflow**.
Setelah selesai, unduh artifact `portix-debug-apk` (berisi `app-debug.apk`) lalu pasang di HP.
