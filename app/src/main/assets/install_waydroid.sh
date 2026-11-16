#!/bin/sh
# Dibuat oleh Portix: memasang aplikasi Android di Linux lewat Waydroid
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="@@PKG@@"
LABEL="@@LABEL@@"

if ! command -v waydroid >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    echo "Memasang Waydroid (Debian/Ubuntu)..."
    sudo apt-get install -y curl ca-certificates
    curl -s https://repo.waydro.id | sudo bash
    sudo apt-get install -y waydroid
  else
    echo "Waydroid belum terpasang. Ikuti panduan distro Anda: https://docs.waydro.id/usage/install-on-desktops"
    exit 1
  fi
fi

if [ ! -f /var/lib/waydroid/waydroid.cfg ]; then
  if [ "@@GMS@@" = "1" ]; then
    echo "Aplikasi ini memakai Google Play Services, inisialisasi dengan GApps..."
    sudo waydroid init -s GAPPS -f
  else
    sudo waydroid init
  fi
fi

if [ "@@ARM@@" = "1" ]; then
  echo "Catatan: library native hanya ARM. Di PC x86_64 pasang translasi (libhoudini/libndk), mis. via https://github.com/casualsnek/waydroid_script"
fi

sudo systemctl enable --now waydroid-container 2>/dev/null || true
waydroid session start >/dev/null 2>&1 &
for i in $(seq 1 30); do
  waydroid status 2>/dev/null | grep -q RUNNING && break
  sleep 2
done

waydroid app install "$DIR/app.apk"

ICON_DIR="$HOME/.local/share/icons/hicolor/256x256/apps"
mkdir -p "$ICON_DIR" "$HOME/.local/share/applications"
[ -f "$DIR/icon.png" ] && cp "$DIR/icon.png" "$ICON_DIR/$PKG.png"
cat > "$HOME/.local/share/applications/$PKG.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$LABEL
Exec=waydroid app launch $PKG
Icon=$PKG
Categories=Utility;
EOF
echo "Selesai. Cari \"$LABEL\" di menu apli
kasi."
