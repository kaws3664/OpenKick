<div align="center">
<br />
<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="QuinKick logo" width="192" />
</div>

<h1 align="center">QuinKick</h1>

<p align="center">
  Kick.com 的开源安卓客户端 <a href="https://github.com/Gallbladderz/OpenKick">OpenKick</a> 的定制分支。<br />
  支持账号登录、直播、聊天、点播（VOD）与剪辑（Clips）。界面提供中文、English、Español、Русский。
</p>

<div align="center">
  <a href="https://github.com/kaws3664/QuinKick/releases"><img src="https://user-images.githubusercontent.com/69304392/148696068-0cfea65d-b18f-4685-82b5-329a330b1c0d.png" alt="Get it on GitHub" height="80" /></a>
</div>

## 在 OpenKick 基础上做的改变

- **完整简体中文界面**：258 条字符串全部翻译，语言设置里可直接切换到「中文（简体）」。
- **弹幕覆盖层**：视频画面上叠加聊天弹幕（可开关），对齐浏览器 userscript 的功能与参数：
  - 弹幕速度（4–20 秒/屏）、字体大小、不透明度、显示区域（25–100%）均可调；
  - 密度三档：普通 / 更多 / 重叠；
  - 真实碰撞检测：按实测文本宽度分配轨道，上一条弹幕完全进屏后新弹幕才入轨，不互相压叠；
  - 轨道满时排队等待，播放器暂停时弹幕同步冻结；
  - Kick 表情图片渲染（含动态表情）；
  - 相同弹幕自动合并为 `内容 ×N`（PiliNara 风格）；
  - 按消息 ID 去重（上限 5000），进入直播间时只发新消息、不重播历史。
- **回放（VOD）弹幕**：自动拉取聊天回放记录，按播放位置同步显示（需登录）。
- **回放倍速播放**：底栏 ×N 按钮循环 0.5x–2x（PiliNara 风格）。
- **全屏手势**（参考 PiliNara）：左侧上下滑调亮度、右侧上下滑调音量（可突破至 200%）、中间上滑进全屏 / 下滑退出；聊天按钮移到底栏与全屏按钮并排。
- **聊天输入优化**：输入框覆盖层跟随光标横向滚动，长文本不再看不到光标后的内容。
- **图标修复**：图标内容缩至启动器安全区内，奎恩头像与 Kick 绿 K 徽章完整显示。
- **SmartDns 网络优化**：DNS 结果 stale-while-revalidate 缓存 + 自动返回最快的 2 个 IP，降低播放与 API 延迟。
- **更稳的数据存储**：偏好数据损坏时回退默认值而不是闪退（之前旧版数据损坏会导致无法启动）。
- **QuinKick 品牌**：应用名与图标换为 QuinKick（图标为奎恩图 + Kick 绿 K 徽章），内置默认关注 quin3614 与 czcjiaxu（仅在本地关注列表为空时生效一次，取关后不会复活）。
- **安装包优化**：仅构建 arm64-v8a，安装包约 5MB。

## 致谢

本项目站在这些项目和作者的肩膀上，非常感谢：

- **[Gallbladderz / OpenKick](https://github.com/Gallbladderz/OpenKick)** — 本项目的上游。QuinKick 的一切都基于这个优秀的开源 Kick 客户端（Kotlin + Jetpack Compose + Media3/ExoPlayer + Centrifugo），账号登录、直播播放、聊天、VOD、通知等核心能力全部来自它。
- **[kick显示弹幕 userscript](https://update.greasyfork.org/scripts/600728)（Greasyfork #600728）** — 浏览器端 Kick 弹幕脚本。QuinKick 弹幕覆盖层的参数设计（速度/字号/不透明度/区域/密度、消息去重上限）直接参考了它的实现思路。
- **[PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)**（及其相关分支 PiliNara）— 开源 B 站第三方客户端及其 `canvas_danmaku` 弹幕引擎。QuinKick 的弹幕轨道碰撞检测、等待队列与暂停同步移植了它的算法思想。

QuinKick 遵循上游的 **GPL-3.0** 许可证开源，欢迎按许可证使用与修改。

## 功能

- **Kick 账号登录** / 访客模式（可本地收藏）
- **直播 / VOD / 剪辑**：Media3/ExoPlayer 播放，支持画中画、后台播放与倍速（0.5x–2x）
- **实时聊天**：读发消息、回复、表情、徽章与名字颜色，弹幕覆盖层
- **VOD 弹幕回放**：看回放也能飘弹幕，与播放位置同步
- **全屏手势**：亮度 / 音量（支持 200% 增强）/ 全屏切换
- **关注同步**：登录后与 Kick 同步，或访客模式本地管理
- **通知**：关注的频道开播提醒
- **搜索发现**：频道、直播、剪辑、分类
- **个性化**：动态取色、Catppuccin 主题、语言设置（含中文）、聊天显示控制

## 构建

Kotlin + Jetpack Compose + Gradle 的 Android Studio 工程，需 JDK 17+。

```bash
git clone https://github.com/kaws3664/QuinKick.git
cd QuinKick
```

用 Android Studio 打开、Gradle 同步后运行 `app` 配置即可。命令行构建 release：

```bash
./gradlew :app:assembleRelease
```
