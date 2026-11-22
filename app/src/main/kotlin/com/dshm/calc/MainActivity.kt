package com.dshm.calc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur as blurModifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("calc_prefs", 0)
        syncLauncherAlias(this, prefs.getString("language", "en") ?: "en")
        setContent { CalculatorApp() }
    }
}


/** 参考语言设置应用 App 级 Locale（Android 13+），启动器名称由资源本地化提供 */
fun syncLauncherAlias(context: Context, lang: String) {
    if (Build.VERSION.SDK_INT >= 33) {
        try {
            val lm = context.getSystemService(android.app.LocaleManager::class.java)
            lm?.applicationLocales = android.os.LocaleList.forLanguageTags(lang)
        } catch (e: Exception) {
            // ignore
        }
    }
}

private val DarkGradient = listOf(
    Color(0xFF16324F), Color(0xFF0A4D8C), Color(0xFF3A2E6E), Color(0xFF16324F)
)
private val LightGradient = listOf(
    Color(0xFFE6EEF8), Color(0xFFC7DBF2), Color(0xFFD9D0EC), Color(0xFFE6EEF8)
)

private fun isImage(file: File): Boolean {
    val n = file.name.lowercase()
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
        n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
        n.endsWith(".heic") || n.endsWith(".heif")
}

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
    val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, false)
    var sum = 0L
    val n = 32 * 32
    for (x in 0 until 32) {
        for (y in 0 until 32) {
            val p = scaled.getPixel(x, y)
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
    val selectAll: String, val done: String, val selectValue: String, val useResult: String
)

private val strings = mapOf(
    Lang.ZH_CN to Str(
        "计算器", "设置", "深色模式", "壁纸", "震动", "语言", "关于",
        "跟随系统", "壁纸取色", "无", "关闭", "标准", "增强", "取消",
        "版本 V4.0-release", "确定", "更换壁纸需要「允许访问所有文件」权限", "去授权", "恢复默认", "关闭", "错误", "复制",
        "历史", "退格", "清除", "插入括号", "加", "减", "乘", "除", "等于",
        "全选", "完成", "选择数字", "回填"
    ),
    Lang.ZH_TW to Str(
        "計算器", "設置", "深色模式", "桌布", "震動", "語言", "關於",
        "跟隨系統", "桌布取色", "無", "關閉", "標準", "增強", "取消",
        "版本 V4.0-release", "確定", "更換桌布需要「允許存取所有檔案」權限", "去授權", "恢復預設", "關閉", "錯誤", "複製",
        "歷史", "退格", "清除", "插入括號", "加", "減", "乘", "除", "等於",
        "全選", "完成", "選取數字", "帶入"
    ),
    Lang.EN to Str(
        "Calculator", "Settings", "Dark mode", "Wallpaper", "Vibration", "Language", "About",
        "Follow system", "Wallpaper color", "None", "Off", "Standard", "Enhanced", "Cancel",
        "Version V4.0-release", "OK", "Changing wallpaper requires 'All files access' permission", "Grant", "Restore default", "Close", "Error", "Copy",
        "History", "Backspace", "Clear", "Insert parentheses", "Plus", "Minus", "Multiply", "Divide", "Equals",
        "Select all", "Done", "Select number", "Use result"
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
            "· 长按 ÷ 输入分数：先输分母再输分子，最多两层嵌套",
            "· 长按 AC 插入括号，用括号改变运算优先级",
            "· 点按算式移动光标；长按数字或结果可选择、复制",
            "· 算式按运算优先级精确计算，分数不丢精度",
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
            "· 長按 ÷ 輸入分數：先輸分母再輸分子，最多兩層巢狀",
            "· 長按 AC 插入括號，用括號改變運算優先級",
            "· 點按算式移動游標；長按數字或結果可選取、複製",
            "· 算式按運算優先級精確計算，分數不丟精度",
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
            "· Long-press ÷ to enter a fraction (denominator first, up to 2 levels)",
            "· Long-press AC to insert parentheses for precedence control",
            "· Tap to move the cursor; hold a number or result to select and copy",
            "· Expressions are evaluated exactly with operator precedence",
            "· The Dynamic Island keeps your latest result; tap to open the last 10",
            "· Long-press the dot at the top-right to open the menu, then slide to pick",
            "· Key shape can be rounded rectangle or circle",
            "· Dark/light theme, custom wallpaper, three-level haptic feedback"
        ),
        "Continue"
    )
}

data class HistoryItem(val expr: String, val result: String)

private fun serializeHistory(history: List<HistoryItem>): String =
    history.joinToString("|") { "${it.expr}=${it.result}" }

private fun deserializeHistory(s: String): List<HistoryItem> =
    s.split("|").filter { it.isNotEmpty() }.mapNotNull {
        val idx = it.indexOf('=')
        if (idx > 0) HistoryItem(it.substring(0, idx), it.substring(idx + 1)) else null
    }


data class ParenFrame(
    val parent: MutableList<FItem>,
    val at: Int,
    val paren: FItem.ParenItem
)

data class FracFrame(
    val parent: MutableList<FItem>,
    val fracAt: Int,
    val frac: FItem.FracItem,
    val inNum: Boolean
)

class FracEditor {
    val root = mutableListOf<FItem>()
    var slot: MutableList<FItem> = root
    var index = 0
    var charOff = 0
    private val stack = mutableListOf<FracFrame>()

    fun inFraction() = stack.isNotEmpty()

    fun inGroup() = stack.isNotEmpty() || parenStack.isNotEmpty()

    fun moveToEnd() {
        slot = root
        index = root.size
        charOff = 0
        stack.clear()
        parenStack.clear()
    }

