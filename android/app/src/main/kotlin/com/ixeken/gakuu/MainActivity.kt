package com.ixeken.gakuu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity: FlutterActivity() {
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
    private val CHANNEL = "com.ixeken.wallpaper/media"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            when (call.method) {
                "updatePlaylist" -> {
                    val playlist = call.argument<List<String>>("playlist")
                    val type = call.argument<String>("type") ?: "general"
                    if (playlist != null) {
                      ioExecutor.execute {
                        try {
                        val storageDir = File(filesDir, "wallpapers/$type")
                        if (!storageDir.exists()) {
                            storageDir.mkdirs()
                        }
                        
                        val copiedPaths = mutableListOf<String>()
                        val failedPaths = mutableListOf<String>()
                        
                        for (path in playlist) {
                            val originalFile = File(path)
                            if (!originalFile.exists()) {
                                continue
                            }
                            
                            if (!isValidImageOrVideo(originalFile)) {
                                failedPaths.add(originalFile.name)
                                continue
                            }
                            
                            val destFile = if (originalFile.parentFile?.canonicalFile == storageDir.canonicalFile) {
                                originalFile
                            } else {
                                File(storageDir, UUID.randomUUID().toString() + '_' + originalFile.name)
                            }
                            
                            if (originalFile.absolutePath != destFile.absolutePath) {
                                try {
                                    originalFile.inputStream().use { input ->
                                        destFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    failedPaths.add(originalFile.name)
                                    continue
                                }
                            }
                            copiedPaths.add(destFile.absolutePath)
                        }
                        
                        val copiedSet = copiedPaths.map { File(it).name }.toSet()
                        storageDir.listFiles()?.forEach { file ->
                            if (!copiedSet.contains(file.name)) {
                                file.delete()
                            }
                        }
                        
                        val key = when(type) {
                            "day" -> "playlist_day"
                            "night" -> "playlist_night"
                            else -> "playlist"
                        }
                        editor.putString(key, copiedPaths.joinToString("||")).commit()
                        
                        performDiskCleanup()
                        
                        val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED)
                        intent.setPackage(packageName)
                        sendBroadcast(intent)
                        
                        if (failedPaths.isNotEmpty()) {
                            runOnUiThread {
                                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
                                    .invokeMethod("onPlaylistError", failedPaths)
                            }
                        }
                        
                        runOnUiThread { result.success(copiedPaths) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("PLAYLIST_ERROR", e.message, null) }
                        }
                      }
                    } else {
                        result.error("INVALID_ARGS", "Playlist is null", null)
                    }
                }
                "applySingleWallpaper" -> {
                    val path = call.argument<String>("path")
                    if (path != null) {
                      ioExecutor.execute copy@ {
                        val originalFile = File(path)
                        if (originalFile.exists()) {
                            if (isValidImageOrVideo(originalFile)) {
                                val storageDir = File(filesDir, "wallpapers/general")
                                if (!storageDir.exists()) {
                                    storageDir.mkdirs()
                                }
                                val destFile = if (originalFile.parentFile?.canonicalFile == storageDir.canonicalFile) {
                                    originalFile
                                } else {
                                    File(storageDir, UUID.randomUUID().toString() + '_' + originalFile.name)
                                }
                                if (originalFile.absolutePath != destFile.absolutePath) {
                                    try {
                                        originalFile.inputStream().use { input ->
                                            destFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        runOnUiThread { result.error("COPY_FAILED", "Failed to copy file", e.message) }
                                        return@copy
                                    }
                                }
                                
                                editor.putString("playlist", destFile.absolutePath)
                                    .putString("selected_engine", "carousel")
                                    .putBoolean("useDayNightMode", false)
                                    .putBoolean("syncWithSystemTheme", false)
                                    .commit()
                                
                                performDiskCleanup()
                                
                                val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED)
                                intent.setPackage(packageName)
                                sendBroadcast(intent)
                                
                                runOnUiThread { result.success(true) }
                            } else {
                                runOnUiThread { result.error("INVALID_FILE", "File is corrupt or not supported", null) }
                            }
                        } else {
                            runOnUiThread { result.error("FILE_NOT_FOUND", "File does not exist", null) }
                        }
                      }
                    } else {
                        result.error("INVALID_ARGS", "Path is null", null)
                    }
                }
                "updateSettings" -> {
                    val changeOnVisible = call.argument<Boolean>("changeOnVisible") ?: true
                    val useDayNightMode = call.argument<Boolean>("useDayNightMode") ?: false
                    val dayStartHour = call.argument<Int>("dayStartHour") ?: 6
                    val nightStartHour = call.argument<Int>("nightStartHour") ?: 18
                    val isDimEnabled = call.argument<Boolean>("isDimEnabled") ?: false
                    val dimIntensity = call.argument<Double>("dimIntensity") ?: 0.35
                    val selectedEngine = call.argument<String>("selectedEngine") ?: "carousel"
                    val isRandom = call.argument<Boolean>("isRandom") ?: false
                    val tetrisStyle = call.argument<String>("tetrisStyle") ?: "neon"
                    val syncWithSystemTheme = call.argument<Boolean>("syncWithSystemTheme") ?: false
                    val isParallaxEnabled = call.argument<Boolean>("isParallaxEnabled") ?: false
                    val carouselChangeMode = call.argument<String>("carouselChangeMode") ?: "on_visibility"
                    val carouselChangeInterval = call.argument<Int>("carouselChangeInterval") ?: 60
                    val isHalfFpsEnabled = call.argument<Boolean>("isHalfFpsEnabled") ?: false
                    
                    val patternLayoutSize = call.argument<Int>("patternLayoutSize") ?: 2
                    val patternSlotIcons = call.argument<String>("patternSlotIcons") ?: "circle,star,heart,cross"
                    val patternSpeed = call.argument<Double>("patternSpeed") ?: 2.0
                    val patternDensity = call.argument<String>("patternDensity") ?: "medium"
                    val patternRotate = call.argument<Boolean>("patternRotate") ?: true

                    editor.putBoolean("changeOnVisible", changeOnVisible)
                        .putBoolean("useDayNightMode", useDayNightMode)
                        .putInt("dayStartHour", dayStartHour)
                        .putInt("nightStartHour", nightStartHour)
                        .putBoolean("isDimEnabled", isDimEnabled)
                        .putFloat("dim_intensity", dimIntensity.toFloat())
                        .putString("selected_engine", selectedEngine)
                        .putBoolean("isRandom", isRandom)
                        .putString("tetris_style", tetrisStyle)
                        .putBoolean("syncWithSystemTheme", syncWithSystemTheme)
                        .putBoolean("isParallaxEnabled", isParallaxEnabled)
                        .putString("carousel_change_mode", carouselChangeMode)
                        .putInt("carousel_change_interval", carouselChangeInterval)
                        .putBoolean("isHalfFpsEnabled", isHalfFpsEnabled)
                        .putInt("pattern_layout_size", patternLayoutSize)
                        .putString("pattern_slot_icons", patternSlotIcons)
                        .putInt("pattern_speed", patternSpeed.toInt())
                        .putString("pattern_density", patternDensity)
                        .putBoolean("pattern_rotate", patternRotate)
                        .commit()
                    
                    val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED)
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    
                    result.success(true)
                }
                "openWallpaperPicker" -> {
                    try {
                        val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        intent.putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, 
                            android.content.ComponentName(this, IxekenWallpaperService::class.java))
                        startActivity(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            startActivity(intent)
                            result.success(true)
                        } catch (ex: Exception) {
                            result.error("PICKER_ERROR", ex.message, null)
                        }
                    }
                }
                "launchUrl" -> {
                    val url = call.argument<String>("url")
                    if (url != null) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("LAUNCH_ERROR", e.message, null)
                        }
                    } else {
                        result.error("INVALID_ARGS", "URL is null", null)
                    }
                }
                "clearWallpaper" -> {
                    try {
                        val wm = android.app.WallpaperManager.getInstance(this)
                        wm.clear()
                        result.success(true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        result.success(false)
                    }
                }
                "getAppDirectory" -> {
                    result.success(filesDir.absolutePath)
                }
                "getAppVersion" -> {
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    result.success(packageInfo.versionName ?: "0.0.0")
                }
                "cropAndProcessMedia" -> {
                    val path = call.argument<String>("path")
                    val cropX = call.argument<Double>("cropX") ?: 0.0
                    val cropY = call.argument<Double>("cropY") ?: 0.0
                    val cropWidth = call.argument<Double>("cropWidth") ?: 1.0
                    val cropHeight = call.argument<Double>("cropHeight") ?: 1.0
                    val type = call.argument<String>("type") ?: "general"
                    
                    if (path == null) {
                        result.error("INVALID_ARGS", "Path is null", null)
                        return@setMethodCallHandler
                    }
                    val originalFile = File(path)
                    if (!originalFile.exists()) {
                        result.error("FILE_NOT_FOUND", "Original file does not exist", null)
                        return@setMethodCallHandler
                    }
                    
                    val storageDir = File(filesDir, "wallpapers/$type")
                    if (!storageDir.exists()) {
                        storageDir.mkdirs()
                    }
                    
                    val isVideo = originalFile.name.endsWith(".mp4", ignoreCase = true) ||
                                  originalFile.name.endsWith(".mkv", ignoreCase = true) ||
                                  originalFile.name.endsWith(".mov", ignoreCase = true) ||
                                  originalFile.name.endsWith(".webm", ignoreCase = true)
                                  
                    if (originalFile.extension.equals("gif", ignoreCase = true)) {
                        ioExecutor.execute {
                            try {
                                // BitmapFactory solo conservaría el primer frame. Para mantener
                                // la animación, el GIF se copia sin recodificar.
                                val destFile = File(storageDir, "media_${UUID.randomUUID()}.gif")
                                originalFile.copyTo(destFile, overwrite = false)
                                val list = addToPlaylist(destFile.absolutePath, type)
                                runOnUiThread { result.success(list) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("GIF_COPY_ERROR", e.message, null) }
                            }
                        }
                    } else if (isVideo) {
                        val destFile = File(storageDir, "crop_${System.currentTimeMillis()}.mp4")
                        
                        // Media3 Crop normalized coordinates: [-1.0f, 1.0f]
                        val media3Left = (-1.0f + 2.0f * cropX.toFloat()).coerceIn(-1.0f, 1.0f)
                        val media3Right = (-1.0f + 2.0f * (cropX + cropWidth).toFloat()).coerceIn(-1.0f, 1.0f)
                        val media3Top = (1.0f - 2.0f * cropY.toFloat()).coerceIn(-1.0f, 1.0f)
                        val media3Bottom = (1.0f - 2.0f * (cropY + cropHeight).toFloat()).coerceIn(-1.0f, 1.0f)
                        
                        try {
                            val cropEffect = androidx.media3.effect.Crop(media3Left, media3Right, media3Bottom, media3Top)
                            val effects = androidx.media3.transformer.Effects(emptyList(), listOf(cropEffect))
                            val editedMediaItem = androidx.media3.transformer.EditedMediaItem.Builder(
                                androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(originalFile))
                            )
                                .setRemoveAudio(true)
                                .setEffects(effects)
                                .build()
                                
                            val transformer = androidx.media3.transformer.Transformer.Builder(this)
                                .addListener(object : androidx.media3.transformer.Transformer.Listener {
                                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                                        val key = when(type) {
                                            "day" -> "playlist_day"
                                            "night" -> "playlist_night"
                                            else -> "playlist"
                                        }
                                        val currentPlaylist = prefs.getString(key, "") ?: ""
                                        val list = if (currentPlaylist.isEmpty()) mutableListOf() else currentPlaylist.split("||").toMutableList()
                                        list.add(destFile.absolutePath)
                                        editor.putString(key, list.joinToString("||")).commit()
                                        
                                        performDiskCleanup()
                                        val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED)
                                        intent.setPackage(packageName)
                                        sendBroadcast(intent)
                                        
                                        result.success(list)
                                    }
                                    
                                    override fun onError(
                                        composition: androidx.media3.transformer.Composition,
                                        exportResult: androidx.media3.transformer.ExportResult,
                                        exportException: androidx.media3.transformer.ExportException
                                    ) {
                                        exportException.printStackTrace()
                                        result.error("CROP_FAILED", exportException.message, null)
                                    }
                                })
                                .build()
                                
                            transformer.start(editedMediaItem, destFile.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            result.error("CROP_ERROR", e.message, null)
                        }
                    } else {
                        // Image or GIF
                      ioExecutor.execute crop@ {
                        try {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(originalFile.absolutePath, options)
                            val fullW = options.outWidth
                            val fullH = options.outHeight
                            
                            if (fullW <= 0 || fullH <= 0) {
                                runOnUiThread { result.error("INVALID_IMAGE", "Could not decode image bounds", null) }
                                return@crop
                            }

                            var sampleSize = 1
                            while (fullW / sampleSize > 2160 || fullH / sampleSize > 2160) {
                                sampleSize *= 2
                            }
                            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            val fullBitmap = BitmapFactory.decodeFile(originalFile.absolutePath, decodeOptions)
                            if (fullBitmap != null) {
                                val decodedW = fullBitmap.width
                                val decodedH = fullBitmap.height
                                val px = (cropX * decodedW).toInt().coerceIn(0, decodedW - 1)
                                val py = (cropY * decodedH).toInt().coerceIn(0, decodedH - 1)
                                val pw = (cropWidth * decodedW).toInt().coerceIn(1, decodedW - px)
                                val ph = (cropHeight * decodedH).toInt().coerceIn(1, decodedH - py)
                                val croppedBitmap = Bitmap.createBitmap(fullBitmap, px, py, pw, ph)
                                val destFile = File(storageDir, "crop_${System.currentTimeMillis()}.png")
                                FileOutputStream(destFile).use { out ->
                                    if (!croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                                        throw IllegalStateException("Failed to encode cropped image")
                                    }
                                    out.flush()
                                }
                                fullBitmap.recycle()
                                if (croppedBitmap !== fullBitmap) {
                                    croppedBitmap.recycle()
                                }
                                
                                val key = when(type) {
                                    "day" -> "playlist_day"
                                    "night" -> "playlist_night"
                                    else -> "playlist"
                                }
                                val currentPlaylist = prefs.getString(key, "") ?: ""
                                val list = if (currentPlaylist.isEmpty()) mutableListOf() else currentPlaylist.split("||").toMutableList()
                                list.add(destFile.absolutePath)
                                editor.putString(key, list.joinToString("||")).commit()
                                
                                performDiskCleanup()
                                val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED)
                                intent.setPackage(packageName)
                                sendBroadcast(intent)
                                
                                runOnUiThread { result.success(list) }
                            } else {
                                runOnUiThread { result.error("DECODE_ERROR", "Failed to decode bitmap", null) }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            runOnUiThread { result.error("IMAGE_CROP_ERROR", e.message, null) }
                        }
                      }
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun addToPlaylist(path: String, type: String): List<String> {
        val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        val key = when(type) {
            "day" -> "playlist_day"
            "night" -> "playlist_night"
            else -> "playlist"
        }
        val currentPlaylist = prefs.getString(key, "") ?: ""
        val list = if (currentPlaylist.isEmpty()) mutableListOf() else currentPlaylist.split("||").toMutableList()
        list.add(path)
        prefs.edit().putString(key, list.joinToString("||")).commit()
        performDiskCleanup()
        val intent = Intent(IxekenWallpaperService.ACTION_SETTINGS_CHANGED).setPackage(packageName)
        sendBroadcast(intent)
        return list
    }

    private fun isValidImageOrVideo(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val path = file.absolutePath
        return if (path.endsWith(".mp4", ignoreCase = true) || path.endsWith(".mkv", ignoreCase = true) || path.endsWith(".mov", ignoreCase = true) || path.endsWith(".webm", ignoreCase = true)) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration != null
            } catch (e: Exception) {
                false
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            options.outWidth > 0 && options.outHeight > 0
        }
    }

    private fun performDiskCleanup() {
        val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        val activeFiles = mutableSetOf<String>()
        
        val playlist = prefs.getString("playlist", "") ?: ""
        val playlistDay = prefs.getString("playlist_day", "") ?: ""
        val playlistNight = prefs.getString("playlist_night", "") ?: ""
        
        val allPaths = playlist.split("||") + playlistDay.split("||") + playlistNight.split("||")
        for (path in allPaths) {
            if (path.isNotEmpty()) {
                activeFiles.add(File(path).absolutePath)
            }
        }
        
        val wallpapersDir = File(filesDir, "wallpapers")
        if (wallpapersDir.exists() && wallpapersDir.isDirectory) {
            wallpapersDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    if (!activeFiles.contains(file.absolutePath)) {
                        file.delete()
                    }
                }
            }
        }
    }
}
