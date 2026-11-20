package com.dshm.calc

import android.content.ClipData
import android.content.ComponentName
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
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

private val LAUNCHER_ALIASES = listOf(
    "com.dshm.calc.LauncherZhCN",
    "com.dshm.calc.LauncherZhTW",
    "com.dshm.calc.LauncherEn"
)

fun syncLauncherAlias(context: Context, lang: String) {
    val target = when (lang) {
        "zh-CN" -> LAUNCHER_ALIASES[0]
        "zh-TW" -> LAUNCHER_ALIASES[1]
        else -> LAUNCHER_ALIASES[2]
    }
    val pm = context.packageManager
    for (name in LAUNCHER_ALIASES) {
        val desired = if (name == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            val cn = ComponentName(context, name)
            if (pm.getComponentEnabledSetting(cn) != desired) {
                pm.setComponentEnabledSetting(cn, desired, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}

private val MC = MathContext(12, RoundingMode.HALF_UP)

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
    val restoreDefault: String, val close: String, val error: String, val copy: String
)

private val strings = mapOf(
    Lang.ZH_CN to Str(
        "计算器", "设置", "深色模式", "壁纸", "震动", "语言", "关于",
        "跟随系统", "壁纸取色", "无", "关闭", "标准", "增强", "取消",
        "版本 V3.0-release", "确定", "更换壁纸需要「允许访问所有文件」权限", "去授权", "恢复默认", "关闭", "错误", "复制"
    ),
    Lang.ZH_TW to Str(
        "計算器", "設置", "深色模式", "桌布", "震動", "語言", "關於",
        "跟隨系統", "桌布取色", "無", "關閉", "標準", "增強", "取消",
        "版本 V3.0-release", "確定", "更換桌布需要「允許存取所有檔案」權限", "去授權", "恢復預設", "關閉", "錯誤", "複製"
    ),
    Lang.EN to Str(
        "Calculator", "Settings", "Dark mode", "Wallpaper", "Vibration", "Language", "About",
        "Follow system", "Wallpaper color", "None", "Off", "Standard", "Enhanced", "Cancel",
        "Version V3.0-release", "OK", "Changing wallpaper requires 'All files access' permission", "Grant", "Restore default", "Close", "Error", "Copy"
    )
)

private fun stringsFor(lang: Lang): Str = strings[lang]!!

data class FeatureIntro(val title: String, val lines: List<String>, val continueLabel: String)

private fun featureIntroFor(lang: Lang): FeatureIntro = when (lang) {
    Lang.ZH_CN -> FeatureIntro(
        "功能介绍",
        listOf(
            "· 按住玻璃按键，触点一侧下压，旁边的键缘映出微光",
            "· 长按 ÷ 输入分数：先输分母再输分子，最多两层嵌套",
            "· 长按 AC 插入括号，用括号改变运算优先级",
            "· 算式按运算优先级精确计算，分数不丢精度",
            "· 顶栏记录最近 10 次计算，可回填结果、双击清空",
            "· 深色/浅色主题、自定义壁纸、三档震动反馈"
        ),
        "继续"
    )
    Lang.ZH_TW -> FeatureIntro(
        "功能介紹",
        listOf(
            "· 按住玻璃按鍵，觸點一側下壓，旁邊的鍵緣映出微光",
            "· 長按 ÷ 輸入分數：先輸分母再輸分子，最多兩層巢狀",
            "· 長按 AC 插入括號，用括號改變運算優先級",
            "· 算式按運算優先級精確計算，分數不丟精度",
            "· 頂欄記錄最近 10 次計算，可回填結果、雙擊清空",
            "· 深色/淺色主題、自訂桌布、三檔震動回饋"
        ),
        "繼續"
    )
    Lang.EN -> FeatureIntro(
        "Features",
        listOf(
            "· Glass keys dip under your finger and light the edges of nearby keys",
            "· Long-press ÷ to enter a fraction (denominator first, up to 2 levels)",
            "· Long-press AC to insert parentheses for precedence control",
            "· Expressions are evaluated exactly with operator precedence, no precision loss",
            "· Top bar keeps the last 10 calculations; reuse results, double-tap to clear",
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

private fun formatResult(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return "错误"
    val bd = BigDecimal(v).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
    return if (bd.signum() == 0) "0" else bd.toPlainString()
}

private sealed class Tok
private data class NumTok(val v: Double) : Tok()
private data class OpTok(val op: Char) : Tok()

private fun tokenize(s: String): List<Tok>? {
    val tokens = mutableListOf<Tok>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isDigit() || c == '.' -> {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                val num = s.substring(start, i).toDoubleOrNull() ?: return null
                tokens.add(NumTok(num))
            }
            c == '+' || c == '-' || c == '×' || c == '÷' -> {
                tokens.add(OpTok(c)); i++
            }
            else -> return null
        }
    }
    return tokens
}

private fun evaluateExpression(s: String): Double? {
    val tokens = tokenize(s) ?: return null
    if (tokens.isEmpty()) return null
    var i = 0
    fun parseFactor(): Double? {
        if (i >= tokens.size) return null
        val t = tokens[i]
        if (t is OpTok && (t.op == '-' || t.op == '+')) {
            i++
            val v = parseFactor() ?: return null
            return if (t.op == '-') -v else v
        }
        if (t is NumTok) { i++; return t.v }
        return null
    }
    fun parseTerm(): Double? {
        var v = parseFactor() ?: return null
        while (i < tokens.size && tokens[i] is OpTok) {
            val op = (tokens[i] as OpTok).op
            if (op != '×' && op != '÷') break
            i++
            val rhs = parseFactor() ?: return null
            if (op == '÷' && rhs == 0.0) return null
            v = if (op == '×') v * rhs else v / rhs
        }
        return v
    }
    fun parseExpr(): Double? {
        var v = parseTerm() ?: return null
        while (i < tokens.size && tokens[i] is OpTok) {
            val op = (tokens[i] as OpTok).op
            if (op != '+' && op != '-') break
            i++
            val rhs = parseTerm() ?: return null
            v = if (op == '+') v + rhs else v - rhs
        }
        return v
    }
    val result = parseExpr()
    return if (i == tokens.size) result else null
}

/* ===================== 堆叠分数模型 ===================== */

sealed class FItem {
    class Num(var text: String) : FItem()
    class Op(val sym: String) : FItem()
    class FracItem(
        val num: MutableList<FItem> = mutableListOf(),
        val den: MutableList<FItem> = mutableListOf()
    ) : FItem()
    class ParenItem(
        val inner: MutableList<FItem> = mutableListOf()
    ) : FItem()
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
            slot.add(index, FItem.Num(c.toString()))
            charOff = 1
        }
    }

    fun insertOp(sym: String) {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num) {
            val off = charOff.coerceIn(0, cur.text.length)
            if (off == 0) {
                slot.add(index, FItem.Op(sym))
                index += 1
                charOff = 0
                return
            }
            if (off < cur.text.length) {
                val suffix = cur.text.substring(off)
                cur.text = cur.text.substring(0, off)
                slot.add(index + 1, FItem.Op(sym))
                slot.add(index + 2, FItem.Num(suffix))
                index += 2
                charOff = 0
                return
            }
            index += 1
            slot.add(index, FItem.Op(sym))
            index += 1
            charOff = 0
            return
        }
        slot.add(index, FItem.Op(sym))
        index += 1
        charOff = 0
    }

    fun backspace() {
        val cur = slot.getOrNull(index)
        if (cur is FItem.Num) {
            val off = charOff.coerceIn(0, cur.text.length)
            if (off > 0) {
                cur.text = cur.text.substring(0, off - 1) + cur.text.substring(off)
                charOff = off - 1
                if (cur.text.isEmpty()) { slot.removeAt(index); charOff = 0 }
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
                return
            }
            val ff = stack.lastOrNull()
            if (ff != null && (slot === ff.frac.num || slot === ff.frac.den)) {
                stack.removeAt(stack.size - 1)
                slot = ff.parent
                if (ff.fracAt < slot.size && slot[ff.fracAt] === ff.frac) slot.removeAt(ff.fracAt)
                index = ff.fracAt
                charOff = 0
                return
            }
        }
        if (index > 0) {
            val prev = slot.removeAt(index - 1)
            index -= 1
            charOff = 0
            while (stack.isNotEmpty() && stack.last().frac === prev) stack.removeAt(stack.size - 1)
            val pf = parenStack.lastOrNull()
            if (pf != null && pf.paren === prev) parenStack.removeAt(parenStack.size - 1)
        }
    }

    fun clear() {
        root.clear(); stack.clear(); slot = root; index = 0; charOff = 0
    }

    fun newFraction() {
        if (stack.size + parenStack.size >= 3) return
        val f = FItem.FracItem()
        slot.add(index, f)
        stack.add(FracFrame(slot, index, f, false))
        slot = f.den; index = 0; charOff = 0
    }

    private val parenStack = mutableListOf<ParenFrame>()

    fun newParen() {
        if (stack.size + parenStack.size >= 3) return
        val p = FItem.ParenItem()
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

private class Ratio(val n: BigInteger, val d: BigInteger) {
    companion object {
        fun of(n: BigInteger, d: BigInteger): Ratio {
            if (d.signum() == 0) throw ArithmeticException("div0")
            var nn = n; var dd = d
            if (dd.signum() < 0) { nn = nn.negate(); dd = dd.negate() }
            val g = nn.gcd(dd)
            if (g.signum() != 0 && g != BigInteger.ONE) { nn = nn.divide(g); dd = dd.divide(g) }
            return Ratio(nn, dd)
        }
    }
    fun plus(o: Ratio) = of(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d))
    fun minus(o: Ratio) = of(n.multiply(o.d).subtract(o.n.multiply(d)), d.multiply(o.d))
    fun times(o: Ratio) = of(n.multiply(o.n), d.multiply(o.d))
    fun divide(o: Ratio) = of(n.multiply(o.d), d.multiply(o.n))
    fun isInteger() = d == BigInteger.ONE
    fun isZero() = n.signum() == 0
}

private fun textToRatio(s: String): Ratio? {
    if (s.isEmpty() || s == ".") return null
    val t = if (s.endsWith(".")) s.dropLast(1) else s
    if (t.isEmpty() || t == "-") return null
    val bd = t.toBigDecimalOrNull() ?: return null
    val unscaled = bd.unscaledValue()
    val scale = bd.scale()
    val den = if (scale > 0) BigInteger.TEN.pow(scale) else BigInteger.ONE
    val num = if (scale > 0) unscaled else unscaled.multiply(BigInteger.TEN.pow(-scale))
    return try { Ratio.of(num, den) } catch (e: Exception) { null }
}

private fun evalSlot(items: List<FItem>): Ratio? {
    var pos = 0
    fun factor(): Ratio? {
        if (pos >= items.size) return null
        val it = items[pos]
        if (it is FItem.Op && (it.sym == "-" || it.sym == "+")) {
            pos++
            val v = factor() ?: return null
            return if (it.sym == "-") Ratio.of(v.n.negate(), v.d) else v
        }
        return when (it) {
            is FItem.Num -> { pos++; textToRatio(it.text) }
            is FItem.FracItem -> {
                pos++
                val num = evalSlot(it.num) ?: return null
                val den = evalSlot(it.den) ?: return null
                if (den.isZero()) null else try { num.divide(den) } catch (e: ArithmeticException) { null }
            }
            is FItem.ParenItem -> { pos++; evalSlot(it.inner) }
            else -> null
        }
    }
    fun term(): Ratio? {
        var v = factor() ?: return null
        while (pos < items.size) {
            val it = items[pos]
            if (it is FItem.Op && (it.sym == "×" || it.sym == "÷")) {
                pos++
                val r = factor() ?: return null
                v = if (it.sym == "×") v.times(r) else { if (r.isZero()) return null; v.divide(r) }
            } else break
        }
        return v
    }
    fun expr(): Ratio? {
        var v = term() ?: return null
        while (pos < items.size) {
            val it = items[pos]
            if (it is FItem.Op && (it.sym == "+" || it.sym == "-")) {
                pos++
                val r = term() ?: return null
                v = if (it.sym == "+") v.plus(r) else v.minus(r)
            } else break
        }
        return v
    }
    val r = expr()
    return if (pos == items.size) r else null
}

private fun formatRatio(r: Ratio): String =
    if (r.isInteger()) r.n.toString() else "${r.n}/${r.d}"

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

private class DText(val text: String, val x: Float, val y: Float)
private class DBar(val x: Float, val y: Float, val w: Float)

private class CTarget(
    val slot: MutableList<FItem>,
    val index: Int,
    val rect: Rect,
    val charXs: List<Float>,
    val fracPath: List<FracFrame>,
    val parenPath: List<ParenFrame>,
    val kind: Int
)

private class LayoutResult(
    val width: Float,
    val height: Float,
    val texts: List<DText>,
    val bars: List<DBar>,
    val targets: List<CTarget>
)

private class LayoutEngine(private val tm: TextMeasurer, private val style: TextStyle) {
    private val lineH = tm.measure(AnnotatedString("0"), style).size.height.toFloat()
    private val texts = mutableListOf<DText>()
    private val bars = mutableListOf<DBar>()
    private val targets = mutableListOf<CTarget>()

    private fun tw(s: String) = tm.measure(AnnotatedString(s), style).size.width.toFloat()

    private fun measure(items: List<FItem>): Pair<Float, Float> {
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
        place(root, 0f, 0f, h, emptyList(), emptyList())
        return LayoutResult(w, h, texts, bars, targets)
    }

    private fun place(items: MutableList<FItem>, x0: Float, y0: Float, rowH: Float, fp: List<FracFrame>, pp: List<ParenFrame>) {
        var x = x0
        val ty = y0 + (rowH - lineH) / 2f
        items.forEachIndexed { idx, item ->
            when (item) {
                is FItem.Num -> {
                    val w = tw(item.text)
                    texts.add(DText(item.text, x, ty))
                    val cxs = ArrayList<Float>()
                    for (i in 0..item.text.length) cxs.add(x + tw(item.text.substring(0, i)))
                    targets.add(CTarget(items, idx, Rect(x, ty, x + w, ty + lineH), cxs, fp, pp, 0))
                    x += w
                }
                is FItem.Op -> {
                    val w = tw(item.sym)
                    texts.add(DText(item.sym, x, ty))
                    targets.add(CTarget(items, idx, Rect(x, ty, x + w, ty + lineH), emptyList(), fp, pp, 0))
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
                    x += fw
                }
                is FItem.ParenItem -> {
                    val (iw, ih) = measure(item.inner)
                    val lw = tw("(")
                    val rw = tw(")")
                    val ph = maxOf(lineH, ih)
                    val py = y0 + (rowH - ph) / 2f
                    val ip = pp + ParenFrame(items, idx, item)
                    texts.add(DText("(", x, py + (ph - lineH) / 2f))
                    targets.add(CTarget(item.inner, 0, Rect(x, py, x + lw, py + ph), emptyList(), fp, ip, 1))
                    place(item.inner, x + lw, py + (ph - ih) / 2f, ih, fp, ip)
                    texts.add(DText(")", x + lw + iw, py + (ph - lineH) / 2f))
                    targets.add(CTarget(items, idx + 1, Rect(x + lw + iw, py, x + lw + iw + rw, py + ph), emptyList(), fp, pp, 2))
                    x += lw + iw + rw
                }
            }
        }
        targets.add(CTarget(items, items.size, Rect(x, ty, x + 10f, ty + lineH), listOf(x), fp, pp, 0))
    }
}

private fun hitTest(layout: LayoutResult, editor: FracEditor, pos: Offset) {
    val exact = layout.targets.firstOrNull { it.rect.contains(pos) }
    val t = exact ?: layout.targets.minByOrNull { r ->
        val dy = if (pos.y < r.rect.top) r.rect.top - pos.y else if (pos.y > r.rect.bottom) pos.y - r.rect.bottom else 0f
        val dx = if (pos.x < r.rect.left) r.rect.left - pos.x else if (pos.x > r.rect.right) pos.x - r.rect.right else 0f
        dy * 100f + dx
    } ?: return
    val num = t.slot.getOrNull(t.index) as? FItem.Num
    if (num != null && t.charXs.isNotEmpty()) {
        var best = 0
        var bestD = Float.MAX_VALUE
        t.charXs.forEachIndexed { i, cx ->
            val d = kotlin.math.abs(cx - pos.x)
            if (d < bestD) { bestD = d; best = i }
        }
        editor.setCursor(t.slot, t.index, best.coerceIn(0, num.text.length), t.fracPath, t.parenPath)
    } else {
        editor.setCursor(t.slot, t.index, 0, t.fracPath, t.parenPath)
    }
}

private fun cursorRectOf(layout: LayoutResult, editor: FracEditor): Rect? {
    val t = layout.targets.firstOrNull { it.slot === editor.slot && it.index == editor.index && it.kind == 0 && it.charXs.isNotEmpty() } ?: return null
    val i = editor.charOff.coerceIn(0, t.charXs.size - 1)
    val cx = t.charXs[i]
    return Rect(cx, t.rect.top, cx, t.rect.bottom)
}

private fun exprText(items: List<FItem>): String = items.joinToString("") {
    when (it) {
        is FItem.Num -> it.text
        is FItem.Op -> it.sym
        is FItem.FracItem -> "(${exprText(it.num)})/(${exprText(it.den)})"
        is FItem.ParenItem -> "(${exprText(it.inner)})"
    }
}

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

    val dark = when (darkMode) {
        "system" -> isSystemInDarkTheme()
        "wallpaper" -> effectiveWallpaper?.let { isWallpaperDark(it) } ?: manualDark
        else -> manualDark
    }
    val gradientColors = if (dark) DarkGradient else LightGradient
    val fg = if (dark) Color.White else Color(0xFF15151A)
    val buttonSurface = if (dark) Color(0xFF0D0D10).copy(alpha = 0.5f) else Color(0xFFFFFFFF).copy(alpha = 0.42f)
    val displayColor = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF5A5A5A).copy(alpha = 0.9f)

    var showMenu by remember { mutableStateOf(false) }
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

    fun useResult(result: String) {
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
        editor.insertChar(d[0])
        revision++
    }

    fun onDot() {
        val cur = editor.slot.getOrNull(editor.index)
        if (cur is FItem.Num) {
            if (cur.text.contains(".")) return
            editor.insertChar('.')
        } else {
            editor.slot.add(editor.index, FItem.Num("0."))
            editor.charOff = 2
        }
        revision++
    }

    fun onOperator(op: String) {
        editor.insertOp(op)
        revision++
    }

    fun onBackspace() {
        editor.backspace()
        revision++
    }

    fun onClear() {
        editor.clear()
        revision++
    }

    fun onFraction() {
        editor.newFraction()
        revision++
    }

    fun onParen() {
        editor.newParen()
        revision++
    }

    fun onNextField() {
        editor.nextField()
        revision++
    }

    fun onEquals() {
        val text = exprText(editor.root)
        val r = evalSlot(editor.root)
        if (r != null) {
            val formatted = formatRatio(r)
            history.add(0, HistoryItem(text, formatted))
            while (history.size > 10) history.removeAt(history.size - 1)
            persistHistory()
            editor.clear()
            if (r.isInteger()) {
                editor.root.add(FItem.Num(r.n.toString()))
            } else {
                editor.root.add(FItem.FracItem(
                    num = mutableListOf(FItem.Num(r.n.toString())),
                    den = mutableListOf(FItem.Num(r.d.toString()))
                ))
            }
            editor.index = editor.root.size
            editor.charOff = 0
        } else {
            editor.clear()
            editor.root.add(FItem.Num(str.error))
            editor.index = 1
            editor.charOff = 0
        }
        revision++
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
    CompositionLocalProvider(LocalVibrationLevel provides vibrationLevel, LocalKeypadLights provides keypadLights) {
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

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    str.title,
                    style = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HistoryPill(history, fg, dark, str.copy, ::clearHistory, onUseResult = { useResult(it) })
                }
                Box(Modifier.size(44.dp).clickable { showMenu = true }, contentAlignment = Alignment.Center) {
                    BasicText("⋮", style = TextStyle(color = fg, fontSize = 24.sp))
                }
            }

            val fontSp = when {
                editor.textLen() > 10 -> 40.sp
                editor.textLen() > 7 -> 48.sp
                else -> 60.sp
            }
            val dispStyle = TextStyle(color = displayColor, fontSize = fontSp, fontWeight = FontWeight.Light)
            val textMeasurer = rememberTextMeasurer()
            val rev = revision
            val layout = remember(rev, fontSp) {
                if (editor.root.isEmpty()) null else LayoutEngine(textMeasurer, dispStyle).build(editor.root)
            }
            val cursorAlpha by rememberInfiniteTransition().animateFloat(
                initialValue = 1f, targetValue = 0.15f,
                animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse)
            )
            val density = LocalDensity.current

            Box(
                Modifier.fillMaxWidth().weight(1f).padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (layout == null) {
                    BasicText("0", style = dispStyle)
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            Modifier
                                .size(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() })
                                .pointerInput(fontSp, rev) {
                                    detectTapGestures { offset ->
                                        hitTest(layout, editor, offset)
                                        revision++
                                    }
                                }
                        ) {
                            layout.texts.forEach { drawText(textMeasurer, it.text, topLeft = Offset(it.x, it.y), style = dispStyle) }
                            layout.bars.forEach { drawRect(fg, topLeft = Offset(it.x, it.y), size = Size(it.w, 1.5.dp.toPx())) }
                            val cr = cursorRectOf(layout, editor)
                            if (cr != null) {
                                drawRect(fg.copy(alpha = cursorAlpha), topLeft = Offset(cr.left, cr.top), size = Size(2.dp.toPx(), cr.height))
                            }
                        }
                    }
                }
            }

            Row {
                GlassButton("AC", backdrop, Modifier.weight(2f).aspectRatio(2f), ::onClear, ::onParen, buttonSurface, fg, strongHaptic = true)
                GlassButton("÷", backdrop, Modifier.weight(1f).aspectRatio(1f), { if (editor.inFraction()) onNextField() else onOperator("÷") }, { onFraction() }, buttonSurface, fg)
                GlassButton("×", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("×") }, null, buttonSurface, fg)
            }
            Row {
                GlassButton("7", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("7") }, null, buttonSurface, fg)
                GlassButton("8", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("8") }, null, buttonSurface, fg)
                GlassButton("9", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("9") }, null, buttonSurface, fg)
                GlassButton("-", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("-") }, null, buttonSurface, fg)
            }
            Row {
                GlassButton("4", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("4") }, null, buttonSurface, fg)
                GlassButton("5", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("5") }, null, buttonSurface, fg)
                GlassButton("6", backdrop, Modifier.weight(1f).aspectRatio(1f), { onDigit("6") }, null, buttonSurface, fg)
                GlassButton("+", backdrop, Modifier.weight(1f).aspectRatio(1f), { onOperator("+") }, null, buttonSurface, fg)
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
                        GlassButton("⌫", backdrop, Modifier.weight(1f).aspectRatio(1f), ::onBackspace, null, buttonSurface, fg)
                    }
                }
                GlassButton("=", backdrop, Modifier.weight(1f).aspectRatio(0.5f), ::onEquals, ::toggleThemeByLongPress, buttonSurface, fg, strongHaptic = true)
            }
        }
        Spacer(Modifier.matchParentSize().graphicsLayer().drawNeighborReflections(keypadLights, dark))
    }

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            DialogPanel {
                BasicText(str.settings, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.darkMode) { showMenu = false; showSettings = true }
                DialogItem(str.wallpaper) { showMenu = false; requestWallpaper() }
                DialogItem(str.vibration) { showMenu = false; showVibration = true }
                DialogItem(str.language) { showMenu = false; showLanguage = true }
                DialogItem(featureIntroFor(lang).title) { showMenu = false; showIntro = true }
                DialogItem(str.about) { showMenu = false; showAbout = true }
            }
        }
    }
    if (showVibration) {
        Dialog(onDismissRequest = { showVibration = false }) {
            DialogPanel {
                BasicText(str.vibration, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem(str.off + if (vibrationLevel == "off") "  ✓" else "") {
                    vibrationLevel = "off"
                    prefs.edit().putString("vibration_level", "off").apply()
                    showVibration = false
                }
                DialogItem(str.standard + if (vibrationLevel == "standard") "  ✓" else "") {
                    vibrationLevel = "standard"
                    prefs.edit().putString("vibration_level", "standard").apply()
                    showVibration = false
                }
                DialogItem(str.enhanced + if (vibrationLevel == "enhanced") "  ✓" else "") {
                    vibrationLevel = "enhanced"
                    prefs.edit().putString("vibration_level", "enhanced").apply()
                    showVibration = false
                }
                DialogItem(str.cancel, isCancel = true) { showVibration = false }
            }
        }
    }
    if (showLanguage) {
        Dialog(onDismissRequest = { showLanguage = false }) {
            DialogPanel {
                BasicText(str.language, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                DialogItem("简体中文" + if (language == "zh-CN") "  ✓" else "") {
                    language = "zh-CN"
                    prefs.edit().putString("language", "zh-CN").apply()
                    syncLauncherAlias(context, "zh-CN")
                    showLanguage = false
                }
                DialogItem("繁體中文" + if (language == "zh-TW") "  ✓" else "") {
                    language = "zh-TW"
                    prefs.edit().putString("language", "zh-TW").apply()
                    syncLauncherAlias(context, "zh-TW")
                    showLanguage = false
                }
                DialogItem("English" + if (language == "en") "  ✓" else "") {
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
                DialogItem(str.followSystem + if (darkMode == "system") "  ✓" else "") {
                    darkMode = "system"
                    prefs.edit().putString("dark_mode", "system").apply()
                    showSettings = false
                }
                DialogItem(str.wallpaperColor + if (darkMode == "wallpaper") "  ✓" else "") {
                    darkMode = "wallpaper"
                    prefs.edit().putString("dark_mode", "wallpaper").apply()
                    showSettings = false
                }
                DialogItem(str.none + if (darkMode == "manual") "  ✓" else "") {
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
private fun HistoryPill(history: List<HistoryItem>, fg: Color, dark: Boolean, copyLabel: String, onClear: () -> Unit, onUseResult: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val widthFraction by animateFloatAsState(
        targetValue = if (expanded) 0.92f else 0.5f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(widthFraction).height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onDoubleClick = { onClear() }
                ),
            contentAlignment = Alignment.Center
        ) {
            val last = history.firstOrNull()
            BasicText(
                if (last != null) "${last.expr} = ${last.result}" else "",
                style = TextStyle(color = fg, fontSize = 13.sp),
                maxLines = 1
            )
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
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
                                    expanded = false
                                }.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(copyLabel, style = TextStyle(color = Color(0xFF0A84FF), fontSize = 13.sp))
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

@Composable
private fun DialogPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF26262A), RoundedCornerShape(22.dp)).padding(vertical = 8.dp),
        content = content
    )
}

@Composable
private fun DialogItem(text: String, isCancel: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BasicText(
            text,
            style = TextStyle(color = if (isCancel) Color(0xFF4DA3FF) else Color.White, fontSize = 16.sp)
        )
    }
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
    strongHaptic: Boolean = false
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
                onClick = {
                    hapticType(vibrationLevel, strongHaptic)?.let { haptic.performHapticFeedback(it) }
                    onClick()
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassKeyFace(backdrop, surfaceColor, interactionSource, light)
        BasicText(
            text,
            style = TextStyle(
                color = textColor,
                fontSize = if (text in listOf("÷", "×", "-", "+", "=")) 26.sp else 22.sp,
                fontWeight = if (text in listOf("÷", "×", "-", "+", "=")) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}
