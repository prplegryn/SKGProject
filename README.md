# SKGProject

极简相册 App，Kotlin + Jetpack Compose + Media3。

## 当前功能

- 首次运行使用系统目录选择器设置读取目录。
- 读取目录下的一级文件夹，每个文件夹作为一个相册。
- 主页展示相册网格，相册页展示图片和视频网格。
- 图片全屏查看支持双击放大、手势缩放和平移。
- 视频全屏打开后立即播放，使用 Media3 ExoPlayer。
- 视频控制层包含极简进度条、拖动定位、横向滑动快进、时间戳和无限循环按钮。
- 仓库内置固定开发签名 `signing/skgproject-dev.p12`，debug/release 均使用同一签名，覆盖安装不需要卸载旧包。
- GitHub Actions 会在 push/PR/workflow_dispatch 时构建 release APK 并上传 artifact。

## 签名

开发签名文件：`signing/skgproject-dev.p12`

Gradle 签名配置：

- store password: `skgproject`
- key alias: `skgproject`
- key password: `skgproject`
- store type: `pkcs12`

这是开发/分发测试用固定签名，不建议用于正式商店发布。

