<div align="center">
  <img src="assets/logo.png" style="width: 128px;"/>
  <h1 align="center">SoNovel Web</h1>
  <p>基于 <a href="https://github.com/freeok/so-novel">freeok/so-novel</a> 改造的服务端版本</p>
  <p>带用户认证 · API Token · 管理后台 · 一键部署</p>
</div>

---

## 什么是 SoNovel Web？

SoNovel Web 是在原免费开源小说下载器 [SoNovel](https://github.com/freeok/so-novel) 基础上改造成的**服务端版本**。增加了完整的用户认证系统、RESTful API、管理后台，可部署到服务器上对外提供服务。

核心功能与原版一致：**搜索、下载网络小说，导出为 EPUB/TXT/HTML/PDF 格式**。

## 与原版的区别

| 维度 | 原版 | 本改版 |
|------|------|--------|
| 使用方式 | 本地 CLI / TUI / WebUI | 服务端部署 + Web UI |
| 用户系统 | 无 | 注册/登录/角色/封禁 |
| API 接口 | 无 | 完整 RESTful API |
| Token 认证 | 无 | API Token 管理 |
| 管理后台 | 无 | 用户管理 + 公告系统 |
| 数据持久化 | 无 | SQLite 数据库 |
| 部署方式 | 手动运行 | systemd + nginx 一键部署 |

## 快速部署

```bash
# Debian / Ubuntu 一键部署
curl -sSL https://raw.githubusercontent.com/linlelest/so-novel-web/main/deploy.sh | sudo bash
```

部署完成后访问：`http://你的服务器IP/sonovel-web`

首次访问会自动跳转到管理员注册页面。

## 手动使用

```bash
# 1. 下载最新 Release
https://github.com/linlelest/so-novel-web/releases/latest

# 2. 解压后启动
tar xzf sonovel-linux_x64.tar.gz
cd sonovel-linux_x64
./run-linux.sh

# 3. 浏览器打开
http://localhost:7765
```

## API 使用

第三方程序可通过 API Token 调用本服务：

```python
import requests

BASE = "http://your-server:7765"
TOKEN = "sonovel_xxxxx"  # 在 Web UI 右上角获取

# 搜索
r = requests.get(f"{BASE}/search/aggregated", params={"kw":"凡人修仙传","token":TOKEN})
books = r.json()["data"]

# 下载
r = requests.get(f"{BASE}/book-fetch", params={"url": books[0]["url"], "format":"epub", "token":TOKEN})
```

完整 API 文档：见 [API.md](API.md)

## 与上游同步

本项目基于 [freeok/so-novel](https://github.com/freeok/so-novel) 改造。新增代码集中在以下区域，合并上游更新时不受影响：

- `db/` — 数据库层（SQLite）
- `web/AuthFilter.java` — 认证过滤器
- `web/servlet/AuthServlet.java` — 认证 API
- `web/servlet/TokenServlet.java` — Token 管理
- `web/servlet/AdminServlet.java` — 管理员 API
- `web/servlet/HistoryServlet.java` — 下载历史
- `web/servlet/AnnouncementServlet.java` — 公告 API
- `static/login.html` — 登录/注册页面
- `static/admin.html` — 管理后台

同步步骤：
```bash
git remote add upstream https://github.com/freeok/so-novel.git
git fetch upstream
git merge upstream/main    # 冲突仅可能出现在 pom.xml / Main.java / WebServer.java
```

## 开发环境

```bash
# 要求 JDK 21+
git clone https://github.com/linlelest/so-novel-web.git
cd so-novel-web
mvn compile
mvn exec:java
```

## 许可证

基于原项目 [LICENSE](LICENSE) (MIT)

## 免责声明

本项目仅用于学习交流，请勿用于商业用途或批量抓取。使用本工具即表示您同意遵守所有适用法律。
