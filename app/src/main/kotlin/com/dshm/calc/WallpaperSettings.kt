package com.dshm.calc

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private const val maxWallpaperBytes = 50L * 1024 * 1024

private data class WallpaperLabels(
    val up: String, val storageRead: String, val empty: String, val unreadable: String,
    val loading: String, val tooLarge: String, val invalidImage: String, val settingsUnavailable: String
)

private fun wallpaperLabels(lang: Lang) = when (lang) {
    Lang.ZH_CN -> WallpaperLabels("返回上一级", "选择壁纸需要读取存储中的图片。", "没有文件夹或图片", "无法读取此文件夹",
        "正在读取文件夹…", "壁纸文件须小于或等于 50 MB", "无法读取此图片", "无法打开权限设置")
    Lang.ZH_TW -> WallpaperLabels("返回上一層", "選取桌布需要讀取儲存空間內的圖片。", "沒有資料夾或圖片", "無法讀取此資料夾",
        "正在讀取資料夾…", "桌布檔案須小於或等於 50 MB", "無法讀取此圖片", "無法開啟權限設定")
    Lang.EN -> WallpaperLabels("Up one folder", "Allow access to read images from storage to choose a wallpaper.",
        "No folders or images", "Couldn't read this folder", "Reading folder…", "Wallpaper files must be 50 MB or smaller",
        "Couldn't read this image", "Couldn't open permission settings")
}

private fun canBrowseStorage(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/** The route reflects actual access, including grants changed while the system Settings app was open. */
@Composable
internal fun WallpaperSettings(
    page: CalculatorOverlay,
    str: Str,
    lang: Lang,
    dark: Boolean,
    backdrop: Backdrop,
    onNavigate: (CalculatorOverlay?) -> Unit,
    onPick: (String?) -> Unit
) {
    val context = LocalContext.current
    val interactive = LocalPanelPresentation.current.interactive
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val labels = remember(lang) { wallpaperLabels(lang) }
    var allowed by remember(context) { mutableStateOf(canBrowseStorage(context)) }
    var askedForRead by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = canBrowseStorage(context)
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = canBrowseStorage(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val destination = if (allowed) CalculatorOverlay.WALLPAPER else CalculatorOverlay.WALLPAPER_PERMISSION
    LaunchedEffect(destination, page, interactive) {
        if (interactive && page != destination) onNavigate(destination)
    }

    if (!allowed) {
        val grant = {
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            if (!modern && (!askedForRead || activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) == true)) {
                askedForRead = true
                permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                val app = Uri.fromParts("package", context.packageName, null)
                val targets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) listOf(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                ) else listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
                val launched = targets.any { intent ->
                    try {
                        settings.launch(intent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                }
                if (!launched) Toast.makeText(context, labels.settingsUnavailable, Toast.LENGTH_SHORT).show()
            }
        }
        OverlayPanel(str.wallpaper, backdrop, dark, { onNavigate(null) }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                PanelHeading(str.wallpaper, dark)
                BasicText(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) str.permText else labels.storageRead,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                    style = TextStyle(color = overlayInk(dark), fontSize = 15.sp, lineHeight = 21.sp))
                PanelButton(str.grant, dark, grant, modifier = Modifier.fillMaxWidth())
                PanelButton(str.cancel, dark, { onNavigate(null) }, secondary = true, modifier = Modifier.align(Alignment.End))
            }
        }
        return
    }

    val root = remember { Environment.getExternalStorageDirectory().absolutePath }
    var path by rememberSaveable(root) { mutableStateOf(root) }
    var pendingImage by remember(path) { mutableStateOf<File?>(null) }
    val savedDirectories = rememberSaveableStateHolder()
    val close = { onNavigate(null) }
    val up = {
        if (path == root) close() else {
            savedDirectories.removeState(path)
            path = File(path).parent ?: root
        }
    }
    LaunchedEffect(pendingImage, interactive) {
        if (!interactive) {
            pendingImage = null
            return@LaunchedEffect
        }
        val file = pendingImage ?: return@LaunchedEffect
        val message = withContext(Dispatchers.IO) { validateWallpaper(file, labels) }
        if (message == null) {
            onPick(file.absolutePath)
            close()
        } else {
            pendingImage = null
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    OverlayPanel(str.wallpaper, backdrop, dark, close) {
        BackHandler(enabled = interactive && path != root, onBack = up)
        savedDirectories.SaveableStateProvider(path) {
            key(path) {
                WallpaperDirectory(path, str, labels, if (path == root) str.close else labels.up, dark, up, close,
                    onReset = { onPick(null); close() },
                    onDirectory = { path = it.absolutePath },
                    onImage = { if (pendingImage == null) pendingImage = it })
            }
        }
    }
}

private data class WallpaperEntry(val file: File, val directory: Boolean)
private data class DirectoryContents(val entries: List<WallpaperEntry>, val readable: Boolean)

private suspend fun readDirectory(path: String): DirectoryContents {
    val unavailable = DirectoryContents(emptyList(), false)
    return try {
        val files = File(path).listFiles() ?: return unavailable
        val job = currentCoroutineContext()
        val entries = files.mapNotNull { file ->
            job.ensureActive()
            val directory = file.isDirectory
            if (directory || (file.extension.lowercase(Locale.ROOT) in imageExtensions && file.isFile)) {
                WallpaperEntry(file, directory)
            } else null
        }.sortedWith(compareBy<WallpaperEntry> { !it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.file.name })
        DirectoryContents(entries, true)
    } catch (_: SecurityException) {
        unavailable
    }
}

private fun validateWallpaper(file: File, labels: WallpaperLabels): String? = try {
    when {
        !file.isFile || !file.canRead() -> labels.invalidImage
        file.length() > maxWallpaperBytes -> labels.tooLarge
        else -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) null else labels.invalidImage
        }
    }
} catch (_: SecurityException) {
    labels.invalidImage
}

