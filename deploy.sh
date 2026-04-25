#!/bin/bash
set -e
# =============================================================================
# SoNovel Web 一键部署脚本 (Debian / Ubuntu)
# 用法: curl -sSL https://raw.githubusercontent.com/linlelest/so-novel-web/main/deploy.sh | sudo bash
# =============================================================================

GITHUB_USER="linlelest"
REPO="so-novel-web"
APP_NAME="sonovel"
INSTALL_DIR="/opt/${APP_NAME}"
NGINX_AVAILABLE="/etc/nginx/sites-available/${APP_NAME}"
NGINX_ENABLED="/etc/nginx/sites-enabled/${APP_NAME}"
NGINX_DEFAULT="/etc/nginx/sites-enabled/default"
SERVICE_NAME="${APP_NAME}.service"
SYSTEMD_PATH="/etc/systemd/system/${SERVICE_NAME}"
WEBPATH="/sonovel-web"

echo "============================================"
echo " SoNovel Web 一键部署脚本"
echo "============================================"

# 检查 root
if [ "$(id -u)" -ne 0 ]; then
    echo "❌ 请使用 sudo 运行此脚本"
    exit 1
fi

# 检测系统
. /etc/os-release
if [ "$ID" != "debian" ] && [ "$ID" != "ubuntu" ]; then
    echo "⚠️ 此脚本仅针对 Debian/Ubuntu 设计，当前系统: $ID"
    echo "   按 Ctrl+C 取消，或等待 5 秒继续..."
    sleep 5
fi

# 安装依赖
echo ""
echo "📦 [1/5] 安装依赖..."
apt-get update -qq
if ! command -v java &>/dev/null; then
    echo "  → 安装 JDK 21..."
    apt-get install -y -qq openjdk-21-jre-headless
fi
if ! command -v unzip &>/dev/null; then apt-get install -y -qq unzip; fi
if ! command -v curl &>/dev/null; then apt-get install -y -qq curl; fi
if ! command -v nginx &>/dev/null; then apt-get install -y -qq nginx; fi

echo "  ✔ 依赖已就绪"

# 获取最新版本
echo ""
echo "🔍 [2/5] 获取最新版本信息..."
LATEST_URL=$(curl -s "https://api.github.com/repos/${GITHUB_USER}/${REPO}/releases/latest" \
    | grep "browser_download_url.*sonovel-linux_x64.tar.gz" \
    | cut -d '"' -f 4)
LATEST_TAG=$(curl -s "https://api.github.com/repos/${GITHUB_USER}/${REPO}/releases/latest" \
    | grep '"tag_name"' | cut -d '"' -f 4)

if [ -z "$LATEST_URL" ]; then
    echo "❌ 未找到发行版，请确认仓库已创建 Release"
    exit 1
fi
echo "  ✔ 版本: ${LATEST_TAG}"

# 停止旧服务
echo ""
echo "🛑 [3/5] 停止旧服务..."
systemctl stop "$SERVICE_NAME" 2>/dev/null || true

# 下载并解压
echo ""
echo "⬇ [4/5] 下载并安装..."
rm -rf "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
TMP_FILE="/tmp/sonovel.tar.gz"
curl -L -o "$TMP_FILE" "$LATEST_URL"
tar xzf "$TMP_FILE" -C "$INSTALL_DIR" --strip-components=1
rm "$TMP_FILE"

# 创建数据目录
mkdir -p "${INSTALL_DIR}/downloads" "${INSTALL_DIR}/logs"

# 确保 config.ini web port 存在
if [ -f "${INSTALL_DIR}/config.ini" ]; then
    PORT=$(grep -oP '^port\s*=\s*\K\d+' "${INSTALL_DIR}/config.ini" 2>/dev/null || echo "7765")
else
    PORT=7765
fi

echo "  ✔ 安装完成: ${INSTALL_DIR}"
echo "  ✔ 服务端口: ${PORT}"

# 创建 systemd 服务
echo ""
echo "⚙ [5/5] 配置 systemd 和 Nginx..."
cat > "$SYSTEMD_PATH" << EOF
[Unit]
Description=SoNovel Web Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=${INSTALL_DIR}
ExecStart=${INSTALL_DIR}/runtime/bin/java \\
  -XX:+UseZGC \\
  -XX:+ZGenerational \\
  -Dfile.encoding=UTF-8 \\
  -Duser.timezone=GMT+08 \\
  -jar ${INSTALL_DIR}/app.jar
ExecStop=/bin/kill -15 \$MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Nginx 配置 — 使用 sites-available + symlink 标准做法
# 删除默认站点，避免 80 端口冲突
rm -f "$NGINX_DEFAULT" "$NGINX_ENABLED"

cat > "$NGINX_AVAILABLE" << EOF
server {
    listen 80;
    server_name _;

    # /sonovel-web 不带斜杠也匹配
    location ${WEBPATH} {
        proxy_pass http://127.0.0.1:${PORT}/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
        client_max_body_size 50m;
    }
}
EOF

ln -sf "$NGINX_AVAILABLE" "$NGINX_ENABLED"

# 启动服务
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl start "$SERVICE_NAME"

# 重载 nginx
if nginx -t 2>/dev/null; then
    systemctl reload nginx
fi

# 检查状态
sleep 2
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo ""
    echo "============================================"
    echo " ✅ SoNovel Web 部署成功！"
    echo ""
    echo " 🌐 访问地址:"
    echo "    http://$(hostname -I | awk '{print $1}')${WEBPATH}"
    echo ""
    echo " 📍 安装目录: ${INSTALL_DIR}"
    echo " 📋 下载目录: ${INSTALL_DIR}/downloads"
    echo " 🔧 配置文件: ${INSTALL_DIR}/config.ini"
    echo ""
    echo " 💡 常用命令:"
    echo "    systemctl status ${SERVICE_NAME}   # 查看状态"
    echo "    systemctl restart ${SERVICE_NAME}  # 重启服务"
    echo "    journalctl -u ${SERVICE_NAME} -f   # 查看日志"
    echo "    tail -f ${INSTALL_DIR}/logs/*.log  # 下载日志"
    echo ""
    echo " 🌐 首次访问请注册管理员账号！"
    echo "============================================"
else
    echo ""
    echo "❌ 服务启动失败，请检查日志:"
    echo "   journalctl -u ${SERVICE_NAME} -n 30"
    exit 1
fi
