#!/bin/sh
# Dibuat oleh Portix: membangun ulang source untuk Linux
set -e
REPO="@@REPO_URL@@"
BRANCH="@@BRANCH@@"
SYSTEM="@@SYSTEM@@"
OUT="$(pwd)/dist"
mkdir -p "$OUT"

[ -d src ] || git clone --depth 1 --branch "$BRANCH" "$REPO" src
cd src

case "$SYSTEM" in
  cmake)   cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j"$(nproc)" && cp -r build "$OUT/" ;;
  meson)   meson setup build && ninja -C build && cp -r build "$OUT/" ;;
  cargo)   cargo build --release && cp -r target/release "$OUT/" ;;
  go)      go build -o "$OUT/" ./... ;;
  flutter) flutter config --enable-linux-desktop && flutter pub get && flutter build linux --release && cp -r build/linux "$OUT/" ;;
  node)    (npm ci || npm install) && npm run build --if-present && cp -r . "$OUT/app" ;;
  gradle)  if [ -x ./gradlew ]; then ./gradlew build; else gradle build; fi && cp -r build "$OUT/" ;;
  maven)   mvn -B package && cp -r target "$OUT/" ;;
  make)    make -j"$(nproc)" && make install DESTDIR="$OUT" ;;
  python)  pip install --prefix "$OUT" . ;;
  *)       echo "Sistem build tidak dikenali. Baca README proyek untuk cara membangunnya."; exit 1 ;;
esac
echo "Selesai. Hasil ada di: $OU
T"