@Composable
private fun WallpaperDirectory(
    path: String,
    str: Str,
    labels: WallpaperLabels,
    upLabel: String,
    dark: Boolean,
    onUp: () -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onDirectory: (File) -> Unit,
    onImage: (File) -> Unit
) {
    val directory by produceState<DirectoryContents?>(null, path) {
        value = withContext(Dispatchers.IO) { readDirectory(path) }
    }
    val ink = overlayInk(dark)
    val interactive = LocalPanelPresentation.current.interactive
    val listing = directory
    if (listing == null) {
        BasicText(labels.loading, Modifier.padding(12.dp), style = TextStyle(color = ink, fontSize = 15.sp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "heading") { PanelHeading(str.wallpaper, dark) }
        item(key = "path") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PanelButton("←", dark, onUp, modifier = Modifier.semantics { contentDescription = upLabel },
                    feedback = OverlayButtonFeedback.Native)
                BasicText(path, Modifier.weight(1f).padding(start = 10.dp),
                    style = TextStyle(color = ink.copy(alpha = 0.80f), fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelButton(str.restoreDefault, dark, onReset, modifier = Modifier.weight(1f),
                    feedback = OverlayButtonFeedback.Native)
                PanelButton(str.close, dark, onClose, secondary = true, modifier = Modifier.weight(1f),
                    feedback = OverlayButtonFeedback.Native)
            }
        }
        if (listing.entries.isEmpty()) item(key = "empty") {
            BasicText(if (listing.readable) labels.empty else labels.unreadable, Modifier.padding(8.dp),
                style = TextStyle(color = ink.copy(alpha = 0.84f), fontSize = 14.sp))
        }
        items(listing.entries, key = { it.file.absolutePath }) { entry ->
            OverlayPressButton(null, dark, RoundedCornerShape(14.dp), feedback = OverlayButtonFeedback.Native,
                onClick = {
                    if (entry.directory) onDirectory(entry.file) else onImage(entry.file)
                }, enabled = interactive, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (entry.directory) "📁" else "🖼", style = TextStyle(fontSize = 17.sp))
                    BasicText(entry.file.name, Modifier.weight(1f).padding(start = 10.dp), style = TextStyle(color = ink, fontSize = 15.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
