# SoNovel 服务端 API 文档 v2.0

> 本文档适用于 SoNovel 服务端改版。原版项目的 CLI/TUI 功能不受影响。

---

## 📋 目录

1. [认证方式](#1-认证方式)
2. [用户认证 API](#2-用户认证-api)
3. [Token 管理 API](#3-token-管理-api)
4. [书籍搜索 API](#4-书籍搜索-api)
5. [书籍下载 API](#5-书籍下载-api)
6. [文件下载 API](#6-文件下载-api)
7. [本地书籍列表 API](#7-本地书籍列表-api)
8. [下载历史 API](#8-下载历史-api)
9. [服务器配置 API](#9-服务器配置-api)
10. [管理员 API](#10-管理员-api)
11. [完整调用示例](#11-完整调用示例)

---

## 1. 认证方式

本系统支持两种认证方式：

### 方式一：Session Cookie（Web 浏览器）

登录后自动设置 Cookie，适用于浏览器访问。

### 方式二：API Token（第三方程序）

适用于脚本、第三方程序调用。

**获取 Token**：登录 Web 界面后，在首页右上角点击"🔑 获取 Token"按钮创建。

**使用 Token**：在所有 API 请求的 URL 后追加 `?token=你的Token` 参数。

```
GET http://your-server:7765/search/aggregated?kw=凡人修仙传&token=sonovel_xxxxx
```

### 响应格式

所有 API 统一返回 JSON 格式：

```json
{
  "code": 200,
  "message": "OK",
  "data": { ... }
}
```

错误时：
```json
{
  "code": 401,
  "message": "未登录或 Token 无效",
  "data": null
}
```

| code | 含义 |
|------|------|
| 200  | 成功 |
| 400  | 请求参数错误 |
| 401  | 未认证 / Token 无效 |
| 403  | 权限不足 / 账号被封禁 |
| 404  | 资源不存在 |
| 409  | 资源冲突（如用户名已存在） |
| 500  | 服务器内部错误 |
| 501  | 服务器维护中（维护模式下所有API返回此状态码） |
| 503  | 服务不可用（如速率限制触发） |

---

## 2. 用户认证 API

### 2.1 检查管理员是否存在

```
GET /api/auth/check-admin
```

**无需认证**。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": { "hasAdmin": false }
}
```

### 2.2 管理员注册

```
POST /api/auth/admin-register
Content-Type: application/json
```

**无需认证**。仅首次访问时可用，一旦注册成功不可重复注册。

**请求体：**
```json
{
  "username": "admin",
  "password": "mypassword123"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "sessionId": "abc123...",
    "username": "admin",
    "role": "admin",
    "message": "管理员注册成功"
  }
}
```

### 2.3 用户登录

```
POST /api/auth/login
Content-Type: application/json
```

**无需认证**。

**请求体：**
```json
{
  "username": "myuser",
  "password": "mypassword"
}
```

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "sessionId": "abc123...",
    "username": "myuser",
    "role": "user"
  }
}
```

响应中会设置 `sonovel_session` Cookie。

### 2.4 用户注册

```
POST /api/auth/register
Content-Type: application/json
```

**无需认证**。

**请求体：**
```json
{
  "username": "myuser",
  "password": "mypassword"
}
```

**限制：**
- 用户名长度：2-20 个字符
- 密码长度：不少于 4 个字符

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": { "message": "注册成功" }
}
```

### 2.5 检查登录状态

```
GET /api/auth/check
```

**无需认证**。检查当前 Cookie 是否有效。

**响应示例（已登录）：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "authenticated": true,
    "username": "admin",
    "role": "admin"
  }
}
```

**响应示例（未登录）：**
```json
{
  "code": 200,
  "message": "OK",
  "data": { "authenticated": false }
}
```

### 2.6 登出

```
POST /api/auth/logout
```

**无需认证**。清除当前会话。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": { "message": "已登出" }
}
```

---

## 3. Token 管理 API

所有 Token API **需要认证**（Session Cookie 或 Token 参数）。

### 3.1 获取所有 Token

```
GET /api/tokens
```

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "token": "sonovel_abc123def456",
      "note": "我的下载脚本",
      "createdAt": 1714000000000,
      "lastUsedAt": 1714000000000
    }
  ]
}
```

### 3.2 创建 Token

```
POST /api/tokens
Content-Type: application/json
```

**请求体：**
```json
{
  "note": "我的下载脚本"
}
```

`note` 为可选备注（最长 50 字符）。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "id": 2,
    "userId": 1,
    "token": "sonovel_xyz789",
    "note": "我的下载脚本",
    "createdAt": 1714000000000
  }
}
```

### 3.3 更新 Token 备注

```
PUT /api/tokens
Content-Type: application/json
```

**请求体：**
```json
{
  "id": 1,
  "note": "更新后的备注"
}
```

### 3.4 删除 Token

```
DELETE /api/tokens?id=1
```

---

## 4. 书籍搜索 API

### 4.1 聚合搜索

```
GET /search/aggregated?kw=凡人修仙传&token=xxx
```

**需要认证**（建议使用 Token）。

| 参数 | 必填 | 说明 |
|------|------|------|
| kw | 是 | 搜索关键词（书名或作者） |
| searchLimit | 否 | 每页返回数量，不超过服务器配置限制 |
| token | 是* | API Token（非浏览器访问时必填） |

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "sourceId": 1,
      "sourceName": "起点中文网",
      "url": "https://...",
      "bookName": "凡人修仙传",
      "author": "忘语",
      "latestChapter": "第一千章",
      "lastUpdateTime": "2024-01-01",
      "intro": "..."
    }
  ]
}
```

---

## 5. 书籍下载 API

### 5.1 下载书籍到服务器

```
GET /book-fetch?url=https://...&format=epub&token=xxx
```

**需要认证**（建议使用 Token）。

| 参数 | 必填 | 说明 |
|------|------|------|
| url | 是 | 书籍详情页 URL |
| format | 否 | 下载格式：epub/txt/html/pdf，默认使用服务器配置 |
| language | 否 | 语言：zh_CN/zh_TW/zh_Hant |
| concurrency | 否 | 下载并发数，不超过服务器配置 |
| dlid | 否 | 下载追踪ID（9位数字），不传则自动生成 |
| token | 是* | API Token（非浏览器访问时必填） |

下载完成后，文件保存在服务器下载目录中。返回的 `dlid` 可用于 `/book-download` 接口下载。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "message": "下载完成",
    "timeSeconds": 12.34,
    "dlid": "358291047",
    "fileName": "凡人修仙传(忘语).epub"
  }
}
```

---

## 6. 文件下载 API

### 6.1 从服务器下载文件到本地

```
GET /book-download?dlid=358291047&token=xxx
```

或使用传统文件名方式：

```
GET /book-download?filename=凡人修仙传(忘语).epub&token=xxx
```

**需要认证**（建议使用 Token）。

| 参数 | 必填 | 说明 |
|------|------|------|
| dlid | 二选一 | 下载追踪ID（从 /book-fetch 响应获取，用后即失效） |
| filename | 二选一 | 文件名（从本地书籍列表获取） |
| token | 是* | API Token（非浏览器访问时必填） |

该接口返回二进制文件流。

---

## 7. 本地书籍列表 API

### 7.1 获取已下载书籍列表

```
GET /local-books?token=xxx
```

**需要认证**（建议使用 Token）。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "name": "凡人修仙传 (忘语) EPUB.epub",
      "size": 5242880,
      "timestamp": 1714000000000
    }
  ]
}
```

- `size`: 文件大小（字节）
- `timestamp`: 文件最后修改时间戳（毫秒）

---

## 8. 下载历史 API

### 8.1 获取当前用户的下载历史

```
GET /api/history?token=xxx
```

**需要认证**（建议使用 Token）。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "bookName": "凡人修仙传",
      "author": "忘语",
      "format": "epub",
      "createdAt": 1714000000000
    }
  ]
}
```

---

## 9. 服务器配置 API

### 9.1 获取服务器配置

```
GET /config?token=xxx
```

**需要认证**（建议使用 Token）。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "version": "1.10.1",
    "downloadPath": "./downloads",
    "extName": "epub",
    "concurrency": -1,
    "searchLimit": -1,
    ...
  }
}
```

