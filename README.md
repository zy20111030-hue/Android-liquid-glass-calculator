# Android Liquid Glass Calculator

一个用 Kotlin + Jetpack Compose（Compose Multiplatform）实现的液态玻璃风格计算器，
接入 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（`io.github.kyant0:backdrop`）
实现带折射、模糊与按压形变的玻璃按键。

## 下载

已编译的 APK 见 [Releases](https://github.com/zy20111030-hue/Android-liquid-glass-calculator/releases)。

## 特性

- 加/减/乘/除四则运算，按运算符优先级精确求值
- **竖屏分数输入**：长按 `÷` 画分数线，先输分母再输分子
- **竖屏括号输入**：长按 `AC` 插入一对 `()`，用括号改变运算优先级；点算式中的 `(` 进入、点 `)` 退出
- 分数用 BigInteger 有理数**精确计算**，结果以假分数（如 `7/4`）显示
- 点按算式可移动光标，长算式会随输入滚动。双击或长按可选中一个数，拖动手柄可以调整范围、复制或用键盘替换选区
- 按等号后光标收起，长按可复制整个结果（例如 `3/4`）。输入数字开始下一次计算，输入运算符可继续使用结果
- 结果上方保留原算式的数学排版，分子、分母和嵌套括号可以直接辨认。旋转会保留正在编辑的算式、光标和选区
- 横屏自动进入科学计算器并收起系统状态栏。上方显示整条算式，下方左侧是科学键，右侧保留竖屏按键顺序，并在最上面加一行记忆键
- 两种方向的基础键区都可直接输入百分比，`%` 在 `0` 左侧，退格在 `×` 右侧
- 科学键支持幂、平方根和任意次根、阶乘、绝对值、π、e、对数和三角函数。`Inv` 切换反三角函数，`Deg/Rad` 切换角度和弧度。四则与可精确求值的幂、根保留有理数结果，超越函数按需要给出近似值
- 横屏点分数键可把刚输入的数作为分子，直接填写分母。也可先插入空模板，再用 `⇥` 切换输入位置；`(`、`)` 有独立按键，`÷` 始终输入除法
- `M+`、`M−` 将当前算式的值加到或减出记忆值，`MR` 回填，`MC` 清除
- 液态玻璃键盘的 `AC` 占一格，竖向 `=` 跨两行。键面使用 `vibrancy`、`blur` 和 `lens`（折射），静止时边缘带有明暗倒角。按住并移动时，玻璃在指尖一侧内收，邻键弧边映出微光，字符保持稳定
- 键面沿指尖位置下压，形变由短边和最大位移共同限制。快速轻点有短促的压入反馈，按住再松开会轻微回弹
- **跟手光效**：按住后手指滑出键区，光晕与邻键反光依然保持，直到抬手才熄灭
- **顶部灵动岛**：点开查看最近十条计算。点记录可连续回填，点右侧回填箭头会同时收起面板；横屏可滚动查看和清空记录
- 点按右上角三点展开菜单，历史胶囊仍在原位。长按三点可拖动透镜选项，松手进入对应页面。横屏单列放不下且宽度足够时，七项排成两列。菜单、历史面板和设置页会折射下方实际显示的内容
- **设置子页**：同样是玻璃化的胶囊选项，选中项蓝底高亮
- **按键形状**：圆角矩形 / 圆形可切换
- OOBE 首次启动：语言选择 + 功能介绍，含过渡动画（介绍页也可在 ⋮ 菜单中查看）
- 多语言：简体中文 / 繁體中文 / English
- **桌面名称本地化**：`计算器` / `計算器` / `Calculator`（通过资源与系统/应用语言解析）
- 深色 / 浅色主题：跟随系统 / 壁纸取色 / 手动
- 自定义壁纸（≤50MB）+ 内置默认壁纸
- 三档震动反馈：关闭 / 标准 / 增强

> 当前版本：**4.0-release**

## 构建

需要 Android SDK（`platforms;android-37`、`build-tools;36.0.0`）与 JDK 17。

```sh
# 调试
./gradlew :app:assembleDebug

# 发布（签名信息从 local.properties 读取；未配置时为未签名 APK）
./gradlew :app:assembleRelease
```

本项目附带 Gradle Wrapper（Gradle 9.9.1），无需本机预装 Gradle。

## 目录结构

```
app/src/main/kotlin/com/dshm/calc/
  MainActivity.kt        # 页面、主题、设置与历史记录
  CalculatorOverlays.kt  # 顶栏、浮层导航与触摸遮挡
  OverlayGlass.kt        # 菜单、历史岛和设置页共用的取样与玻璃表面
  SettingsPanels.kt      # 设置子页与选项；壁纸文件浏览见 WallpaperSettings.kt
  CalculatorKeypad.kt    # 竖屏基础键盘、横屏科学键盘与按键绑定
  ExpressionEditor.kt    # 表达式树、模板输入槽与光标导航
  ExpressionSession.kt   # 横竖屏共用的输入命令、选区与结算状态
  ExpressionLayout.kt    # 算式测量、数学轴对齐、绘制与命中
  ExpressionDisplay.kt   # 可编辑算式和已完成算式的显示
  Evaluator.kt           # 有理数精确求值、科学函数与角度换算
  GlassKeyFace.kt        # 玻璃按键外观与按压形变
  GlassPressMotion.kt    # 玻璃受压比例、快速轻点和卸力回弹
  KeypadLighting.kt      # 指针跟踪与邻键反光
app/src/main/res/                                   # 图标与默认壁纸
app/src/main/AndroidManifest.xml
settings.gradle.kts / gradle/libs.versions.toml     # 依赖与版本
```

## 许可

[MIT](LICENSE)