    fun insertChar(c: Char) {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num) {
            val off = charOff.coerceIn(0, cur.text.length)
            cur.text = cur.text.substring(0, off) + c + cur.text.substring(off)
            charOff = off + 1
        } else {
            val previous = slot.getOrNull(index - 1) as? FItem.Num
            if (previous == null) {
                slot.add(index, FItem.Num(c.toString()))
                charOff = 1
            } else {
                index--
                previous.text += c
                charOff = previous.text.length
            }
        }
    }

    private fun splitAtCursor(): Int {
        val number = slot.getOrNull(index) as? FItem.Num ?: return index
        val offset = charOff.coerceIn(0, number.text.length)
        if (offset == 0) return index
        if (offset < number.text.length) {
            slot.add(index + 1, FItem.Num(number.text.substring(offset)))
            number.text = number.text.substring(0, offset)
        }
        return index + 1
    }

    fun insertOp(sym: String) {
        val at = splitAtCursor()
        slot.add(at, FItem.Op(sym))
        index = at + 1
        charOff = 0
    }

    fun backspace() {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num) {
            val off = charOff.coerceIn(0, cur.text.length)
            if (off > 0) {
                cur.text = cur.text.substring(0, off - 1) + cur.text.substring(off)
                charOff = off - 1
                if (cur.text.isEmpty()) {
                    slot.removeAt(index)
                    mergeNumbersAtCursor()
                }
                return
            }
        }
        // 光标位于某个组的开头：直接删除整组
        if (index == 0) {
            val pf = parenStack.lastOrNull()
            if (pf != null && slot === pf.paren.inner) {
                parenStack.removeAt(parenStack.size - 1)
                slot = pf.parent
                if (pf.at < slot.size && slot[pf.at] === pf.paren) slot.removeAt(pf.at)
                index = pf.at
                charOff = 0
                mergeNumbersAtCursor()
                return
            }
            val ff = stack.lastOrNull()
            if (ff != null && (slot === ff.frac.num || slot === ff.frac.den)) {
                stack.removeAt(stack.size - 1)
                slot = ff.parent
                if (ff.fracAt < slot.size && slot[ff.fracAt] === ff.frac) slot.removeAt(ff.fracAt)
                index = ff.fracAt
                charOff = 0
                mergeNumbersAtCursor()
                return
            }
        }
        if (index > 0) {
            index--
            val prev = slot[index]
            if (prev is FItem.Num && prev.text.length > 1) {
                prev.text = prev.text.dropLast(1)
                charOff = prev.text.length
                return
            }
            slot.removeAt(index)
            charOff = 0
            mergeNumbersAtCursor()
        }
    }

    fun mergeNumbersAtCursor() {
        val left = slot.getOrNull(index - 1) as? FItem.Num ?: return
        val right = slot.getOrNull(index) as? FItem.Num ?: return
        charOff = left.text.length + charOff.coerceIn(0, right.text.length)
        left.text += right.text
        slot.removeAt(index)
        index--
    }

    fun clear() {
        root.clear()
        moveToEnd()
    }

    fun newFraction() {
        if (stack.size + parenStack.size >= 3) return
        val f = FItem.FracItem()
        index = splitAtCursor()
        slot.add(index, f)
        stack.add(FracFrame(slot, index, f, false))
        slot = f.den; index = 0; charOff = 0
    }

    private val parenStack = mutableListOf<ParenFrame>()

    fun newParen() {
        if (stack.size + parenStack.size >= 3) return
        val p = FItem.ParenItem()
        index = splitAtCursor()
        slot.add(index, p)
        parenStack.add(ParenFrame(slot, index, p))
        slot = p.inner; index = 0; charOff = 0
    }

    fun nextField() {
        val pf = parenStack.lastOrNull()
        if (pf != null && slot === pf.paren.inner) {
            parenStack.removeAt(parenStack.size - 1)
            slot = pf.parent; index = pf.at + 1; charOff = 0
            return
        }
        val top = stack.lastOrNull() ?: return
        if (!top.inNum) {
            stack[stack.size - 1] = top.copy(inNum = true)
            slot = top.frac.num; index = 0; charOff = 0
        } else {
            stack.removeAt(stack.size - 1)
            slot = top.parent; index = top.fracAt + 1; charOff = 0
        }
    }

    fun textLen(): Int = slotLen(root)
    private fun slotLen(items: List<FItem>): Int = items.sumOf {
        when (it) {
            is FItem.Num -> it.text.length
            is FItem.Op -> 1
            is FItem.FracItem -> slotLen(it.num) + slotLen(it.den) + 2
            is FItem.ParenItem -> slotLen(it.inner) + 2
        }
    }

    fun setCursor(slot: MutableList<FItem>, index: Int, char: Int, fracPath: List<FracFrame>, parenPath: List<ParenFrame>) {
        this.slot = slot
        this.index = index
        this.charOff = char
        stack.clear()
        stack.addAll(fracPath)
        parenStack.clear()
        parenStack.addAll(parenPath)
    }
}


/** 把历史结果字符串（整数或 a/b）还原成可编辑的表达式项 */
private fun resultToItem(s: String): FItem {
    val slash = s.indexOf('/')
    if (slash > 0) {
        return FItem.FracItem(
            num = mutableListOf(FItem.Num(s.substring(0, slash))),
            den = mutableListOf(FItem.Num(s.substring(slash + 1)))
        )
    }
    return FItem.Num(s)
}

/* ===================== 布局引擎（自绘 + 命中同一套几何） ===================== */

private const val BAR_GAP = 6f

private class DText(val layout: TextLayoutResult, val x: Float, val y: Float)
private class DBar(val x: Float, val y: Float, val w: Float)

private class SlotGeometry(
    val items: MutableList<FItem>,
    val top: Float,
    val fracPath: List<FracFrame>,
    val parenPath: List<ParenFrame>
) {
    val boundaries = mutableListOf<Float>()
    val digitStops = mutableMapOf<Int, List<Float>>()
}

private class InsertionPoint(val slot: SlotGeometry, val index: Int)

private class CTarget(
    val rect: Rect,
    val token: FItem?,
    val before: InsertionPoint,
    val after: InsertionPoint = before,
    val digitStops: List<Float> = emptyList()
)

private class LayoutResult(
    val width: Float,
    val height: Float,
    val texts: List<DText>,
    val bars: List<DBar>,
    val targets: List<CTarget>,
    val slots: List<SlotGeometry>,
    val lineHeight: Float,
    val caretWidth: Float
)

private class LayoutEngine(private val tm: TextMeasurer, private val style: TextStyle) {
    private val zero = tm.measure(AnnotatedString("0"), style)
    private val lineH = zero.size.height.toFloat()
    private val caretWidth = with(zero.layoutInput.density) { 2.dp.toPx() }
    private val emptyWidth = maxOf(with(zero.layoutInput.density) { 16.dp.toPx() }, zero.size.width * 0.75f)
    private val texts = mutableListOf<DText>()
    private val bars = mutableListOf<DBar>()
    private val targets = mutableListOf<CTarget>()
    private val slots = mutableListOf<SlotGeometry>()
    private val measuredText = mutableMapOf<String, TextLayoutResult>()

    private fun textLayout(s: String) = measuredText.getOrPut(s) {
        tm.measure(AnnotatedString(s), style, softWrap = false, maxLines = 1)
    }

    private fun tw(s: String) = textLayout(s).size.width.toFloat()

    private fun measure(items: List<FItem>): Pair<Float, Float> {
        if (items.isEmpty()) return emptyWidth to lineH
        var w = 0f
        var h = lineH
        for (item in items) {
            when (item) {
                is FItem.Num -> w += tw(item.text)
                is FItem.Op -> w += tw(item.sym)
                is FItem.FracItem -> {
                    val (nw, nh) = measure(item.num)
                    val (dw, dh) = measure(item.den)
                    w += maxOf(nw, dw) + 8f
                    h = maxOf(h, nh + BAR_GAP + dh)
                }
                is FItem.ParenItem -> {
                    val (iw, ih) = measure(item.inner)
                    w += tw("(") + iw + tw(")")
                    h = maxOf(h, ih)
                }
            }
        }
        return w to h
    }

    fun build(root: MutableList<FItem>): LayoutResult {
        val (w, h) = measure(root)
        val rootSlot = place(root, 0f, 0f, h, emptyList(), emptyList())
        targets.add(CTarget(Rect(w, 0f, w + caretWidth, h), root.lastOrNull(), InsertionPoint(rootSlot, root.size)))
        return LayoutResult(w + caretWidth, h, texts, bars, targets, slots, lineH, caretWidth)
    }

