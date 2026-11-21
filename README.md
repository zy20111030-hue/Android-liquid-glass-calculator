# Android Liquid Glass Calculator

一个用 Kotlin + Jetpack Compose（Compose Multiplatform）实现的液态玻璃风格计算器，
接入 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop`）
实现带折射、模糊与按压形变的玻璃按键。

## 下载

已编译的 APK 见 [Releases](https://github.com/zy20111030-hue/Android-liquid-glass-calculator/releases)。

> 本仓库源码打包在 `calc-src.zip`，解压后即为完整 Gradle 工程。

## 特性

- 加/减/乘/除四则运算，按运算符优先级精确求值
- **堆叠分数输入**：长按 `÷` 画分数线，先输分母再输分子
- **括号**：长按 `AC` 插入一对 `()`，用括号改变运算优先级；点 `(` 进入、点 `)` 退出
- 分数用 BigInteger 有理数**精确计算**，结果以假分数（如 `7/4`）显示
- 点按算式可移动光标，长算式会随输入滚动。双击或长按可选中一个数，拖动手柄可以调整范围、复制或用键盘替换选区
- 按等号后光标收起，长按可复制整个结果（例如 `3/4`）。输入数字开始下一次计算，输入运算符可继续使用结果
- 液态玻璃键盘采用圆形单格键、横向胶囊 `AC` 和竖向胶囊 `=`。键面使用 `vibrancy`、`blur` 和 `lens`（折射），静止时边缘带有明暗倒角。按住并移动时，玻璃在指尖一侧内收，邻键弧边映出微光，字符保持稳定
- 点按右上角的「历史」可查看最近 10 次计算并回填结果，双击「历史」可清空记录
- OOBE 首次启动：语言选择 + 功能介绍，含过渡动画（介绍页也可在 ⋮ 菜单中查看）
- 多语言：简体中文 / 繁體中文 / English
- **桌面名称随语言变化**：`计算器` / `計算器` / `Calculator`（通过 activity-alias 切换）
- 深色 / 浅色主题：跟随系统 / 壁纸取色 / 手动
- 自定义壁纸（≤50MB）+ 内置默认壁纸
- 三档震动反馈：关闭 / 标准 / 增强

> 当前版本：**3.0-release**

## 构建

需要 Android SDK（`platforms;android-37`、`build-tools;36.0.0`）与 JDK 17。

```sh
# 调试
gradle :app:assembleDebug

# 发布（未签名；如需签名请自行配置 keystore，切勿提交密码）
gradle :app:assembleRelease
```

> 本项目未附带 Gradle Wrapper，请使用本机已安装的 Gradle，
> 或自行执行 `gradle wrapper` 生成。

## 目录结构

```
app/src/main/kotlin/com/dshm/calc/MainActivity.kt   # 全部 UI 与逻辑
app/src/main/res/                                   # 图标与默认壁纸
app/src/main/AndroidManifest.xml
settings.gradle.kts / gradle/libs.versions.toml     # 依赖与版本
```

## 许可

[MIT](LICENSE)
