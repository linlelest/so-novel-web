<div align="center">
  <img src="assets/logo.png" style="width: 128px;"/>
  <h1 align="center">SoNovel Web</h1>
  <p>基于 <a href="https://github.com/freeok/so-novel">freeok/so-novel</a> 改造的服务端版本</p>
  <p>用户认证 · API Token · 管理后台 · 公告系统 · 一键部署 · Windows 托盘</p>
</div>

---

## 概述

SoNovel Web 是在免费开源小说下载器 [SoNovel](https://github.com/freeok/so-novel) 基础上改造的服务端版本。支持搜索、下载网络小说并导出为 EPUB / TXT / HTML / PDF 格式。增加了完整的多用户认证、RESTful API、管理后台和公告系统，可直接部署到服务器对外提供服务。

## 与原版的区别

| 维度 | 原版 | 本改版 |
|------|------|--------|
| 启动方式 | CLI / TUI / WebUI | Web 服务端（Windows 有托盘 GUI） |
| 用户系统 | 无 | 注册 / 登录 / 管理员 / 封禁 / 删除 |
| API 接口 | 无 | 完整 RESTful API（25+ 端点） |
| Token 认证 | 无 | 可创建多个 Token，带备注，一键复制 |
| 管理后台 | 无 | 用户管理 / 公告管理 / 下载日志 / 邀请码 / 系统配置 |
| 公告系统 | 无 | 多条公告，Markdown 渲染，置顶，登录页弹窗 |
| 邀请码系统 | 无 | 批量生成 / 使用次数限制 / 自动消耗 |
| 并发控制 | 无 | 单本线程数量 + 多本同时下载上限 |
| 下载进度 | 无 | SSE 实时推送下载进度（Web + API） |
| 自动清理 | 无 | 下载文件 1 小时后自动删除 |
| 程序更新 | 无 | 管理后台一键检查/下载更新（支持代理） |
| 数据持久化 | 无 | SQLite 数据库 |
| 部署方式 | 手动运行 | systemd + nginx 一键部署脚本 |

## 一键部署 (Debian / Ubuntu)

```bash
curl -sSL https://raw.githubusercontent.com/linlelest/so-novel-web/main/deploy.sh | sudo bash
```

脚本自动完成：安装 JDK21 + nginx → 下载最新 Release → 解压 → 配置 systemd 服务 → 配置 nginx 反向代理 → 启动。

部署后访问：

```
http://你的服务器IP/sonovel-web
```

首次访问自动跳转到管理员注册页面。

## 手动部署 (Linux)

```bash
# 1. 下载最新 Release
wget https://github.com/linlelest/so-novel-web/releases/latest/download/sonovel-linux_x64.tar.gz

# 2. 解压
tar xzf sonovel-linux_x64.tar.gz
cd sonovel-linux_x64

# 3. 启动
./run-linux.sh

# 4. 浏览器访问
# http://localhost:7765
```

## Windows 版本

下载 `sonovel-windows.tar.gz`，解压后双击运行。程序会：

1. 弹窗显示服务地址（本机 IP:端口）
2. 点击"打开网页"自动打开浏览器
3. 关闭弹窗后最小化到系统托盘
4. 右键托盘图标可：打开网页 / 切换开机自启 / 退出

## 使用流程

1. 访问登录页面 → 首次无管理员时自动进入管理员注册
2. 注册管理员账号（密码 ≥ 6 位）
3. 登录后可搜索和下载书籍（下载完成后浏览器自动弹出下载）
4. 管理员在右上角进入"管理后台"，可管理用户、发布公告、生成邀请码、查看下载日志
5. 任意用户可在首页右上角"🔑 Token"创建 API Token 供第三方程序调用
6. 下载的文件在 1 小时后自动从服务器删除

## API 调用
完整 API 文档 → [API.md](API.md)

## 项目结构（改版新增文件）

```
src/main/java/com/pcdd/sonovel/
├── db/                          # SQLite 数据库层
│   ├── DatabaseManager.java
│   ├── UserRepository.java
│   ├── TokenRepository.java
│   ├── HistoryRepository.java
│   └── AnnouncementRepository.java
├── launch/
│   └── WinLauncher.java         # Windows 托盘 GUI
├── model/
│   ├── AuthUser.java
│   ├── ApiToken.java
│   ├── DownloadHistory.java
│   └── Announcement.java
└── web/
    ├── AuthFilter.java          # 认证过滤器
    └── servlet/
        ├── AuthServlet.java
        ├── TokenServlet.java
        ├── AdminServlet.java
        ├── HistoryServlet.java
        └── AnnouncementServlet.java
src/main/resources/static/
├── login.html                   # 登录/注册/管理员注册
└── admin.html                   # 管理后台
```

原版文件仅 `Main.java`、`WebServer.java`、`pom.xml`、`index.html` 有少量修改。
还有大量新增文件用于分发和管理服务，不影响原版功能。

## 与上游同步

Action页自动同步上游项目最新版本规则

## 开发

```bash
git clone https://github.com/linlelest/so-novel-web.git
cd so-novel-web

# 要求 JDK 21+, Maven 3.8+
mvn compile
mvn exec:java
```

## 许可证

MIT · 基于 [freeok/so-novel](https://github.com/freeok/so-novel)

## 友情链接
[LinuxDo](https://linux.do)
