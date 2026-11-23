package com.dshm.calc

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.content.edit
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            bars.show(WindowInsetsCompat.Type.statusBars())
        }
        val expression = ViewModelProvider(this)[ExpressionSession::class.java]
        setContent {
            var manualScientific by rememberSaveable { mutableStateOf(false) }
            var lockedPortrait by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
            val orientation = LocalConfiguration.current.orientation
            LaunchedEffect(manualScientific, orientation) {
                // Some devices adopt the app's landscape rotation as the user's locked rotation.
                // Return to the entry orientation before handing control back to the system.
                val restore = lockedPortrait != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                    orientation == Configuration.ORIENTATION_LANDSCAPE && rotationLockedInFullscreen()
                val direction = when {
                    manualScientific -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    restore -> lockedPortrait
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                if (!manualScientific && !restore) lockedPortrait = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (requestedOrientation != direction) requestedOrientation = direction
            }
            CalculatorApp(expression, manualScientific) { enabled ->
                if (enabled && orientation == Configuration.ORIENTATION_PORTRAIT && rotationLockedInFullscreen()) {
                    lockedPortrait = when (window.decorView.display?.rotation) {
                        Surface.ROTATION_180, Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
                manualScientific = enabled
            }
        }
    }

    private fun rotationLockedInFullscreen(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInMultiWindowMode) &&
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
}


private val DarkGradient = listOf(
    Color(0xFF16324F), Color(0xFF0A4D8C), Color(0xFF3A2E6E), Color(0xFF16324F)
)
private val LightGradient = listOf(
    Color(0xFFE6EEF8), Color(0xFFC7DBF2), Color(0xFFD9D0EC), Color(0xFFE6EEF8)
)

private fun decodeWallpaper(path: String): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxDim / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)
} catch (e: Exception) { null }