    private fun place(items: MutableList<FItem>, x0: Float, y0: Float, rowH: Float, fp: List<FracFrame>, pp: List<ParenFrame>): SlotGeometry {
        var x = x0
        val ty = y0 + (rowH - lineH) / 2f
        val slot = SlotGeometry(items, ty, fp, pp)
        slots.add(slot)
        if (items.isEmpty()) {
            slot.boundaries.add(x0 + (emptyWidth - caretWidth) / 2f)
            targets.add(CTarget(Rect(x0, ty, x0 + emptyWidth, ty + lineH), null, InsertionPoint(slot, 0)))
            return slot
        }
        items.forEachIndexed { idx, item ->
            slot.boundaries.add(x)
            val before = InsertionPoint(slot, idx)
            val after = InsertionPoint(slot, idx + 1)
            when (item) {
                is FItem.Num -> {
                    val shaped = textLayout(item.text)
                    val w = shaped.size.width.toFloat()
                    texts.add(DText(shaped, x, ty))
                    val stops = (0..item.text.length).map { x + shaped.getHorizontalPosition(it, usePrimaryDirection = true) }
                    slot.digitStops[idx] = stops
                    targets.add(CTarget(Rect(x, ty, x + w, ty + lineH), item, before, digitStops = stops))
                    x += w
                }
                is FItem.Op -> {
                    val w = tw(item.sym)
                    texts.add(DText(textLayout(item.sym), x, ty))
                    targets.add(CTarget(Rect(x, ty, x + w, ty + lineH), item, before, after))
                    x += w
                }
                is FItem.FracItem -> {
                    val (nw, nh) = measure(item.num)
                    val (dw, dh) = measure(item.den)
                    val fw = maxOf(nw, dw) + 8f
                    val fh = nh + BAR_GAP + dh
                    val fy = y0 + (rowH - fh) / 2f
                    place(item.num, x + 4f + (fw - 8f - nw) / 2f, fy, nh, fp + FracFrame(items, idx, item, true), pp)
                    bars.add(DBar(x, fy + nh + BAR_GAP / 2f, fw))
                    place(item.den, x + 4f + (fw - 8f - dw) / 2f, fy + nh + BAR_GAP, dh, fp + FracFrame(items, idx, item, false), pp)
                    targets.add(CTarget(Rect(x, fy, x + 4f, fy + fh), item, before))
                    targets.add(CTarget(Rect(x + fw - 4f, fy, x + fw, fy + fh), item, after))
                    x += fw
                }
                is FItem.ParenItem -> {
                    val (iw, ih) = measure(item.inner)
                    val lw = tw("(")
                    val rw = tw(")")
                    val ph = maxOf(lineH, ih)
                    val py = y0 + (rowH - ph) / 2f
                    val ip = pp + ParenFrame(items, idx, item)
                    texts.add(DText(textLayout("("), x, py + (ph - lineH) / 2f))
                    val inner = place(item.inner, x + lw, py + (ph - ih) / 2f, ih, fp, ip)
                    targets.add(CTarget(Rect(x, py, x + lw, py + ph), item, before, InsertionPoint(inner, 0)))
                    texts.add(DText(textLayout(")"), x + lw + iw, py + (ph - lineH) / 2f))
                    targets.add(CTarget(Rect(x + lw + iw, py, x + lw + iw + rw, py + ph), item, InsertionPoint(inner, item.inner.size), after))
                    x += lw + iw + rw
                }
            }
        }
        slot.boundaries.add(x)
        return slot
    }
}

private fun targetAt(layout: LayoutResult, pos: Offset): CTarget? =
    layout.targets.firstOrNull { it.rect.contains(pos) } ?: layout.targets.minByOrNull {
        val dx = pos.x - pos.x.coerceIn(it.rect.left, it.rect.right)
        val dy = pos.y - pos.y.coerceIn(it.rect.top, it.rect.bottom)
        dx * dx + dy * dy
    }

private fun tokenAt(layout: LayoutResult, pos: Offset): FItem? = targetAt(layout, pos)?.token

private class DisplayTap(val time: Long, val position: Offset, val revision: Int, val token: FItem?, val wasResult: Boolean)

private fun hitTest(layout: LayoutResult, editor: FracEditor, pos: Offset): FItem? {
    val target = targetAt(layout, pos) ?: return null
    val point = if (pos.x < target.rect.center.x) target.before else target.after
    val offset = target.digitStops.indices.minByOrNull { kotlin.math.abs(target.digitStops[it] - pos.x) } ?: 0
    editor.setCursor(point.slot.items, point.index, offset, point.slot.fracPath, point.slot.parenPath)
    return target.token
}

private fun cursorRectOf(layout: LayoutResult, editor: FracEditor): Rect? {
    val slot = layout.slots.firstOrNull { it.items === editor.slot } ?: return null
    val stops = slot.digitStops[editor.index]
    val x = stops?.get(editor.charOff.coerceIn(0, stops.lastIndex)) ?: slot.boundaries.getOrNull(editor.index) ?: return null
    return Rect(x, slot.top, x + layout.caretWidth, slot.top + layout.lineHeight)
}


private val binaryDisplayOperator = Regex("""(?<=[0-9)])([+×÷/\-])""")

private fun formatDisplayExpression(expression: String): String =
    binaryDisplayOperator.replace(expression) { " ${it.value} " }.replace('-', '−')

@Composable
private fun CursorBar(style: TextStyle, color: Color) {
    val alpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse)
    )
    val h = with(LocalDensity.current) { style.fontSize.toDp() }
    Box(
        Modifier.padding(horizontal = 1.dp).width(2.dp).height(h * 1.05f)
            .background(color.copy(alpha = alpha))
    )
}

private class HitBox(
    val slot: MutableList<FItem>,
    val index: Int,
    val fracPath: List<FracFrame>,
    val parenPath: List<ParenFrame>,
    val rect: Rect,
    val parenExit: FItem.ParenItem? = null,
    val parenEnter: FItem.ParenItem? = null
)

