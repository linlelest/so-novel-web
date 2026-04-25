#!/bin/bash
set -e
# ============================================
# Windows 发布脚本 (x64 | arm64)
# 用法: ./release-windows.sh [ARCH]
# 默认: x64
# ============================================

ARCH="${1:-x64}"

if [ "$ARCH" = "x64" ]; then
    JRE_FILENAME="jre-21.0.8+9-windows_x64.zip"
    DOWNLOAD_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jre_x64_windows_hotspot_21.0.8_9.zip"
    MAVEN_PROFILE="windows-x64"
elif [ "$ARCH" = "arm64" ]; then
    JRE_FILENAME="jre-21.0.8+9-windows_aarch64.zip"
    DOWNLOAD_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jre_aarch64_windows_hotspot_21.0.8_9.zip"
    MAVEN_PROFILE="windows-x64"
else
    echo "❌ 不支持的架构: $ARCH (可选: x64, arm64)"; exit 1
fi

JRE_DIRNAME="jdk-21.0.8+9-jre"
JRE_PATH="bundle/$JRE_FILENAME"
DIST_FILENAME="sonovel-windows_${ARCH}.tar.gz"
DIST_DIRNAME="SoNovel_${ARCH}"
PROJECT_PATH="$( cd "$(dirname "$0")"/.. && pwd )"
DIST_PATH="$PROJECT_PATH/dist"
TARGET_DIR="$PROJECT_PATH/target/$DIST_DIRNAME"

download_jre() {
    if [ -f "$JRE_PATH" ]; then echo "$JRE_FILENAME 已存在，无需下载。"
    else echo "$JRE_FILENAME 不存在，开始下载..."
        curl --retry 3 -C - -L -o "$JRE_PATH" "$DOWNLOAD_URL"
        if [ $? -eq 0 ]; then echo "下载完成"
        else echo "下载失败"; exit 1; fi
    fi
}
run_maven() { mvn clean package -P"$MAVEN_PROFILE" '-Dmaven.test.skip=true' '-DjrePath=runtime'; }
copy_files() {
    mkdir -p "$TARGET_DIR"
    cp "bundle/$JRE_FILENAME" "$TARGET_DIR"
    cp "target/app-jar-with-dependencies.jar" "$TARGET_DIR/app.jar"
    # 复制 launch4j 生成的 exe（pom.xml 输出到 target/SoNovel/sonovel.exe）
    cp "target/SoNovel/sonovel.exe" "$TARGET_DIR/sonovel.exe" 2>/dev/null || true
    cp -r bundle/rules "$TARGET_DIR/"
    cp bundle/config.ini bundle/sonovel.l4j.ini bundle/readme.txt "$TARGET_DIR"
    cp API.md "$TARGET_DIR"
    echo "SoNovel 服务端改版 - Windows ${ARCH} 版 (双击 sonovel.exe 启动)" > "$TARGET_DIR/使用说明.txt"
}
extract_jre() { cd "$TARGET_DIR"; unzip -q "$JRE_FILENAME"; mv "$JRE_DIRNAME" runtime; rm "$JRE_FILENAME"; }
package_artifacts() { mkdir -p "$DIST_PATH"; cd "$PROJECT_PATH/target"; tar czf "$DIST_FILENAME" "$DIST_DIRNAME"; mv "$DIST_FILENAME" "$DIST_PATH"; }

main() {
    echo "🏗️ 开始构建 Windows ${ARCH}..."
    cd "$PROJECT_PATH"
    download_jre; run_maven; copy_files; extract_jre; package_artifacts
    echo "✅ Windows ${ARCH} 构建完成！产物: $DIST_FILENAME"
}
main