private fun isWallpaperDark(bitmap: Bitmap): Boolean {
    val scaled = bitmap.scale(32, 32, filter = false)
    var sum = 0L
    val n = 32 * 32
    for (x in 0 until 32) {
        for (y in 0 until 32) {
            val p = scaled[x, y]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
    }
    return (sum / n) < 128
}

val LocalVibrationLevel = compositionLocalOf { "standard" }

private fun hapticType(level: String, strong: Boolean): HapticFeedbackType? {
    return when (level) {
        "off" -> null
        "enhanced" -> if (Build.VERSION.SDK_INT >= 30) {
            if (strong) HapticFeedbackType.Reject else HapticFeedbackType.VirtualKey
        } else {
            if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        }
        else -> if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
    }
}

enum class Lang(val code: String) { ZH_CN("zh-CN"), ZH_TW("zh-TW"), EN("en") }

data class Str(
    val title: String, val settings: String, val darkMode: String, val wallpaper: String,
    val vibration: String, val language: String, val about: String,
    val followSystem: String, val wallpaperColor: String, val none: String,
    val off: String, val standard: String, val enhanced: String, val cancel: String,
    val version: String, val ok: String, val permText: String, val grant: String,
    val restoreDefault: String, val close: String, val error: String, val copy: String,
    val history: String, val backspace: String, val clear: String, val insertParen: String,
    val add: String, val subtract: String, val multiply: String, val divide: String, val equalsLabel: String,
    val selectAll: String, val done: String, val selectValue: String, val useResult: String,
    val insertFraction: String, val nextField: String, val absoluteValue: String,
    val emptyHistory: String, val clearHistory: String,
    val scientificCalculator: String, val followOrientation: String
)

private val strings = mapOf(
    Lang.ZH_CN to Str(
        "计算器", "设置", "深色模式", "壁纸", "震动", "语言", "关于",
        "跟随系统", "壁纸取色", "无", "关闭", "标准", "增强", "取消",
        "版本 V4.0-release", "确定", "更换壁纸需要「允许访问所有文件」权限", "去授权", "恢复默认", "关闭", "错误", "复制",
        "历史", "退格", "清除", "插入括号", "加", "减", "乘", "除", "等于",
        "全选", "完成", "选择数字", "回填", "插入分数", "下一个输入位置", "取绝对值", "暂无历史", "清空历史",
        "科学计算器", "跟随屏幕方向"
    ),
    Lang.ZH_TW to Str(
        "計算器", "設置", "深色模式", "桌布", "震動", "語言", "關於",
        "跟隨系統", "桌布取色", "無", "關閉", "標準", "增強", "取消",
        "版本 V4.0-release", "確定", "更換桌布需要「允許存取所有檔案」權限", "去授權", "恢復預設", "關閉", "錯誤", "複製",
        "歷史", "退格", "清除", "插入括號", "加", "減", "乘", "除", "等於",
        "全選", "完成", "選取數字", "帶入", "插入分數", "下一個輸入位置", "取絕對值", "暫無歷史", "清除歷史",
        "科學計算器", "跟隨螢幕方向"
    ),
    Lang.EN to Str(
        "Calculator", "Settings", "Dark mode", "Wallpaper", "Vibration", "Language", "About",
        "Follow system", "Wallpaper color", "None", "Off", "Standard", "Enhanced", "Cancel",
        "Version V4.0-release", "OK", "Changing wallpaper requires 'All files access' permission", "Grant", "Restore default", "Close", "Error", "Copy",
        "History", "Backspace", "Clear", "Insert parentheses", "Plus", "Minus", "Multiply", "Divide", "Equals",
        "Select all", "Done", "Select number", "Use result", "Insert fraction", "Next input field", "Take absolute value", "No history yet", "Clear history",
        "Scientific calculator", "Follow screen orientation"
    )
)

private fun stringsFor(lang: Lang): Str = strings[lang]!!

private fun keyShapeText(lang: Lang, which: String): String = when (lang) {
    Lang.ZH_CN -> when (which) { "title" -> "按键形状"; "rect" -> "圆角矩形"; else -> "圆形" }
    Lang.ZH_TW -> when (which) { "title" -> "按鍵形狀"; "rect" -> "圓角矩形"; else -> "圓形" }
    Lang.EN -> when (which) { "title" -> "Key shape"; "rect" -> "Rounded"; else -> "Circle" }
}

data class FeatureIntro(val title: String, val lines: List<String>, val continueLabel: String)

private fun featureIntroFor(lang: Lang): FeatureIntro = when (lang) {
    Lang.ZH_CN -> FeatureIntro(
        "功能介绍",
        listOf(
            "· 竖屏长按 ÷ 插入分数，长按 AC 插入括号；横屏有独立按键",
            "· 竖屏先填分母，再点 ÷ 填分子。横屏先填分子，用 ⇥ 切换输入位置",
            "· 点按算式移动光标；长按数字或结果可选择、复制",
            "· 横屏或从菜单打开科学计算器，可输入幂、根式和三角函数；点 Deg/Rad 切换角度单位",
            "· 顶部灵动岛常驻最近一次计算，点开可查看最近 10 条",
            "· 长按右上角圆点展开菜单，上下滑动即可选择",
            "· 按键形状可选圆角矩形或圆形",
            "· 深色/浅色主题、自定义壁纸、三档震动反馈"
        ),
        "继续"
    )
    Lang.ZH_TW -> FeatureIntro(
        "功能介紹",
        listOf(
            "· 直向時長按 ÷ 插入分數，長按 AC 插入括號；橫向有獨立按鍵",
            "· 直向先填分母，再點 ÷ 填分子。橫向先填分子，用 ⇥ 切換輸入位置",
            "· 點按算式移動游標；長按數字或結果可選取、複製",
            "· 橫向或從選單開啟科學計算器，可輸入冪、根式與三角函數；點 Deg/Rad 切換角度單位",
            "· 頂部靈動島常駐最近一次計算，點開可查看最近 10 條",
            "· 長按右上角圓點展開選單，上下滑動即可選擇",
            "· 按鍵形狀可選圓角矩形或圓形",
            "· 深色/淺色主題、自訂桌布、三檔震動回饋"
        ),
        "繼續"
    )
    Lang.EN -> FeatureIntro(
        "Features",
        listOf(
            "· Hold ÷ for fractions or AC for parentheses in portrait. In landscape, tap their own keys",
            "· Start with the denominator in portrait or the numerator in landscape. Tap ÷ or ⇥ respectively to advance",
            "· Tap to move the cursor; hold a number or result to select and copy",
            "· Rotate to landscape or open Scientific calculator from the menu for powers, roots and trigonometry. Tap Deg/Rad to change angle units",
            "· The Dynamic Island keeps your latest result; tap to open the last 10",
            "· Long-press the dot at the top-right to open the menu, then slide to pick",
            "· Key shape can be rounded rectangle or circle",
            "· Dark/light theme, custom wallpaper, three-level haptic feedback"
        ),
        "Continue"
    )
}

private fun serializeHistory(history: List<HistoryItem>): String =
    history.joinToString("|") { "${it.expr}=${it.result}" }

private fun deserializeHistory(s: String): List<HistoryItem> =
    s.split("|").filter { it.isNotEmpty() }.mapNotNull {
        val idx = it.indexOf('=')
        if (idx > 0) HistoryItem(it.substring(0, idx), it.substring(idx + 1)) else null
    }


@Composable
private fun CalculatorApp(session: ExpressionSession, manualScientific: Boolean, setManualScientific: (Boolean) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calc_prefs", 0) }

    var oobeDone by remember { mutableStateOf(prefs.getBoolean("oobe_done", false)) }
    var language by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }

    val resources = LocalResources.current
    val defaultWallpaper = remember(resources) {
        try { BitmapFactory.decodeResource(resources, R.drawable.default_wallpaper) }
        catch (_: android.content.res.Resources.NotFoundException) { null }
    }

    val introExit = remember { Animatable(if (oobeDone) 0f else 1f) }
    val calculatorEntry = remember { Animatable(if (oobeDone) 1f else 0f) }
    var showIntroScene by remember { mutableStateOf(!oobeDone) }
    LaunchedEffect(oobeDone) {
        if (oobeDone && showIntroScene) {
            introExit.animateTo(0f, tween(350))
            showIntroScene = false
            calculatorEntry.animateTo(1f, tween(500))
        }
    }

    val lang = Lang.values().find { it.code == language } ?: Lang.EN
    val str = stringsFor(lang)

    var darkMode by remember { mutableStateOf(prefs.getString("dark_mode", "manual") ?: "manual") }
    var manualDark by remember { mutableStateOf(prefs.getBoolean("manual_dark", true)) }
    var wallpaperPath by remember { mutableStateOf(prefs.getString("wallpaper", null)) }
    val wallpaper by produceState<Bitmap?>(null, wallpaperPath) {
        val path = wallpaperPath
        value = path?.let { withContext(Dispatchers.IO) { decodeWallpaper(it) } }
    }
    val effectiveWallpaper = if (wallpaperPath == null) defaultWallpaper else wallpaper ?: defaultWallpaper
    val wallpaperIsDark = remember(effectiveWallpaper) { effectiveWallpaper?.let(::isWallpaperDark) }

    val dark = when (darkMode) {
        "system" -> isSystemInDarkTheme()
        "wallpaper" -> wallpaperIsDark ?: manualDark
        else -> manualDark
    }
    val fg = if (dark) Color.White else Color(0xFF15151A)
    val equalsInk = if (dark) Color(0xFFB9DCFF) else Color(0xFF173F67)
    val buttonSurface = if (dark) Color(0xFF0D0D10).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.42f)
    val lightDisplayText = wallpaperIsDark ?: dark
    val displayColor = if (lightDisplayText) Color(0xFFF4F6FA) else Color(0xFF181B20)

    var overlay by rememberSaveable { mutableStateOf<CalculatorOverlay?>(null) }
    val overlayTransition = updateTransition(overlay, label = "Calculator overlays")
    val menuProgress = rememberMenuProgress(overlay == CalculatorOverlay.MENU)
    val overlayOnStage by remember(overlayTransition, menuProgress) {
        derivedStateOf { overlayTransition.onStage || menuProgress.value > 0f }
    }
    BackHandler(enabled = manualScientific && !overlayOnStage && session.selection == null) {
        setManualScientific(false)
    }
    val focus = LocalFocusManager.current
    fun navigate(destination: CalculatorOverlay?) {
        if (overlay == null && destination != null) focus.clearFocus()
        overlay = destination
    }
    var keyShape by remember { mutableStateOf(prefs.getString("key_shape", "circle") ?: "circle") }
    val keyRoundness = rememberUpdatedState(if (keyShape == "circle") 1f else 0f)
    val intro = remember(lang) { featureIntroFor(lang) }
    val items = remember(lang, manualScientific) {
        menuItems(str, keyShapeText(lang, "title"), intro.title, manualScientific)
    }

    var vibrationLevel by remember { mutableStateOf(prefs.getString("vibration_level", "standard") ?: "standard") }
    val haptic = LocalHapticFeedback.current

    val history = remember {
        mutableStateListOf<HistoryItem>().apply {
            addAll(deserializeHistory(prefs.getString("history", "") ?: ""))
        }
    }

    fun persistHistory() {
        prefs.edit { putString("history", serializeHistory(history.toList())) }
    }

    fun clearHistory() {
        session.forgetCompletedExpression()
        history.clear()
        persistHistory()
    }

    fun onEquals() {
        val item = session.calculate(str.error) ?: return
        history.add(0, item)
        while (history.size > 10) history.removeAt(history.lastIndex)
        persistHistory()
    }

    fun toggleThemeByLongPress() {
        if (darkMode == "manual") {
            manualDark = !manualDark
            prefs.edit { putBoolean("manual_dark", manualDark) }
            hapticType(vibrationLevel, true)?.let { haptic.performHapticFeedback(it) }
        }
    }

    val keypadLights = remember { KeypadLights() }
    val foreground = rememberLayerBackdrop()
    val scene = rememberCombinedBackdrop(backdrop, foreground)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scientific = landscape || manualScientific
    CompositionLocalProvider(
        LocalVibrationLevel provides vibrationLevel,
        LocalKeypadLights provides keypadLights,
        LocalKeyRoundness provides keyRoundness
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val progress = if (showIntroScene) introExit.value else calculatorEntry.value
            scaleX = progress
            scaleY = progress
            alpha = progress
            translationY = if (showIntroScene) 0f else (1f - progress) * 60.dp.toPx()
        }) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                if (effectiveWallpaper != null) {
                    Image(
                        bitmap = effectiveWallpaper.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val gradientColors = if (dark) DarkGradient else LightGradient
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradientColors)))
                }
            }

            if (!showIntroScene) {
                Box(Modifier.fillMaxSize().coveredByOverlay(calculatorEntry.isRunning)) {
                    Box(Modifier.fillMaxSize().coveredByOverlay(overlayOnStage).layerBackdrop(foreground)) {
                        if (session.selection != null) {
                            // 选中态下，点空白区域即可退出选择、恢复原样
                            Box(
                                Modifier.matchParentSize().pointerInput(Unit) {
                                    detectTapGestures { session.dismissSelection() }
                                }
                            )
                        }

                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)
                        ) {
                            if (!landscape) Spacer(Modifier.height(ToolbarTouch + ToolbarTop * 2))

                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                val historyMargin = minOf(maxWidth * 0.18f, 120.dp)
                                val keypadSize = if (landscape) {
                                    val height = (maxHeight * 0.74f).coerceAtMost(360.dp)
                                    Modifier.fillMaxWidth().height(height)
                                } else if (manualScientific) {
                                    Modifier.fillMaxWidth().height(maxHeight * 0.74f)
                                } else {
                                    // On a nearly square portrait window, five rows still need to leave room for the formula.
                                    Modifier.width(minOf(maxWidth, maxHeight * 0.64f))
                                }
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val formulaArea = Modifier.weight(1f).fillMaxWidth()
                                    ExpressionDisplay(session, str, displayColor, lightDisplayText, CalculatorFont,
                                        if (landscape) formulaArea.padding(start = historyMargin, end = 54.dp)
                                        else formulaArea.padding(end = 10.dp), active = !overlayOnStage && !calculatorEntry.isRunning)
                                    Box(keypadSize.onPlaced { keypadLights.plane = it }
                                        .then(if (manualScientific && !landscape) Modifier.clipToBounds() else Modifier)) {
                                        CalculatorKeypad(session, str, backdrop, buttonSurface, fg, equalsInk, ::onEquals, ::toggleThemeByLongPress,
                                            scientific, if (scientific) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), narrowWindow = !landscape)
                                        Spacer(Modifier.matchParentSize().graphicsLayer()
                                            .drawNeighborReflections(keypadLights, dark, keyRoundness))
                                    }
                                }
                            }
                        }
                    }
                    CalculatorOverlays(
                        transition = overlayTransition, menuProgress = menuProgress, navigate = ::navigate, scene = scene, landscape = landscape, dark = dark,
                        toggleScientific = { setManualScientific(!manualScientific) },
                        toggleHistory = {
                            when (overlay) {
                                null -> navigate(CalculatorOverlay.HISTORY)
                                CalculatorOverlay.HISTORY -> navigate(null)
                                else -> Unit
                            }
                        },
                        titleColor = displayColor, str = str, items = items, history = history,
                        clearHistory = ::clearHistory, useResult = session::useResult,
                        dragHaptic = { hapticType(vibrationLevel, true)?.let { haptic.performHapticFeedback(it) } }
                    ) { page, source ->
                        if (page == CalculatorOverlay.WALLPAPER || page == CalculatorOverlay.WALLPAPER_PERMISSION) {
                            WallpaperSettings(page = page, str = str, lang = lang, dark = dark, backdrop = source,
                                onNavigate = { destination -> if (overlay == page) navigate(destination) }, onPick = { path ->
                                    if (overlay == page) {
                                        wallpaperPath = path
                                        prefs.edit { putString("wallpaper", path) }
                                    }
                                })
                        } else {
                            val current = when (page) {
                                CalculatorOverlay.THEME -> darkMode
                                CalculatorOverlay.VIBRATION -> vibrationLevel
                                CalculatorOverlay.KEY_SHAPE -> keyShape
                                CalculatorOverlay.LANGUAGE -> language
                                else -> null
                            }
                            SettingsPanel(page = page, title = items.first { it.action == page }.label, str = str, intro = intro,
                                shapeLabels = keyShapeText(lang, "circle") to keyShapeText(lang, "rect"),
                                current = current, dark = dark, backdrop = source,
                                onDismiss = { if (overlay == page) navigate(null) },
                                onChoose = choose@{ value ->
                                    if (overlay != page) return@choose
                                    val pref = when (page) {
                                        CalculatorOverlay.THEME -> { darkMode = value; "dark_mode" }
                                        CalculatorOverlay.VIBRATION -> { vibrationLevel = value; "vibration_level" }
                                        CalculatorOverlay.KEY_SHAPE -> { keyShape = value; "key_shape" }
                                        CalculatorOverlay.LANGUAGE -> { language = value; "language" }
                                        else -> error("No choices for $page")
                                    }
                                    prefs.edit { putString(pref, value) }
                                    navigate(null)
                                })
                        }
                    }
                }
            } else {
                OobeScreen(backdrop, enabled = !oobeDone) { code ->
                    if (!oobeDone) {
                        language = code
                        prefs.edit { putString("language", code); putBoolean("oobe_done", true) }
                        oobeDone = true
                    }
                }
            }
        }
    }
}