private fun cursorToPointer(
    hitBoxes: List<HitBox>,
    editor: FracEditor,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    rootPos: Offset
) {
    if (hitBoxes.isEmpty()) return
    fun dist(h: HitBox): Float {
        val dy = if (rootPos.y < h.rect.top) h.rect.top - rootPos.y else if (rootPos.y > h.rect.bottom) rootPos.y - h.rect.bottom else 0f
        val dx = if (rootPos.x < h.rect.left) h.rect.left - rootPos.x else if (rootPos.x > h.rect.right) rootPos.x - h.rect.right else 0f
        return dy * 100f + dx
    }
    val exact = hitBoxes.firstOrNull { it.parenExit == null && it.rect.contains(rootPos) }
    val closeHit = hitBoxes.firstOrNull { it.parenExit != null && it.rect.contains(rootPos) }
    val hit = exact
        ?: closeHit
        ?: hitBoxes.filter { it.parenExit == null }.minByOrNull { dist(it) }
        ?: hitBoxes.minByOrNull { dist(it) }
        ?: return
    val num = hit.slot.getOrNull(hit.index) as? FItem.Num
    if (num == null) {
        editor.setCursor(hit.slot, hit.index, 0, hit.fracPath, hit.parenPath)
        return
    }
    val relX = rootPos.x - hit.rect.left
    var best = 0
    var bestD = Float.MAX_VALUE
    for (i in 0..num.text.length) {
        val w = textMeasurer.measure(AnnotatedString(num.text.substring(0, i)), style).size.width.toFloat()
        val d = kotlin.math.abs(w - relX)
        if (d < bestD) { bestD = d; best = i }
    }
    editor.setCursor(hit.slot, hit.index, best, hit.fracPath, hit.parenPath)
}
@Composable
private fun FracSlot(
    items: MutableList<FItem>,
    editor: FracEditor,
    style: TextStyle,
    barColor: Color,
    path: List<FracFrame>,
    parenPath: List<ParenFrame>,
    hitBoxes: MutableList<HitBox>
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { idx, item ->
            val here = items === editor.slot && idx == editor.index
            when (item) {
                is FItem.Num -> {
                    Row(Modifier.onGloballyPositioned { c ->
                        val r = c.boundsInRoot()
                        hitBoxes.removeAll { it.slot === items && it.index == idx }
                        hitBoxes.add(HitBox(items, idx, path, parenPath, r))
                    }) {
                        if (here) {
                            val off = editor.charOff.coerceIn(0, item.text.length)
                            BasicText(item.text.substring(0, off), style = style, softWrap = false)
                            CursorBar(style, barColor)
                            BasicText(item.text.substring(off), style = style, softWrap = false)
                        } else {
                            BasicText(item.text, style = style, softWrap = false)
                        }
                    }
                }
                is FItem.Op -> {
                    if (here && editor.charOff == 0) CursorBar(style, barColor)
                    BasicText(
                        item.sym,
                        modifier = Modifier.onGloballyPositioned { c ->
                            hitBoxes.removeAll { it.slot === items && it.index == idx && it.parenExit == null && it.parenEnter == null }
                            hitBoxes.add(HitBox(items, idx, path, parenPath, c.boundsInRoot()))
                        },
                        style = style,
                        softWrap = false
                    )
                }
                is FItem.FracItem -> {
                    Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            FracSlot(item.num, editor, style, barColor, path + FracFrame(items, idx, item, true), parenPath, hitBoxes)
                        }
                        Box(Modifier.fillMaxWidth().height(1.5.dp).background(barColor))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            FracSlot(item.den, editor, style, barColor, path + FracFrame(items, idx, item, false), parenPath, hitBoxes)
                        }
                    }
                }
                is FItem.ParenItem -> {
                    val innerParenPath = parenPath + ParenFrame(items, idx, item)
                    BasicText(
                        "(",
                        modifier = Modifier.onGloballyPositioned { c ->
                            hitBoxes.removeAll { it.parenEnter === item }
                            hitBoxes.add(HitBox(item.inner, 0, path, innerParenPath, c.boundsInRoot(), parenEnter = item))
                        },
                        style = style,
                        softWrap = false
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FracSlot(item.inner, editor, style, barColor, path, innerParenPath, hitBoxes)
                        Box(
                            Modifier
                                .width(12.dp)
                                .height(with(LocalDensity.current) { style.fontSize.toDp() })
                                .onGloballyPositioned { c ->
                                    hitBoxes.removeAll { it.slot === item.inner && it.index == item.inner.size && it.parenExit == null && it.parenEnter == null }
                                    hitBoxes.add(HitBox(item.inner, item.inner.size, path, innerParenPath, c.boundsInRoot()))
                                }
                        )
                    }
                    BasicText(
                        ")",
                        modifier = Modifier.onGloballyPositioned { c ->
                            val r = c.boundsInRoot()
                            hitBoxes.removeAll { it.parenExit === item }
                            hitBoxes.add(HitBox(items, idx + 1, path, parenPath, r, parenExit = item))
                        },
                        style = style,
                        softWrap = false
                    )
                }
            }
        }
        if (items === editor.slot && editor.index >= items.size) CursorBar(style, barColor)
    }
}