---

## 10. 管理员 API

所有管理员 API **需要管理员权限**（Session Cookie 或 Token 必须属于管理员账号）。

### 10.1 获取用户列表

```
GET /api/admin/users?token=xxx
```

**需要管理员权限**。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "role": "admin",
      "banned": 0,
      "createdAt": 1714000000000
    },
    {
      "id": 2,
      "username": "user1",
      "role": "user",
      "banned": 0,
      "createdAt": 1714000000000
    }
  ]
}
```

### 10.2 封禁/解封用户

```
POST /api/admin/users
Content-Type: application/json
```

**需要管理员权限**。管理员不能操作自己的账号。

**封禁用户：**
```json
{
  "userId": 2,
  "action": "ban"
}
```

**解封用户：**
```json
{
  "userId": 2,
  "action": "unban"
}
```

### 10.3 获取下载日志（全局）

```
GET /api/admin/logs?token=xxx
```

**需要管理员权限**。

**响应示例：**
```json
{
  "code": 200,
  "message": "OK",
  "data": [
    {
      "id": 1,
      "bookName": "凡人修仙传",
      "author": "忘语",
      "format": "epub",
      "createdAt": 1714000000000
    }
  ]
}
```

---

## 11. 完整调用示例

### Python 示例

```python
import requests