@Composable
private fun OobeScreen(backdrop: Backdrop, enabled: Boolean, onDone: (String) -> Unit) {
    var language by rememberSaveable { mutableStateOf("en") }
    var showIntro by rememberSaveable { mutableStateOf(false) }
    val pageScroll = key(showIntro) { rememberScrollState() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().coveredByOverlay(!enabled)
            .padding(horizontal = 32.dp).verticalScroll(pageScroll).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (!showIntro) {
            BasicText("选择语言 / Select language",
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(24.dp))
            for (option in Lang.entries) {
                val label = when (option) {
                    Lang.ZH_CN -> "简体中文"
                    Lang.ZH_TW -> "繁體中文"
                    Lang.EN -> "English"
                }
                OobeButton(backdrop, label, enabled, chosen = language == option.code) { language = option.code }
            }
            Spacer(Modifier.height(32.dp))
            OobeButton(backdrop, "开始 / Start", enabled) { showIntro = true }
        } else {
            val intro = featureIntroFor(Lang.entries.first { it.code == language })
            BasicText(intro.title,
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(20.dp))
            intro.lines.forEach { line ->
                BasicText(line, Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp))
            }
            Spacer(Modifier.height(28.dp))
            OobeButton(backdrop, intro.continueLabel, enabled) { onDone(language) }
        }
    }
}

