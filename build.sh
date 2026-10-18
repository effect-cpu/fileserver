#!/bin/bash
set -e
cd "$(dirname "$0")"

ROOT="$(pwd)"
BT="${BT_DIR:-$ROOT/android-14}"
AJ="${ANDROID_JAR:-$ROOT/android-34/android.jar}"
PJ="$ROOT/project"
OUT="$ROOT/out"
KS="${KEYSTORE:-$ROOT/debug.keystore}"

for f in "$AJ" "$BT/aapt" "$BT/apksigner"; do
    if [ ! -e "$f" ]; then
        echo "❌ 缺少构建工具: $f"
        echo "   请把 Android build-tools 放到 $BT,android.jar 放到 $AJ,"
        echo "   或用环境变量指定:BT_DIR=... ANDROID_JAR=... KEYSTORE=... ./build.sh"
        exit 1
    fi
done
[ -f "$KS" ] || { echo "❌ 缺少签名密钥: $KS"; exit 1; }

mkdir -p "$OUT"
rm -rf "$ROOT/build" && mkdir -p "$ROOT/build/gen" "$ROOT/build/classes" "$ROOT/build/dex"

PKG=$(grep -oP 'package="\K[^"]+' "$PJ/AndroidManifest.xml")
echo "[1/5] 包名: $PKG"

echo "[2/5] 生成 R.java ..."
aapt package -f -m -J "$ROOT/build/gen" -M "$PJ/AndroidManifest.xml" -S "$PJ/res" -A "$PJ/assets" -I "$AJ"

echo "[3/5] 编译 Java ..."
javac -source 1.8 -target 1.8 -nowarn -encoding UTF-8 \
    -bootclasspath "$AJ" -classpath "$AJ" \
    -d "$ROOT/build/classes" \
    $(find "$PJ/src" -name '*.java') \
    "$ROOT/build/gen/$(echo "$PKG" | tr '.' '/')/R.java"

echo "[4/5] 转换 dex ..."
DEXER="$BT/lib/d8.jar"
[ -f /workspace/r8-8.9.35.jar ] && DEXER=/workspace/r8-8.9.35.jar
echo "      使用: $(basename "$DEXER")"
java -cp "$DEXER" com.android.tools.r8.D8 --lib "$AJ" --release --min-api 24 \
    --output "$ROOT/build/dex" $(find "$ROOT/build/classes" -name '*.class')

echo "[5/5] 打包 + 对齐 + 签名 ..."
aapt package -f -M "$PJ/AndroidManifest.xml" -S "$PJ/res" -A "$PJ/assets" -I "$AJ" -F "$OUT/unsigned.apk"
python3 - "$OUT/unsigned.apk" "$ROOT/build/dex/classes.dex" << 'PY'
import os, shutil, sys, zipfile
src, dex = sys.argv[1], sys.argv[2]
dst = src.replace('unsigned.apk', 'unsigned-withdex.apk')
shutil.copy(src, dst)
with zipfile.ZipFile(dst, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    print('   dex 已加入, 大小: %d 字节' % os.path.getsize(dex))
PY
zipalign -f 4 "$OUT/unsigned-withdex.apk" "$OUT/aligned.apk"

"$BT/apksigner" sign --ks "$KS" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --v1-signer-name CERT \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT/app-release.apk" "$OUT/aligned.apk"

echo ""
echo "✅ 编译完成: $OUT/app-release.apk"
ls -la "$OUT/app-release.apk"
"$BT/apksigner" verify --verbose "$OUT/app-release.apk" | head -4