@Composable
fun CalculatorApp() {
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calc_prefs", 0) }

    var oobeDone by remember { mutableStateOf(prefs.getBoolean("oobe_done", false)) }
    var language by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }

    val defaultWallpaper = remember {
        try { BitmapFactory.decodeResource(context.resources, R.drawable.default_wallpaper) } catch (e: Exception) { null }
    }

    val scope = rememberCoroutineScope()
    val oobeExit = remember { Animatable(if (oobeDone) 0f else 1f) }
    val mainEnter = remember { Animatable(if (oobeDone) 1f else 0f) }

    if (!oobeDone) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = oobeExit.value
                scaleY = oobeExit.value
                alpha = oobeExit.value
            }
        ) {
            OobeScreen(defaultWallpaper, onDone = { code ->
                language = code
                prefs.edit().putString("language", code).putBoolean("oobe_done", true).apply()
                syncLauncherAlias(context, code)
                scope.launch {
                    oobeExit.animateTo(0f, tween(350))
                    oobeDone = true
                    mainEnter.animateTo(1f, tween(500))
                }
            })
        }
        return
    }

    val lang = Lang.values().find { it.code == language } ?: Lang.EN
    val str = stringsFor(lang)

    var darkMode by remember { mutableStateOf(prefs.getString("dark_mode", "manual") ?: "manual") }
    var manualDark by remember { mutableStateOf(prefs.getBoolean("manual_dark", true)) }
    var wallpaperPath by remember { mutableStateOf(prefs.getString("wallpaper", null)) }
    val wallpaper = remember(wallpaperPath) { wallpaperPath?.let { decodeWallpaper(it) } }
    val effectiveWallpaper = wallpaper ?: defaultWallpaper
    val wallpaperIsDark = remember(effectiveWallpaper) { effectiveWallpaper?.let(::isWallpaperDark) }

    val dark = when (darkMode) {
        "system" -> isSystemInDarkTheme()
        "wallpaper" -> wallpaperIsDark ?: manualDark
        else -> manualDark
    }
    val gradientColors = if (dark) DarkGradient else LightGradient
    val fg = if (dark) Color.White else Color(0xFF15151A)
    val buttonSurface = if (dark) Color(0xFF0D0D10).copy(alpha = 0.5f) else Color(0xFFFFFFFF).copy(alpha = 0.42f)
    val lightDisplayText = wallpaperIsDark ?: dark
    val displayColor = if (lightDisplayText) Color(0xFFF4F6FA) else Color(0xFF181B20)

    var showMenu by remember { mutableStateOf(false) }
    var showKeyShape by remember { mutableStateOf(false) }
    var keyShape by remember { mutableStateOf(prefs.getString("key_shape", "circle") ?: "circle") }
    var historyExpanded by remember { mutableStateOf(false) }
    val islandBlur by androidx.compose.animation.core.animateDpAsState(if (historyExpanded) 10.dp else 0.dp, label = "island-blur")
    val islandScrim by androidx.compose.animation.core.animateFloatAsState(if (historyExpanded) 0.12f else 0f, label = "island-scrim")
    var showVibration by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    var currentDir by remember { mutableStateOf(File(Environment.getExternalStorageDirectory().path)) }

    var vibrationLevel by remember { mutableStateOf(prefs.getString("vibration_level", "standard") ?: "standard") }
    val haptic = LocalHapticFeedback.current

    val editor = remember { FracEditor() }
    var revision by remember { mutableStateOf(0) }
    var resultRevision by remember { mutableStateOf(-1) }
    var evaluationFailed by remember { mutableStateOf(false) }
    var selectionDocument by remember { mutableStateOf<SelectableExpression?>(null) }
    var selectedRange by remember { mutableStateOf(TextRange.Zero) }
    val history = remember {
        mutableStateListOf<HistoryItem>().apply {
            addAll(deserializeHistory(prefs.getString("history", "") ?: ""))
        }
    }

    fun persistHistory() {
        prefs.edit().putString("history", serializeHistory(history.toList())).apply()
    }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    fun selectValue(item: FItem? = null) {
        val document = SelectableExpression(editor)
        selectedRange = if (resultRevision == revision) document.all else document.rangeOf(item)
        selectionDocument = document
    }

    fun prepareInput(startNewResult: Boolean = false): Boolean {
        val document = selectionDocument
        if (document != null) {
            document.prepareEdit(editor, selectedRange)
            selectionDocument = null
            return !selectedRange.collapsed
        }
        if (resultRevision == revision && (startNewResult || evaluationFailed)) editor.clear()
        return false
    }

    fun useResult(result: String) {
        prepareInput(startNewResult = true)
        val item = resultToItem(result)
        if (editor.root.isEmpty()) {
            editor.root.add(item)
            editor.index = 1
            editor.charOff = 0
        } else {
            if (editor.slot.getOrNull(editor.index) is FItem.Num) editor.index += 1
            editor.slot.add(editor.index, item)
            editor.index += 1
            editor.charOff = 0
        }
        revision++
    }

    fun onDigit(d: String) {
        prepareInput(startNewResult = true)
        editor.insertChar(d[0])
        revision++
    }

    fun onDot() {
        val replaced = prepareInput(startNewResult = true)
        val number = editor.slot.getOrNull(editor.index) as? FItem.Num
            ?: editor.slot.getOrNull(editor.index - 1) as? FItem.Num
        if (number?.text?.contains('.') == true) {
            if (replaced) revision++
            return
        }
        if (number == null) editor.insertChar('0')
        editor.insertChar('.')
        revision++
    }

    fun onOperator(op: String) {
        prepareInput()
        editor.insertOp(op)
        revision++
    }

    fun onBackspace() {
        if (!prepareInput()) editor.backspace()
        revision++
    }

    fun onClear() {
        selectionDocument = null
        editor.clear()
        revision++
    }

    fun onFraction() {
        prepareInput(startNewResult = true)
        editor.newFraction()
        revision++
    }

    fun onParen() {
        prepareInput(startNewResult = true)
        editor.newParen()
        revision++
    }

    fun onNextField() {
        selectionDocument = null
        editor.nextField()
        revision++
    }

    fun onEquals() {
        selectionDocument = null
        val text = exprText(editor.root)
        when (val r = evaluate(editor.root)) {
            is RationalResult.Ok -> {
                evaluationFailed = false
                history.add(0, HistoryItem(text, r.display()))
                while (history.size > 10) history.removeAt(history.size - 1)
                persistHistory()
                editor.clear()
                if (r.isInteger) {
                    editor.root.add(FItem.Num(r.numerator.toString()))
                } else {
                    editor.root.add(FItem.FracItem(
                        num = mutableListOf(FItem.Num(r.numerator.toString())),
                        den = mutableListOf(FItem.Num(r.denominator.toString()))
                    ))
                }
                editor.index = editor.root.size
                editor.charOff = 0
            }
            RationalResult.Error -> {
                evaluationFailed = true
                editor.clear()
                editor.root.add(FItem.Num(str.error))
                editor.index = 1
                editor.charOff = 0
            }
        }
        revision++
        resultRevision = revision
    }

    fun requestWallpaper() {
        if (Environment.isExternalStorageManager()) {
            currentDir = File(Environment.getExternalStorageDirectory().path)
            showWallpaperPicker = true
        } else {
            showPermDialog = true
        }
    }

    fun toggleThemeByLongPress() {
        if (darkMode == "manual") {
            manualDark = !manualDark
            prefs.edit().putBoolean("manual_dark", manualDark).apply()
            hapticType(vibrationLevel, true)?.let { haptic.performHapticFeedback(it) }
        }
    }

    val keypadLights = remember { KeypadLights() }
    CompositionLocalProvider(
        LocalVibrationLevel provides vibrationLevel,
        LocalKeypadLights provides keypadLights,
        LocalKeyStyle provides (if (keyShape == "circle") KeyStyle.Circle else KeyStyle.RoundedRect),
        LocalBackdrop provides backdrop
    ) {
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            scaleX = mainEnter.value
            scaleY = mainEnter.value
            alpha = mainEnter.value
            translationY = (1f - mainEnter.value) * 60.dp.toPx()
        }.onPlaced { keypadLights.plane = it }
    ) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (effectiveWallpaper != null) {
                Image(
                    bitmap = effectiveWallpaper.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradientColors)))
            }
        }

        if (selectionDocument != null) {
            // 选中态下，点空白区域即可退出选择、恢复原样
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTapGestures { selectionDocument = null }
                }
            )
        }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    str.title,
                    style = TextStyle(color = displayColor, fontSize = 19.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Box(Modifier.weight(1f))
                Box(Modifier.size(44.dp))
            }

            val fontSp = when {
                editor.textLen() > 10 -> 40.sp
                editor.textLen() > 7 -> 48.sp
                else -> 62.sp
            }
            val density = LocalDensity.current
            val resultShadow = with(density) {
                Shadow(
                    color = if (lightDisplayText) Color(0x99070D19) else Color(0x90FFFFFF),
                    offset = Offset(0f, 0.8.dp.toPx()),
                    blurRadius = 1.8.dp.toPx()
                )
            }
            val dispStyle = TextStyle(color = displayColor, fontSize = fontSp, fontFamily = CalculatorFont, shadow = resultShadow)
            val textMeasurer = rememberTextMeasurer()
            val rev = revision
            val layout = remember(rev, textMeasurer, dispStyle) {
                if (editor.root.isEmpty()) null else LayoutEngine(textMeasurer, dispStyle).build(editor.root)
            }
            val expression = remember(rev) { exprText(editor.root) }
            val document = selectionDocument
            val complete = resultRevision == rev
            val gestureConfig = LocalViewConfiguration.current
            var previousTap by remember { mutableStateOf<DisplayTap?>(null) }
            val editing = document == null && layout != null && !complete
            val scroll = rememberScrollState()
            val caret = if (editing) cursorRectOf(layout, editor) else null
            var blinkOn by remember(rev, editing) { mutableStateOf(true) }
            LaunchedEffect(rev, editing) {
                while (editing) {
                    delay(530)
                    blinkOn = !blinkOn
                }
            }
            LaunchedEffect(rev, layout, editing, scroll.viewportSize, scroll.maxValue) {
                val viewport = scroll.viewportSize
                if (document != null || viewport <= 0 || scroll.maxValue == Int.MAX_VALUE) return@LaunchedEffect
                if (complete) {
                    scroll.scrollTo(scroll.maxValue)
                } else if (caret != null) {
                    val margin = with(density) { 6.dp.toPx() }
                    val target = when {
                        caret.right + margin > scroll.value + viewport -> caret.right + margin - viewport
                        caret.left - margin < scroll.value -> caret.left - margin
                        else -> return@LaunchedEffect
                    }
                    scroll.scrollTo(target.toInt().coerceIn(0, scroll.maxValue))
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(end = 10.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (selectionDocument != null) {
                    Box(
                        Modifier.matchParentSize().pointerInput(Unit) {
                            detectTapGestures { selectionDocument = null }
                        }
                    )
                }
                val last = history.firstOrNull()
                val showExpression = complete && !evaluationFailed && document == null && layout != null &&
                    maxHeight >= with(density) { layout.height.toDp() + 26.sp.toDp() }
                val leaveHandleSpace = maxHeight >= 160.dp
                Column(horizontalAlignment = Alignment.End) {
                    if (showExpression && last != null) {
                        SelectionContainer {
                            BasicText(
                                remember(last.expr) { formatDisplayExpression(last.expr) },
                                modifier = Modifier.padding(bottom = 3.dp),
                                style = TextStyle(color = displayColor.copy(alpha = 0.90f), fontSize = 17.sp, shadow = resultShadow),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (document != null) {
                        DisplaySelection(
                            document.text, selectedRange, { selectedRange = it }, dispStyle, displayColor,
                            lightDisplayText, str.copy, str.selectAll, str.done, { selectionDocument = null }
                        )
                        if (leaveHandleSpace) Spacer(Modifier.height(18.dp))
                    } else if (layout == null) {
                        BasicText("0", style = dispStyle, modifier = Modifier.semantics {
                            onLongClick(str.selectValue) { selectValue(); true }
                        }.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { selectValue() }, onDoubleTap = { selectValue() })
                        })
                    } else {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(scroll),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(
                                Modifier
                                    .size(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() })
                                    .semantics {
                                        contentDescription = expression
                                        onLongClick(str.selectValue) { selectValue(); true }
                                    }
                                    .pointerInput(layout, rev, complete, gestureConfig) {
                                        detectTapGestures(
                                            onLongPress = { offset ->
                                                previousTap = null
                                                selectValue(tokenAt(layout, offset))
                                            },
                                            onTap = { offset ->
                                                if (selectionDocument != null) {
                                                    selectionDocument = null
                                                    return@detectTapGestures
                                                }
                                                val now = SystemClock.uptimeMillis()
                                                val lastTap = previousTap
                                                val token = tokenAt(layout, offset)
                                                if (lastTap != null && lastTap.revision == rev && lastTap.token === token &&
                                                    now - lastTap.time <= gestureConfig.doubleTapTimeoutMillis &&
                                                    (offset - lastTap.position).getDistance() <= gestureConfig.touchSlop * 2) {
                                                    previousTap = null
                                                    if (lastTap.wasResult) {
                                                        editor.moveToEnd()
                                                        resultRevision = rev
                                                    }
                                                    selectValue(token)
                                                } else {
                                                    // Place the caret immediately so the next keypad press uses this tap.
                                                    hitTest(layout, editor, offset)
                                                    revision++
                                                    previousTap = DisplayTap(now, offset, revision, token, complete)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                layout.texts.forEach { drawText(it.layout, topLeft = Offset(it.x, it.y)) }
                                layout.bars.forEach { drawRect(displayColor, topLeft = Offset(it.x, it.y), size = Size(it.w, 1.5.dp.toPx())) }
                                if (caret != null && blinkOn) {
                                    val inset = caret.height * 0.13f
                                    drawRoundRect(
                                        displayColor, topLeft = Offset(caret.left, caret.top + inset),
                                        size = Size(caret.width, caret.height - inset * 2),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(caret.width / 2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row {
                GlassButton(
                    "AC", backdrop, Modifier.weight(2f).aspectRatio(2f), ::onClear, ::onParen, buttonSurface, fg,
                    strongHaptic = true, onClickLabel = str.clear, onLongClickLabel = str.insertParen
                ) {
                    BasicText(
                        "AC",
                        style = TextStyle(color = fg, fontFamily = CalculatorFont, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1, softWrap = false
                    )
                }
                GlassButton("÷", backdrop, Modifier.weight(1f).aspectRatio(1f), { if (selectionDocument == null && editor.inFraction()) onNextField() else onOperator("÷") }, { onFraction() }, buttonSurface, fg, symbolLabel = str.divide)
                GlassButton("×", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("×") }, null, buttonSurface, fg, symbolLabel = str.multiply)
            }
            Row {
                GlassButton("7", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("7") }, null, buttonSurface, fg)
                GlassButton("8", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("8") }, null, buttonSurface, fg)
                GlassButton("9", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("9") }, null, buttonSurface, fg)
                GlassButton("-", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("-") }, null, buttonSurface, fg, symbolLabel = str.subtract)
            }
            Row {
                GlassButton("4", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("4") }, null, buttonSurface, fg)
                GlassButton("5", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("5") }, null, buttonSurface, fg)
                GlassButton("6", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("6") }, null, buttonSurface, fg)
                GlassButton("+", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("+") }, null, buttonSurface, fg, symbolLabel = str.add)
            }
            Row {
                Column(Modifier.weight(3f)) {
                    Row {
                        GlassButton("1", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("1") }, null, buttonSurface, fg)
                        GlassButton("2", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("2") }, null, buttonSurface, fg)
                        GlassButton("3", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("3") }, null, buttonSurface, fg)
                    }
                    Row {
                        GlassButton("0", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("0") }, null, buttonSurface, fg)
                        GlassButton(".", backdrop, Modifier.weight(1f).aspectRatio(1f), ::onDot, null, buttonSurface, fg)
                        GlassButton("⌫", backdrop, Modifier.weight(1f).aspectRatio(1f), ::onBackspace, null, buttonSurface, fg) {
                            val iconSize = with(density) { Modifier.size(32.sp.toDp(), 24.sp.toDp()) }
                            Image(rememberVectorPainter(BackspaceIcon), str.backspace, iconSize, colorFilter = ColorFilter.tint(fg))
                        }
                    }
                }
                GlassButton("=", backdrop, Modifier.weight(1f).aspectRatio(0.5f), ::onEquals, ::toggleThemeByLongPress, buttonSurface, fg, strongHaptic = true, symbolLabel = str.equalsLabel)
            }
        }
        if (historyExpanded) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { historyExpanded = false }
                }
            )
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val cw = maxWidth
            val ch = maxHeight
            Box(Modifier.padding(top = 8.dp)) {
                HistoryButton(
                    history = history,
                    backdrop = backdrop,
                    fg = displayColor,
                    dark = dark,
                    title = str.history,
                    useResultLabel = str.useResult,
                    collapsedMaxWidth = (cw - 200.dp).coerceAtLeast(96.dp),
                    expandedWidth = cw - 32.dp,
                    maxPanelHeight = (ch - 170.dp).coerceAtLeast(220.dp),
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded },
                    onClear = ::clearHistory,
                    onUseResult = ::useResult
                )
            }
        }

        key(keyShape) {
        Box(Modifier.fillMaxSize()) {
            if (showMenu) {
                GlassMenuScrim { showMenu = false }
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().safeDrawingPadding(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(Modifier.padding(top = 8.dp, end = 12.dp)) {
                    GlassMenu(
                        visible = showMenu,
                        backdrop = backdrop,
                        surfaceColor = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.16f),
                        targetWidth = 236.dp,
                        targetHeight = 520.dp,
                        onToggle = { showMenu = !showMenu },
                        itemCount = 7,
                        itemHeight = 60.dp,
                        headerHeight = 54.dp,
                        onSelect = { idx ->
                            when (idx) {
                                0 -> { showMenu = false; showSettings = true }
                                1 -> { showMenu = false; requestWallpaper() }
                                2 -> { showMenu = false; showVibration = true }
                                3 -> { showMenu = false; showKeyShape = true }
                                4 -> { showMenu = false; showLanguage = true }
                                5 -> { showMenu = false; showIntro = true }
                                6 -> { showMenu = false; showAbout = true }
                            }
                        },
                        onHoverChange = { hapticType(vibrationLevel, true)?.let { haptic.performHapticFeedback(it) } },
                    ) {
                        BasicText(
                            str.settings,
                            style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                        DialogItem(str.darkMode, glass = false) { showMenu = false; showSettings = true }
                        DialogItem(str.wallpaper, glass = false) { showMenu = false; requestWallpaper() }
                        DialogItem(str.vibration, glass = false) { showMenu = false; showVibration = true }
                        DialogItem(keyShapeText(lang, "title"), glass = false) { showMenu = false; showKeyShape = true }
                        DialogItem(str.language, glass = false) { showMenu = false; showLanguage = true }
                        DialogItem(featureIntroFor(lang).title, glass = false) { showMenu = false; showIntro = true }
                        DialogItem(str.about, glass = false) { showMenu = false; showAbout = true }
                    }
                }
            }
        }

            Spacer(Modifier.matchParentSize().graphicsLayer().drawNeighborReflections(keypadLights, dark, keyShape == "circle"))
        }
    }

    if (showVibration) {
        Dialog(onDismissRequest = { showVibration = false }) {
            DialogPanel {
                BasicText(str.vibration, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.off, selected = vibrationLevel == "off") {
                    vibrationLevel = "off"
                    prefs.edit().putString("vibration_level", "off").apply()
                    showVibration = false
                }
                DialogItem(str.standard, selected = vibrationLevel == "standard") {
                    vibrationLevel = "standard"
                    prefs.edit().putString("vibration_level", "standard").apply()
                    showVibration = false
                }
                DialogItem(str.enhanced, selected = vibrationLevel == "enhanced") {
                    vibrationLevel = "enhanced"
                    prefs.edit().putString("vibration_level", "enhanced").apply()
                    showVibration = false
                }
                DialogItem(str.cancel, isCancel = true) { showVibration = false }
            }
        }
    }
    if (showKeyShape) {
        Dialog(onDismissRequest = { showKeyShape = false }) {
            DialogPanel {
                BasicText(
                    keyShapeText(lang, "title"),
                    style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                DialogItem(keyShapeText(lang, "circle"), selected = keyShape == "circle") {
                    keyShape = "circle"
                    prefs.edit().putString("key_shape", "circle").apply()
                    showKeyShape = false
                }
                DialogItem(keyShapeText(lang, "rect"), selected = keyShape != "circle") {
                    keyShape = "rect"
                    prefs.edit().putString("key_shape", "rect").apply()
                    showKeyShape = false
                }
                DialogItem(str.cancel, isCancel = true) { showKeyShape = false }
            }
        }
    }
    if (showLanguage) {
        Dialog(onDismissRequest = { showLanguage = false }) {
            DialogPanel {
                BasicText(str.language, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem("简体中文", selected = language == "zh-CN") {
                    language = "zh-CN"
                    prefs.edit().putString("language", "zh-CN").apply()
                    syncLauncherAlias(context, "zh-CN")
                    showLanguage = false
                }
                DialogItem("繁體中文", selected = language == "zh-TW") {
                    language = "zh-TW"
                    prefs.edit().putString("language", "zh-TW").apply()
                    syncLauncherAlias(context, "zh-TW")
                    showLanguage = false
                }
                DialogItem("English", selected = language == "en") {
                    language = "en"
                    prefs.edit().putString("language", "en").apply()
                    syncLauncherAlias(context, "en")
                    showLanguage = false
                }
                DialogItem(str.cancel, isCancel = true) { showLanguage = false }
            }
        }
    }
    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            DialogPanel {
                BasicText(str.darkMode, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.followSystem, selected = darkMode == "system") {
                    darkMode = "system"
                    prefs.edit().putString("dark_mode", "system").apply()
                    showSettings = false
                }
                DialogItem(str.wallpaperColor, selected = darkMode == "wallpaper") {
                    darkMode = "wallpaper"
                    prefs.edit().putString("dark_mode", "wallpaper").apply()
                    showSettings = false
                }
                DialogItem(str.none, selected = darkMode == "manual") {
                    darkMode = "manual"
                    prefs.edit().putString("dark_mode", "manual").apply()
                    showSettings = false
                }
                DialogItem(str.cancel, isCancel = true) { showSettings = false }
            }
        }
    }
    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            DialogPanel {
                BasicText(str.version, style = TextStyle(color = Color.White, fontSize = 20.sp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.ok, isCancel = true) { showAbout = false }
            }
        }
    }
    if (showIntro) {
        val intro = featureIntroFor(lang)
        Dialog(onDismissRequest = { showIntro = false }) {
            DialogPanel {
                BasicText(intro.title, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                intro.lines.forEach { line ->
                    BasicText(line, style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 3.dp))
                }
                DialogItem(str.ok, isCancel = true) { showIntro = false }
            }
        }
    }
    if (showPermDialog) {
        Dialog(onDismissRequest = { showPermDialog = false }) {
            DialogPanel {
                BasicText(str.permText, style = TextStyle(color = Color.White, fontSize = 16.sp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.grant) {
                    showPermDialog = false
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }
                DialogItem(str.cancel, isCancel = true) { showPermDialog = false }
            }
        }
    }
    if (showWallpaperPicker) {
        WallpaperPicker(
            str = str,
            currentDir = currentDir,
            onDirChange = { currentDir = it },
            onPick = { path ->
                wallpaperPath = path
                prefs.edit().putString("wallpaper", path).apply()
                showWallpaperPicker = false
            },
            onReset = {
                wallpaperPath = null
                prefs.edit().remove("wallpaper").apply()
                showWallpaperPicker = false
            },
            onClose = { showWallpaperPicker = false }
        )
    }
    }
}