@Composable
private fun OobeButton(backdrop: Backdrop, text: String, enabled: Boolean, chosen: Boolean? = null, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vibrationLevel = LocalVibrationLevel.current
    val source = remember { MutableInteractionSource() }
    val press = rememberOptionScale(source)
    val focused by source.collectIsFocusedAsState()
    val accent = chosen != false
    val shape = RoundedCornerShape(if (chosen == null) 24.dp else 16.dp)
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).heightIn(min = 48.dp)
            .then(if (chosen == null) Modifier else Modifier.semantics { selected = chosen })
            .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = null) {
                hapticType(vibrationLevel, false)?.let { haptic.performHapticFeedback(it) }
                onClick()
            }, contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = {
                vibrancy()
                blur(2.dp.toPx())
                lens(14.dp.toPx(), 22.dp.toPx())
            },
            layerBlock = { scaleX = press.value; scaleY = press.value },
            onDrawSurface = {
                drawRect(if (accent) Color(0xFF0A84FF).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f))
            }
        ).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (focused) Spacer(Modifier.matchParentSize().border(2.dp, Color.White, shape))
            BasicText(text, Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                style = TextStyle(color = Color.White, fontSize = if (chosen == null) 18.sp else 17.sp,
                    fontWeight = if (chosen == null) FontWeight.Bold else FontWeight.Normal))
            if (chosen == true) {
                BasicText("✓", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
                    .clearAndSetSemantics {},
                    style = TextStyle(color = Color.White, fontSize = 16.sp))
            }
        }
    }
}

