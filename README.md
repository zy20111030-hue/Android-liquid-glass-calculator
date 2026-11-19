
# 简易计算器 (Compose 版 · 液态玻璃)

用 Kotlin + Compose Multiplatform 重写，接入 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop`）实现液态玻璃按键。

## 基本信息

- 包名：`com.dshm.calc`
- targetSdk：37，minSdk：23
- 版本：**2.0-release**
- 构建：AGP 9.3.2 · Kotlin 2.4.10 · Compose Multiplatform 1.12.0
- 依赖：`io.github.kyant0:backdrop:2.0.1`、`io.github.kyant0:shapes:1.2.1`
- 图标：自绘（深灰底 + 藏青内衬 + 白色 `+ − × ÷`）

## 功能

- 加/减/乘/除四则运算，连续运算即时求值；小数点、退格、清空（AC）、除零显示「错误」
- **液态玻璃按键**：`vibrancy` + `blur` + `lens`（折射），按下按键玻璃鼓起形变（可调强度/厚度）
- **按键布局**：正方形圆角按键 + 竖排 `=`；`AC` 占两格、`⌫` 位于末行
- **OOBE 首次启动**：语言选择（默认 English），选完有「下沉缩小 → 放大上浮」过渡动画进入主界面
- **多语言**：简体中文 / 繁體中文 / English，全界面适配
- **深色 / 浅色主题**（三选一，持久化）：跟随系统 / 壁纸取色 / 无（长按 `=` 手动切换）
- **自定义壁纸**：内置默认壁纸 + 用户可选壁纸（需「允许访问所有文件」权限）
- **三档震动**：关闭 / 标准 / 增强
- 关于（版本 V2.0-release）

## 文件说明

- `calculator-v2.0-release-*.apk` —— **已签名 release APK**（V2 签名）
- `app/src/main/kotlin/com/dshm/calc/MainActivity.kt` —— 全部 UI + 逻辑
- `app/src/main/AndroidManifest.xml` —— 清单（含 MANAGE_EXTERNAL_STORAGE）
- `app/src/main/res/` —— 图标 + 默认壁纸
- `app/calc.keystore` —— 签名密钥（别名 `calc`）
- `app/build.gradle.kts`、`settings.gradle.kts`、`gradle/libs.versions.toml` —— Gradle 工程



依赖：Android SDK（`platforms/android-37.0`）、已修复的 JDK `cacerts`、arm64 `aapt2`。

```sh
gradle :app:assembleRelease   # 已签名 release
gradle :app:assembleDebug     # debug 调试
```
