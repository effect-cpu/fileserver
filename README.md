# 文件服务器 · File Server

一款轻量的 Android 局域网文件服务器 —— 选一个文件夹,点一下就变成网站,手机扫码即可访问
A lightweight LAN file server for Android — pick a folder, tap once, and it becomes a website.

**约 119 KB · 零第三方库 · 纯 Android framework · Android 7.0+ (minSdk 24)**

```
同一 Wi-Fi 下的设备 ──►  http://192.168.1.5:8080      ← 手机就是服务器
                      └►  http://[2408:xxxx::1]:8080  ← IPv6 双栈
```

**语言 / Language:[中文](#中文)&nbsp;&nbsp;·&nbsp;&nbsp;[English](#english)**

---

# 中文

## ✨ 功能

| 分类 | 说明 |
|---|---|
| **选目录** | 调用系统文件选择器(SAF),权限持久化,重启 App 仍记得 |
| **本地服务器** | 自研 HTTP/1.1 服务器,**IPv4 + IPv6 双栈监听**,端口可改(1024–65535) |
| **网站映射** | 所选目录即网站根目录;有 `index.html` 自动作为首页,可开关目录浏览 |
| **访问地址** | 自动列出本机局域网地址,点击即复制,**二维码可展开扫码** |
| **实时日志** | `yyyy-MM-dd HH:mm:ss \| IP \| 方法 \| 路径 \| 状态码 原因`,实时刷新 |
| **HTTPS** | 一键开启,运行时生成**自签名 X.509 证书**(RSA 2048 / SHA-256) |
| **反爬虫** | UA 特征库(21 条)+ 10 秒 120 次限流,命中返回 403 / 429 |
| **验证码** | 4 位随机字符验证码,通过后下发 cookie 才放行 |
| **封禁 IP** | 精确或通配(`192.168.1.*`),列表可增删 |
| **主题** | 浅色 / 深色 / 跟随系统,切换带淡入淡出 |
| **多语言** | 简体中文 / English / 跟随系统,协议文本也随语言切换 |
| **其他** | 查看更新(云端 JSON)、查看开发者、查看用户协议(markdown 渲染) |

## 🚀 快速开始

1. 安装 APK(允许「安装未知应用」)
2. 首次打开阅读「使用方法与用户协议」→ 点**同意**
3. 点 **选择文件夹**,选中要共享的目录
4. 点 **启动**,上方会出现 IPv4 / IPv6 地址与二维码
5. 同一局域网内的设备用浏览器打开该地址,或直接扫码

## 🔧 设置项

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
| 查看更新 | 读取云端 `version2.json`,有新版可跳转下载页 |
| 查看用户协议 | 只读弹窗,markdown 渲染 |
| 前往开发者仓库 | 跳转本项目仓库 |

## 🛠 自行构建

仓库只包含源码,**不含** Android SDK 与构建工具。编译需要:

- `android.jar`(API 34)
- Android build-tools(`aapt`、`zipalign`、`apksigner`、`d8`)
- JDK 8+(本项目用 JDK 17 编译,`-source/-target 1.8`)

把工具就位后(脚本默认约定放在 `android-14/` 与 `android-34/android.jar`):

```bash
./build.sh
# 产物:out/app-release.apk
```

> 构建脚本用 **debug keystore** 签名,仅供自用/测试;上架需换正式签名。

## 📄 云端配置文件(可选)

「查看更新」会读取仓库根目录的 `version2.json`(不放也能用,只是没有更新提示):

```json
{
  "version": "1.1",
  "versionCode": 2,
  "content": "## 更新内容\n\n1. 新增 xxx\n2. 修复 **若干问题**",
  "downloadUrl": "https://github.com/effect-cpu/fileserver/releases"
}
```

`content` 支持 Markdown。键名容错:版本名可用 `version` / `versionName` / `ver` / `tag`,
编号可用 `versionCode` / `code` / `build`,下载地址可用 `downloadUrl` / `apkUrl` / `url`。

## ⚠️ 注意事项

1. **仅限局域网使用。** 请勿把它对外开放(家庭宽带上行不允许,企业宽带除外),也请不要通过端口映射 / 内网穿透暴露到公网。
2. **HTTPS 用自签名证书**,浏览器一定会提示「不受信任」,这是自签名证书的固有现象。
3. **IPv6 能否访问**取决于运营商是否下发、路由器是否开启 IPv6;`fe80::` 链路本地地址不会被显示(无法跨设备使用)。
4. 封禁 IP / 反爬虫 / 验证码都是「提高门槛」,不是强认证 —— 真要挡得住需要用验证码 + 行为限制组合。
5. 服务器默认跟随 App 进程运行(带前台服务保活),系统回收进程后服务即停止。

## 📜 许可

尚未指定开源许可证。若需正式开源,请补充 `LICENSE`(如 MIT / Apache-2.0)。

---

# English

## ✨ Features

| Area | Details |
|---|---|
| **Folder picker** | Uses the system file picker (SAF); the permission is persisted, so the folder is remembered after a restart |
| **Local server** | Hand-written HTTP/1.1 server with **IPv4 + IPv6 dual-stack listening**; the port is configurable (1024–65535) |
| **Folder → website** | The chosen folder becomes the web root; `index.html` is served automatically, directory listing can be toggled |
| **Access addresses** | Lists the device's LAN addresses automatically; tap to copy, **tap the QR icon to expand a scannable code** |
| **Live log** | `yyyy-MM-dd HH:mm:ss \| IP \| method \| path \| status reason`, refreshed in real time |
| **HTTPS** | One switch; a **self-signed X.509 certificate** is generated at runtime (RSA 2048 / SHA-256) |
| **Anti-crawler** | 21-entry User-Agent signature list + a 120-requests-per-10 s rate limit → 403 / 429 |
| **CAPTCHA** | 4-character challenge; a cookie is issued only after passing it |
| **IP banning** | Exact or wildcard (`192.168.1.*`), with add/remove list |
| **Theme** | Light / dark / follow system, with a cross-fade when switching |
| **Languages** | 简体中文 / English / follow system — **the bundled agreement switches too** |
| **Extras** | Check for updates (remote JSON), developer info, user agreement (Markdown rendered) |

## 🚀 Quick start

1. Install the APK (allow "install unknown apps")
2. Read the Usage & User Agreement on first launch → tap **Agree**
3. Tap **Choose folder** and pick the folder you want to share
4. Tap **Start**; the IPv4 / IPv6 addresses and a QR code appear
5. Open that address from any device on the same network, or scan the code

## 🔧 Settings

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
| Check updates | Reads `version2.json` from the cloud; opens the release page when a newer version exists |
| User agreement | Read-only dialog, Markdown rendered |
| Developer repository | Opens this repository |

## 🛠 Building it yourself

The repository contains **only sources** — no Android SDK or build tools. You need:

- `android.jar` (API 34)
- Android build-tools (`aapt`, `zipalign`, `apksigner`, `d8`)
- JDK 8+ (this project compiles with JDK 17, `-source/-target 1.8`)

By default the script expects the tools in `android-14/` and `android-34/android.jar`:

```bash
./build.sh
# output: out/app-release.apk
```

Paths can be overridden:

```bash
BT_DIR=/path/to/build-tools ANDROID_JAR=/path/to/android.jar KEYSTORE=my.jks ./build.sh
```

> The script signs with the bundled **debug keystore** — fine for personal use and testing;
> use a proper release key before publishing anywhere.

## 📄 Remote config (optional)

"Check updates" reads `version2.json` from the repository root (optional — without it the app
simply shows no update prompt):

```json
{
  "version": "1.1",
  "versionCode": 2,
  "content": "## What's new\n\n1. Added xxx\n2. Fixed **several issues**",
  "downloadUrl": "https://github.com/effect-cpu/fileserver/releases"
}
```

`content` supports Markdown. Key names are tolerant: version name accepts
`version` / `versionName` / `ver` / `tag`, build number accepts `versionCode` / `code` / `build`,
download URL accepts `downloadUrl` / `apkUrl` / `url`.

## ⚠️ Notes

1. **LAN only.** Do not expose this to the public internet (home broadband does not allow inbound
   service; business lines excepted), and do not publish it through port forwarding or tunnels.
2. **HTTPS uses a self-signed certificate**, so browsers will always warn that it is untrusted —
   that is inherent to self-signing, not a defect.
3. **IPv6 reachability** depends on your ISP and router; `fe80::` link-local addresses are filtered
   out because they cannot be used across devices.
4. IP banning, anti-crawler and CAPTCHA only **raise the bar** — they are not authentication. For real
   protection combine the CAPTCHA with behavioural limits.
5. The server runs inside the app process (kept alive by a foreground service); if the system kills
   the process, the server stops.

## 📜 License

No license has been chosen yet. If you intend to open-source this, add a `LICENSE` file
(e.g. MIT or Apache-2.0).