BASE_URL = "http://your-server:7765"
TOKEN = "sonovel_xxxxx"

# 搜索书籍
search_resp = requests.get(
    f"{BASE_URL}/search/aggregated",
    params={"kw": "凡人修仙传", "token": TOKEN}
)
books = search_resp.json()["data"]
print(f"找到 {len(books)} 本书")

# 下载第一本书为 EPUB
if books:
    book = books[0]
    download_resp = requests.get(
        f"{BASE_URL}/book-fetch",
        params={"url": book["url"], "format": "epub", "token": TOKEN}
    )
    result = download_resp.json()
    dlid = result.get("data", {}).get("dlid", "")
    print(f"下载结果: {result}")

    # 用 dlid 下载文件到本地
    if dlid:
        file_resp = requests.get(
            f"{BASE_URL}/book-download",
            params={"dlid": dlid, "token": TOKEN}, stream=True
        )
        with open("downloaded.epub", "wb") as f:
            for chunk in file_resp.iter_content(): f.write(chunk)
        print("文件已保存到本地")

# 查看下载历史
history = requests.get(f"{BASE_URL}/api/history", params={"token": TOKEN})
print(f"下载历史: {history.json()}")
```

### cURL 示例

```bash
# 搜索
curl "http://your-server:7765/search/aggregated?kw=凡人修仙传&token=sonovel_xxxxx"

# 下载
curl "http://your-server:7765/book-fetch?url=https://example.com/book/123&format=epub&token=sonovel_xxxxx"
# 响应包含 dlid，用它下载文件到本地
curl -o 文件.epub "http://your-server:7765/book-download?dlid=358291047&token=sonovel_xxxxx"

# 查看历史
curl "http://your-server:7765/api/history?token=sonovel_xxxxx"

# 管理 - 获取用户列表
curl "http://your-server:7765/api/admin/users?token=sonovel_xxxxx"

# 管理 - 封禁用户
curl -X POST "http://your-server:7765/api/admin/users?token=sonovel_xxxxx" \
  -H "Content-Type: application/json" \
  -d '{"userId": 2, "action": "ban"}'
```

### Token 连接格式

```
# 完整 URL 格式
http://服务器IP:端口/?token=sonovel_xxxxx

# 示例
http://192.168.1.100:7765/?token=sonovel_abc123def456
http://example.com:7765/?token=sonovel_abc123def456
```
