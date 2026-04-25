SoNovel Web 服务端 - 使用说明
================================

⚠ 首次使用必须注册管理员账号！


快速启动
--------
Linux:   ./run-linux.sh
Windows: 双击 sonovel.exe

启动后浏览器打开: http://localhost:7765

页面自动跳转到管理员注册页面 → 注册管理员 → 登录 → 进入主界面。


第一次使用
----------
1. 浏览器访问 http://localhost:7765
2. 如果没有管理员，页面自动进入管理员注册
3. 输入管理员用户名和密码完成注册
4. 登录后即可搜索和下载小说
5. 点击右上角"🔑 获取 Token"可为第三方程序创建 API Token


部署到服务器 (Debian / Ubuntu)
------------------------------
curl -sSL https://raw.githubusercontent.com/linlelest/so-novel-web/main/deploy.sh | sudo bash

部署完成后: http://服务器IP/sonovel-web


配置文件 config.ini
-------------------
| 配置项        | 说明               | 默认值     |
|---------------|-------------------|------------|
| download-path | 下载文件保存路径   | downloads  |
| extname       | 默认下载格式       | epub       |
| active-rules  | 激活书源规则文件   | main.json  |
| search-limit  | 搜索结果数量限制   | 30         |
| concurrency   | 并发下载上限       | (自动)     |
| port          | Web 服务端口       | 7765       |
| win_devmode   | Windows调试模式    | 0          |
|               | 0=后台托盘(默认)   |            |
|               | 1=显示控制台窗口   |            |

修改配置后重启应用生效。


API 使用
--------
Token 获取方式：首页右上角"🔑 获取 Token"

第三方程序调用格式:
  http://服务器IP:端口/?token=你的Token

示例:
  http://192.168.1.100:7765/?token=sonovel_xxxxx

完整 API 文档见压缩包内的 API.md。


问题反馈
--------
https://github.com/linlelest/so-novel-web/issues