internal val CalculatorFont = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(R.font.dm_sans),
        Font(R.font.dm_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    )
} else {
    FontFamily(Font(R.font.dm_sans))
}


@Composable
fun GlassButton(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    surfaceColor: Color = Color.White.copy(alpha = 0.05f),
    textColor: Color = Color.White,
    strongHaptic: Boolean = false,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    symbolLabel: String = text,
    compact: Boolean = false,
    labelSize: TextUnit? = null,
    content: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val vibrationLevel = LocalVibrationLevel.current
    val interactionSource = remember { MutableInteractionSource() }
    val keypad = LocalKeypadLights.current ?: remember { KeypadLights() }
    val light = remember(keypad) { KeyLightSource(keypad) }
    DisposableEffect(light) {
        keypad.keys.add(light)
        onDispose {
            light.finishPress()
            keypad.keys.remove(light)
        }
    }

    Box(
        modifier
            .padding(horizontal = if (compact) 3.dp else 6.dp, vertical = if (compact) 2.dp else 6.dp)
            .trackKeyContact(light)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onClick = {
                    hapticType(vibrationLevel, strongHaptic)?.let { haptic.performHapticFeedback(it) }
                    onClick()
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassKeyFace(backdrop, surfaceColor, light)
        if (content != null) {
            content()
        } else {
            val icon = when (text) {
                "+" -> R.drawable.keypad_add
                "-", "−" -> R.drawable.keypad_subtract
                "×" -> R.drawable.keypad_multiply
                "÷" -> R.drawable.keypad_divide
                "(", ")" -> R.drawable.keypad_parenthesis
                "□/□" -> R.drawable.keypad_fraction
                "⇥" -> R.drawable.keypad_next_field
                "=" -> R.drawable.keypad_equals
                else -> null
            }
            if (icon != null) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = symbolLabel,
                    modifier = Modifier.size(with(LocalDensity.current) { (labelSize ?: 32.sp).toDp() })
                        .then(if (text == ")") Modifier.graphicsLayer { scaleX = -1f } else Modifier),
                    colorFilter = ColorFilter.tint(textColor)
                )
            } else {
                val isNumber = text.length == 1 && (text[0].isDigit() || text == ".")
                BasicText(
                    text,
                    style = TextStyle(
                        color = textColor,
                        fontFamily = if (isNumber || compact) CalculatorFont else null,
                        fontSize = labelSize ?: if (isNumber) 32.sp else 29.sp
                    ),
                    maxLines = 1, softWrap = false
                )
            }
        }
}
}