@Composable
private fun OobeScreen(wallpaper: Bitmap?, onDone: (String) -> Unit) {
    var selected by remember { mutableStateOf("en") }
    var showIntro by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF16324F), Color(0xFF0A4D8C), Color(0xFF3A2E6E), Color(0xFF16324F)))))
            }
        }

        if (!showIntro) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    "选择语言 / Select language",
                    style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
                Box(Modifier.height(24.dp))
                OobeOption(backdrop, "简体中文", selected == "zh-CN") { selected = "zh-CN" }
                OobeOption(backdrop, "繁體中文", selected == "zh-TW") { selected = "zh-TW" }
                OobeOption(backdrop, "English", selected == "en") { selected = "en" }
                Box(Modifier.height(32.dp))
                OobeConfirmButton(backdrop, "开始 / Start", onClick = { showIntro = true })
            }
        } else {
            val intro = featureIntroFor(Lang.values().find { it.code == selected } ?: Lang.EN)
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    intro.title,
                    style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
                Box(Modifier.height(20.dp))
                intro.lines.forEach { line ->
                    BasicText(
                        line,
                        style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                Box(Modifier.height(28.dp))
                OobeConfirmButton(backdrop, intro.continueLabel, onClick = { onDone(selected) })
            }
        }
    }
}

