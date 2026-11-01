# 文件服务器 · File Server

一款轻量的 Android 局域网文件服务器
A lightweight LAN file server for Android

**语言 / Language:[中文](#中文)&nbsp;&nbsp;·&nbsp;&nbsp;[English](#english)**

---

# 中文

##  功能

| 分类 | 说明 |
|---|---|
| **选目录** | 调用系统文件选择器, 保证您数据的安全 |
| **本地服务器** | HTTP/1.1 服务器,**IPv4 + IPv6 双栈**,端口可改(1024–65535) |
| **网站映射** | 所选目录即网站根目录;有 `index.html` 自动作为首页,可开关目录浏览 |
| **访问地址** | 自动列出本机局域网地址,点击即复制,**二维码可展开扫码** |
| **实时日志** | `yyyy-MM-dd HH:mm:ss \| IP \| 方法 \| 路径 \| 状态码 原因`,实时刷新 |
| **HTTPS** | 一键开启,运行时生成**自签名 X.509 证书**(RSA 2048 / SHA-256) |
| **反爬虫** | UA 特征库(21 条)+ 10 秒 120 次限流,命中返回 403 / 429 |
| **验证码** | 4 位随机字符验证码,通过后下发 cookie 才放行 |
| **封禁 IP** | 精确或通配(`192.168.1.*`),列表可增删 |
| **主题** | 浅色 / 深色 / 跟随系统 |
| **多语言** | 简体中文 / English / 跟随系统 |

## 短板
1. 目前仅支持**静态网页**，暂不支持**python**、**nodejs**等后端
2. 不支持自定义反爬虫频率限制
3. 不支持设置是否支持多线程并发/下载，以及不支持设置最大线程数、超过处理（例如封ip、限速等）
> 以上这些后来会慢慢支持

## 端口设置1024~65535原因
1. 0–1023：知名端口（特权端口，80、443、22、3306 这类）
​
2. 1024–49151：注册端口（自己写服务、控制器、RTSP、自定义TCP服务最常用）
​
3. 49152–65535：临时端口（默认给客户端出站连接用）
作者采用了第二种及第三种情况，因为本软件仅用来本地开个临时小服务，不用来开放正式网站
> 不建议改成无限制端口

##  快速开始

1. 安装 APK(允许「安装未知应用」)
2. 首次打开阅读「使用方法与用户协议」→ 点**同意**
3. 点 **选择文件夹**,选中要共享的目录
4. 点 **启动**
5. 同一局域网内的设备用浏览器打开该地址,或直接扫码

##  设置项

| 项 | 作用 |
|---|---|
| 显示 IPv6 地址 | 隐藏/显示 IPv6 那一行 |
| 允许文件浏览 | 目录下无 `index.html` 时是否展示文件列表;关闭则直接 **403** |
| 反爬虫 | UA 特征 + 频率限制,拦默认脚本/扫描器 |
| 验证码验证 | 未通过验证码前不发文件,挡住伪造 UA 的机器人 |
| 服务端口 | 1024–65535,修改后自动重启服务 |
| HTTPS 加密 | 自签名证书,浏览器会提示「不受信任」(固有现象) |
| 封禁 IP | 精确 / 通配规则,可随时解除 |
| 主题模式 | 跟随系统 / 浅色 / 深色 |
| 语言 | 跟随系统 / 简体中文 / English |
> 反爬虫目前不支持自定义频率限制

## 注意事项

1. **仅限局域网使用。** 请勿把它对外开放(家庭宽带上行不允许,具体可查看您所在国家的法规),也请不要通过端口映射 / 内网穿透暴露到公网。
2. **HTTPS 用自签名证书**,浏览器一定会提示「不受信任」,这是自签名证书的固有现象。
3. **IPv6 能否访问**取决于运营商是否下发、路由器是否开启 IPv6;`fe80::` 链路本地地址不会被显示(无法跨设备使用)。
4. 封禁 IP / 反爬虫 / 验证码都是「提高门槛」,不是强认证，真要挡得住需要用验证码 + 行为限制组合。
> 后面会加强校验、限制
5. 服务器默认跟随 App 进程运行(带前台服务保活),系统回收进程后服务即停止。

---

# English

## Features

| Area | Details |
|---|---|
| **Folder picker** | Uses the system file picker ,ensure the security of your data |
| **Local server** | HTTP/1.1 server with **IPv4 + IPv6 dual-stack**; the port is configurable (1024–65535) |
| **Folder → website** | The chosen folder becomes the web root; `index.html` is served automatically, directory listing can be toggled |
| **Access addresses** | Lists the device's LAN addresses automatically; tap to copy, **tap the QR icon to expand a scannable code** |
| **Live log** | `yyyy-MM-dd HH:mm:ss \| IP \| method \| path \| status reason`, refreshed in real time |
| **HTTPS** | One switch; a **self-signed X.509 certificate** is generated at runtime (RSA 2048 / SHA-256) |
| **Anti-crawler** | 21-entry User-Agent signature list + a 120-requests-per-10 s rate limit → 403 / 429 |
| **CAPTCHA** | 4-character challenge; a cookie is issued only after passing it |
| **IP banning** | Exact or wildcard (`192.168.1.*`), with add/remove list |
| **Theme** | Light / dark / follow system |
| **Languages** | 简体中文 / English / follow system — |

## Limitations
1. Currently, it only supports **static web pages**; backends such as **Python** and **Node.js** are not supported yet.
2. It does not support customizing the anti-crawling frequency limit.
3. It does not support setting whether multi-threaded concurrency/downloading is allowed, nor does it support setting the maximum number of threads or implementing over-limit handling (e.g., IP blocking, rate limiting, etc.).
> These features will be gradually added in the future.

## Reasons for port settings 1024–65535
1. 0–1023: Well-known ports (privileged ports, such as 80, 443, 22, 3306, etc.)
​
2. 1024–49151: Registered ports (most commonly used for writing custom services, controllers, RTSP, or custom TCP services)
​
3. 49152–65535: Ephemeral ports (by default used for outbound connections from clients)
The author utilized the second and third categories, as this software is intended solely for running a temporary local service, not for hosting a formal website.
> It is not recommended to change to an unrestricted port

##  Quick start

1. Install the APK (allow "install unknown apps")
2. Read the Usage & User Agreement on first launch → tap **Agree**
3. Tap **Choose folder** and pick the folder you want to share
4. Tap **Start**
5. Open that address from any device on the same network, or scan the code

##  Settings

| Item | Effect |
|---|---|
| Show IPv6 address | Hide / show the IPv6 row |
| Allow directory listing | Show a file list when there is no `index.html`; when off, return **403** |
| Anti-crawler | UA signatures + rate limit, stops default scripts and scanners |
| CAPTCHA | No file is served before passing the challenge; stops bots with spoofed UAs |
| Port | 1024–65535; the server restarts automatically when changed |
| HTTPS | Self-signed certificate; browsers will warn "not trusted" (inherent to self-signing) |
| Banned IPs | Exact / wildcard rules, removable at any time |
| Theme | Follow system / light / dark |
| Language | Follow system / 简体中文 / English |
> Anti-crawling currently does not support custom frequency limits

## Notes

1. **LAN only.** Do not expose this to the public internet (home broadband does not allow inbound
   service;For specific details, please refer to the regulations in your country. ), and do not publish it through port forwarding or tunnels.
2. **HTTPS uses a self-signed certificate**, so browsers will always warn that it is untrusted —
   that is inherent to self-signing, not a defect.
3. **IPv6 reachability** depends on your ISP and router; `fe80::` link-local addresses are filtered
   out because they cannot be used across devices.
4. IP banning, anti-crawler and CAPTCHA only **raise the bar** — they are not authentication. For real
   protection combine the CAPTCHA with behavioural limits.
> Later, validation and restrictions will be enhanced.
5. The server runs inside the app process (kept alive by a foreground service); if the system kills
   the process, the server stops.
