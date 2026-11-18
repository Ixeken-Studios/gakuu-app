package com.ixeken.gakuu.engines

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import java.io.File

class PatternWallpaperEngine(context: Context) : BaseWallpaperEngine(context) {

    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private var layoutSize = 2 // 1, 2, 3
    private var slotIcons = listOf("circle", "star", "heart", "cross")
    private var cellSize = 120f
    private var speed = 2f
    private var rotateEnabled = true

    private var offsetX = 0f
    private var offsetY = 0f
    private var rotationAngle = 0f

    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun onCreate(holder: SurfaceHolder) {
        super.onCreate(holder)
        loadSettings()
    }

    private fun loadSettings() {
        layoutSize = prefs.getInt("pattern_layout_size", 2)
        val defaultSlots = when (layoutSize) {
            1 -> "heart"
            3 -> "circle,star,heart,cross,triangle,square,star,heart,circle"
            else -> "circle,star,heart,cross"
        }
        val slotsStr = prefs.getString("pattern_slot_icons", defaultSlots) ?: defaultSlots
        slotIcons = slotsStr.split(",")

        val density = prefs.getString("pattern_density", "medium") ?: "medium"
        cellSize = when (density) {
            "small" -> 80f
            "large" -> 180f
            else -> 120f
        }

        speed = prefs.getInt("pattern_speed", 2).toFloat()
        rotateEnabled = prefs.getBoolean("pattern_rotate", true)

        // Clear cache and pre-decode bitmaps on this thread safely
        bitmapCache.clear()
        for (icon in slotIcons) {
            if (icon.isNotEmpty() && !isGenericShape(icon)) {
                loadBitmap(icon)
            }
        }
    }

    private fun isGenericShape(icon: String): Boolean {
        return icon == "circle" || icon == "square" || icon == "triangle" || 
               icon == "cross" || icon == "star" || icon == "heart"
    }

    private fun loadBitmap(path: String): Bitmap? {
        if (bitmapCache.containsKey(path)) return bitmapCache[path]
        return try {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val w = bitmap.width
                    val h = bitmap.height
                    val maxDim = 128f
                    val scale = if (w > h) maxDim / w else maxDim / h
                    val targetW = (w * scale).toInt().coerceAtLeast(1)
                    val targetH = (h * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    bitmapCache[path] = scaled
                    scaled
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun onUpdatePhysics() {
        val step = speed * 0.5f
        offsetX = (offsetX + step) % (cellSize * layoutSize)
        offsetY = (offsetY + step) % (cellSize * layoutSize)

        if (rotateEnabled) {
            rotationAngle = (rotationAngle + 1f) % 360f
        }
    }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val reusableDestRect = RectF()

    private val trianglePath = Path().apply {
        moveTo(0f, -0.4f)
        lineTo(0.4f, 0.4f)
        lineTo(-0.4f, 0.4f)
        close()
    }

    private val starPath = Path().apply {
        val innerR = 0.15f
        val outerR = 0.4f
        for (i in 0 until 10) {
            val angle = (i * 36f - 90f) * Math.PI.toFloat() / 180f
            val r = if (i % 2 == 0) outerR else innerR
            val x = r * kotlin.math.cos(angle)
            val y = r * kotlin.math.sin(angle)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private val heartPath = Path().apply {
        val w = 0.8f
        val h = 0.8f
        moveTo(0f, h * 0.35f)
        cubicTo(
            -w * 0.5f, -h * 0.25f,
            -w * 0.5f, -h * 0.7f,
            0f, -h * 0.3f
        )
        cubicTo(
            w * 0.5f, -h * 0.7f,
            w * 0.5f, -h * 0.25f,
            0f, h * 0.35f
        )
        close()
    }

    companion object {
        private const val BG_COLOR = 0xFF0F0F1B.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw background color
        canvas.drawColor(BG_COLOR)

        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        val cols = (w / cellSize).toInt() + 2
        val rows = (h / cellSize).toInt() + 2

        paint.color = Color.WHITE
        paint.alpha = 77 // ~30% transparency

        for (c in (-layoutSize - 1)..cols) {
            for (r in (-layoutSize - 1)..rows) {
                val px = c * cellSize + offsetX
                val py = r * cellSize + offsetY

                // Grid slot index determination (wrapping layout index)
                val gridCol = ((c % layoutSize) + layoutSize) % layoutSize
                val gridRow = ((r % layoutSize) + layoutSize) % layoutSize
                val slotIndex = gridRow * layoutSize + gridCol

                if (slotIndex >= 0 && slotIndex < slotIcons.size) {
                    val iconKey = slotIcons[slotIndex]
                    val cx = px + cellSize / 2f
                    val cy = py + cellSize / 2f

                    canvas.save()
                    canvas.translate(cx, cy)
                    if (rotateEnabled) {
                        canvas.rotate(rotationAngle)
                    }

                    drawSlot(canvas, iconKey, cellSize * 0.5f)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawSlot(canvas: Canvas, iconKey: String, size: Float) {
        val cached = bitmapCache[iconKey]
        if (cached != null) {
            val imgW = cached.width.toFloat()
            val imgH = cached.height.toFloat()
            val imgAspect = imgW / imgH
            val destW: Float
            val destH: Float
            if (imgAspect > 1f) {
                destW = size
                destH = size / imgAspect
            } else {
                destW = size * imgAspect
                destH = size
            }
            val halfW = destW / 2f
            val halfH = destH / 2f
            reusableDestRect.set(-halfW, -halfH, halfW, halfH)
            canvas.drawBitmap(cached, null, reusableDestRect, bmpPaint)
        } else {
            // Draw generic shape fallback
            drawGeometricShape(canvas, iconKey, size, paint)
        }
    }

    private fun drawGeometricShape(canvas: Canvas, shape: String, size: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.1f
        canvas.save()
        canvas.scale(size, size)
        when (shape) {
            "circle" -> canvas.drawCircle(0f, 0f, 0.4f, p)
            "square" -> canvas.drawRect(-0.35f, -0.35f, 0.35f, 0.35f, p)
            "triangle" -> canvas.drawPath(trianglePath, p)
            "cross" -> {
                canvas.drawLine(-0.3f, 0f, 0.3f, 0f, p)
                canvas.drawLine(0f, -0.3f, 0f, 0.3f, p)
            }
            "star" -> canvas.drawPath(starPath, p)
            "heart" -> canvas.drawPath(heartPath, p)
            else -> canvas.drawCircle(0f, 0f, 0.4f, p)
        }
        canvas.restore()
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        loadSettings()
    }
}
