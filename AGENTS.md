# QuinKick

Gallbladderz/OpenKick 的 fork：Kick 安卓客户端（Kotlin + Compose + Media3/ExoPlayer + Centrifugo 弹幕）。
本地改动：中文翻译、弹幕覆盖层（`features/player/components/DanmakuOverlay.kt`）、SmartDns 优选、QuinKick 品牌。

- 提交被 pre-commit 钩子拒绝：按报错把 >20KB 的文件按功能拆开再提交。
- 写新函数/规则前先搜项目里有没有现成的（`grep -rn` 再动手）。
- 加功能前先 commit + 推 fork + 打 tag 存档。
- 构建坑、Kick API 坑见 skill `android-app-localization`。