@Composable
private fun OobeOption(backdrop: Backdrop, text: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vibrationLevel = LocalVibrationLevel.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f)
    )

    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(16.dp) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(14f.dp.toPx(), 22f.dp.toPx())
                },
                layerBlock = {
                    val s = 1f + 0.08f * press
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = {
                    drawRect(if (selected) Color(0xFF0A84FF).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f))
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticType(vibrationLevel, false)?.let { haptic.performHapticFeedback(it) }
                    onClick()
                }
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text + if (selected) "  ✓" else "",
            style = TextStyle(color = Color.White, fontSize = 17.sp)
        )
    }
}

@Composable
private fun OobeConfirmButton(backdrop: Backdrop, text: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val vibrationLevel = LocalVibrationLevel.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f)
    )

    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(24.dp) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(14f.dp.toPx(), 22f.dp.toPx())
                },
                layerBlock = {
                    val s = 1f + 0.08f * press
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = {
                    drawRect(Color(0xFF0A84FF).copy(alpha = 0.5f))
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticType(vibrationLevel, false)?.let { haptic.performHapticFeedback(it) }
                    onClick()
                }
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun HistoryButton(
    history: List<HistoryItem>,
    backdrop: Backdrop,
    fg: Color,
    dark: Boolean,
    title: String,
    useResultLabel: String,
    collapsedMaxWidth: Dp,
    expandedWidth: Dp,
    maxPanelHeight: Dp,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    onUseResult: (String) -> Unit
) {
    var legacyDialog by remember { mutableStateOf(false) }
    val surfaceColor = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.16f)
    HistoryIsland(
        history = history,
        backdrop = backdrop,
        surfaceColor = surfaceColor,
        contentColor = fg,
        collapsedMaxWidth = collapsedMaxWidth,
        expandedWidth = expandedWidth,
        maxPanelHeight = maxPanelHeight,
        expanded = expanded,
        onToggle = onToggle,
        onClear = onClear,
        onUseResult = onUseResult
    )

    if (legacyDialog) {
        Dialog(onDismissRequest = { legacyDialog = false }) {
            val panelBg = if (dark) Color(0xFF1A1A1E).copy(alpha = 0.6f) else Color(0xFFFFFFFF).copy(alpha = 0.6f)
            val panelText = if (dark) Color.White else Color(0xFF1A1A1A)
            Column(
                Modifier.fillMaxWidth().background(panelBg, RoundedCornerShape(20.dp)).padding(20.dp)
            ) {
                if (history.isEmpty()) {
                    BasicText("", style = TextStyle(color = panelText, fontSize = 15.sp))
                } else {
                    history.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                "${item.expr} = ${item.result}",
                                style = TextStyle(color = panelText, fontSize = 15.sp),
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier.clickable {
                                    onUseResult(item.result)
                                    legacyDialog = false
                                }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(useResultLabel, style = TextStyle(color = Color(0xFF0A84FF), fontSize = 13.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperPicker(
    str: Str,
    currentDir: File,
    onDirChange: (File) -> Unit,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val rootPath = remember { Environment.getExternalStorageDirectory().path }
    val context = LocalContext.current
    val files = remember(currentDir) {
        currentDir.listFiles()?.toList()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF151517)).safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clickable {
                        val parent = currentDir.parentFile
                        if (parent != null && currentDir.path != rootPath) onDirChange(parent) else onClose()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("←", style = TextStyle(color = Color.White, fontSize = 22.sp))
                }
                BasicText(
                    currentDir.path,
                    style = TextStyle(color = Color(0xFFAAAAAA), fontSize = 13.sp),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Box(Modifier.clickable { onReset() }.padding(8.dp)) {
                    BasicText(str.restoreDefault, style = TextStyle(color = Color(0xFF4DA3FF), fontSize = 14.sp))
                }
                Box(Modifier.clickable { onClose() }.padding(8.dp)) {
                    BasicText(str.close, style = TextStyle(color = Color(0xFF4DA3FF), fontSize = 14.sp))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    if (file.isDirectory || isImage(file)) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (file.isDirectory) onDirChange(file)
                                else if (file.length() <= 50L * 1024 * 1024) onPick(file.absolutePath)
                                else Toast.makeText(context, "壁纸文件需 ≤ 50MB", Toast.LENGTH_SHORT).show()
                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                (if (file.isDirectory) "📁 " else "🖼 ") + file.name,
                                style = TextStyle(color = Color.White, fontSize = 15.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}


private val LocalBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

@Composable
private fun DialogPanel(content: @Composable ColumnScope.() -> Unit) {
    val backdrop = LocalBackdrop.current
    val shape: androidx.compose.ui.graphics.Shape = com.kyant.shapes.RoundedRectangle(
        22.dp, com.kyant.shapes.RoundedCornerStyle.Continuous
    )
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (backdrop != null) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    highlight = null,
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(11.dp.toPx(), 17.dp.toPx())
                    },
                    layerBlock = {},
                    onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.42f)) }
                ) else Modifier.background(Color(0xFF26262A), RoundedCornerShape(22.dp))
            )
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.55f),
                    0.35f to Color.White.copy(alpha = 0.22f),
                    0.7f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.30f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind { drawOutline(outline, rim, style = Stroke(1.7.dp.toPx())) }
            }
            .padding(vertical = 8.dp),
        content = content
    )
}

