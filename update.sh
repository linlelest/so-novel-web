#!/bin/bash
# SoNovel Web 一键更新脚本
# 由管理员后台触发，下载最新 Release 并替换当前安装
set -e

GITHUB_USER="linlelest"
REPO="so-novel-web"
INSTALL_DIR="$(dirname "$(readlink -f "$0")")"
SERVICE="sonovel.service"
TMP="/tmp/sonovel-update.tar.gz"

echo "🔄 SoNovel Web 自动更新"
echo "   安装目录: ${INSTALL_DIR}"

# 获取最新版本 URL
LATEST_URL=$(curl -s "https://api.github.com/repos/${GITHUB_USER}/${REPO}/releases/latest" \
  | grep "browser_download_url.*sonovel-linux_x64.tar.gz" \
  | cut -d '"' -f 4)

if [ -z "$LATEST_URL" ]; then
  echo "❌ 未找到最新版本"
  exit 1
fi

echo "⬇ 下载: ${LATEST_URL}"
curl -L -o "$TMP" "$LATEST_URL"

echo "🛑 停止服务..."
systemctl stop "$SERVICE" 2>/dev/null || true

echo "📦 解压覆盖..."
tar xzf "$TMP" -C "$INSTALL_DIR" --strip-components=1 --overwrite

echo "🗑 清理..."
rm "$TMP"
mkdir -p "${INSTALL_DIR}/downloads" "${INSTALL_DIR}/logs"

echo "🚀 启动服务..."
systemctl start "$SERVICE" 2>/dev/null || true

echo "✅ 更新完成"