@Composable
private fun DialogItem(text: String, isCancel: Boolean = false, selected: Boolean = false, glass: Boolean = true, onClick: () -> Unit) {
    val backdrop = LocalBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f)
    )
    val scope = rememberCoroutineScope()
    val capsule: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50)
    Box(
        Modifier.then(if (isCancel) Modifier.widthIn(min = 96.dp) else Modifier.fillMaxWidth())
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .then(
                if (backdrop != null && glass) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsule },
                    highlight = null,
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(14f.dp.toPx(), 22f.dp.toPx())
                    },
                    layerBlock = {
                        val s = 1f + 0.08f * press
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = {
                        drawRect(if (selected) Color(0xFF0A84FF).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f))
                    }
                ) else Modifier
            )
            .then(
                if (glass) Modifier.drawWithCache {
                    val outline = capsule.createOutline(size, layoutDirection, this)
                    val rim = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.50f),
                        0.4f to Color.White.copy(alpha = 0.18f),
                        0.75f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.28f),
                        end = Offset(size.width, size.height)
                    )
                    onDrawBehind {
                        drawOutline(outline, rim, style = Stroke(1.4.dp.toPx()))
                        drawOutline(
                            outline,
                            Color.White.copy(alpha = 0.14f),
                            style = Stroke(0.8.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .clickable(interactionSource = interaction, indication = null) {
                scope.launch {
                    kotlinx.coroutines.delay(110)
                    onClick()
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        BasicText(
            text,
            style = TextStyle(color = if (isCancel) Color(0xFF4DA3FF) else Color.White, fontSize = 16.sp)
        )
    }
}
private val CalculatorFont = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(R.font.dm_sans),
        Font(R.font.dm_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500)))
    )
} else {
    FontFamily(Font(R.font.dm_sans))
}

private val BackspaceIcon = ImageVector.Builder("Backspace", 32.dp, 24.dp, 32f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineJoin = StrokeJoin.Round,
        strokeLineCap = StrokeCap.Round) {
        moveTo(11f, 2.3f)
        lineTo(27f, 2.3f)
        quadTo(30f, 2.3f, 30f, 5.3f)
        lineTo(30f, 18.7f)
        quadTo(30f, 21.7f, 27f, 21.7f)
        lineTo(11f, 21.7f)
        lineTo(2f, 12f)
        close()
        moveTo(15f, 8f)
        lineTo(23f, 16f)
        moveTo(23f, 8f)
        lineTo(15f, 16f)
    }
}.build()

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
            .padding(6.dp)
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
        key(LocalKeyStyle.current) {
            GlassKeyFace(backdrop, surfaceColor, light, LocalKeyStyle.current)
        }
        if (content != null) {
            content()
        } else {
            val icon = when (text) {
                "+" -> R.drawable.keypad_add
                "-", "−" -> R.drawable.keypad_subtract
                "×" -> R.drawable.keypad_multiply
                "÷" -> R.drawable.keypad_divide
                "=" -> R.drawable.keypad_equals
                else -> null
            }
            if (icon != null) {
                val tint = when {
                    text != "=" -> textColor
                    textColor == Color.White -> Color(0xFFB9DCFF)
                    else -> Color(0xFF173F67)
                }
                Image(
                    painter = painterResource(icon),
                    contentDescription = symbolLabel,
                    modifier = Modifier.size(with(LocalDensity.current) { 32.sp.toDp() }),
                    colorFilter = ColorFilter.tint(tint)
                )
            } else {
                val isNumber = text.length == 1 && (text[0].isDigit() || text == ".")
                BasicText(
                    text,
                    style = TextStyle(
                        color = textColor,
                        fontFamily = if (isNumber) CalculatorFont else null,
                        fontSize = if (isNumber) 32.sp else 29.sp
                    ),
                    maxLines = 1
                )
            }
        }
}
}
