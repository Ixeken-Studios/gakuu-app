package com.ixeken.gakuu.engines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.random.Random

class StarfieldWallpaperEngine(context: Context) : BaseWallpaperEngine(context) {
    
    private val trailPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val stars = mutableListOf<Star>()
    private val numStars = 150
    
    private var isWarping = false
    private var currentSpeed = 6f
    private val normalSpeed = 6f
    private val warpSpeed = 32f

    private var touchX = 0f
    private var touchY = 0f
    private var smoothCx = 0f
    private var smoothCy = 0f

    companion object {
        private const val BG_COLOR = 0xFF030206.toInt()
    }

    override fun onCreate(holder: SurfaceHolder) {
        super.onCreate(holder)
        val w = holder.surfaceFrame.width()
        val h = holder.surfaceFrame.height()
        smoothCx = if (w > 0) w / 2f else 540f
        smoothCy = if (h > 0) h / 2f else 960f
        initStars(w, h)
    }

    private fun initStars(width: Int, height: Int) {
        stars.clear()
        val w = if (width > 0) width.toFloat() else 1080f
        val h = if (height > 0) height.toFloat() else 1920f
        
        repeat(numStars) {
            stars.add(Star(
                x = (Random.nextFloat() - 0.5f) * w,
                y = (Random.nextFloat() - 0.5f) * h,
                z = Random.nextFloat() * 1000f + 10f,
                prevZ = 0f,
                color = Color.argb(
                    Random.nextInt(100, 255),
                    Random.nextInt(180, 255),
                    Random.nextInt(200, 255),
                    255
                )
            ).apply { prevZ = z })
        }
    }

    override fun onUpdatePhysics() {
        // Interpolar suavemente hacia la velocidad objetivo
        val targetSpeed = if (isWarping) warpSpeed else normalSpeed
        currentSpeed += (targetSpeed - currentSpeed) * 0.12f
        
        val w = currentHolder?.surfaceFrame?.width()?.toFloat() ?: 1080f
        val h = currentHolder?.surfaceFrame?.height()?.toFloat() ?: 1920f

        val targetCx = if (isWarping) touchX else w / 2f
        val targetCy = if (isWarping) touchY else h / 2f
        smoothCx += (targetCx - smoothCx) * 0.08f
        smoothCy += (targetCy - smoothCy) * 0.08f

        for (s in stars) {
            s.prevZ = s.z
            s.z -= currentSpeed
            
            // Si la estrella se acerca demasiado o sale de la pantalla, reiniciar en la lejanía
            if (s.z <= 0f) {
                s.z = 1000f
                s.prevZ = 1000f
                s.x = (Random.nextFloat() - 0.5f) * w
                s.y = (Random.nextFloat() - 0.5f) * h
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        
        // Espacio negro profundo
        canvas.drawColor(BG_COLOR)
        
        val cx = smoothCx
        val cy = smoothCy
        val isFast = currentSpeed > 8f
        
        for (s in stars) {
            // Proyección 3D a 2D para posición actual
            val x2d = (s.x / s.z) * cx + cx
            val y2d = (s.y / s.z) * cy + cy
            
            // Proyección 3D a 2D para posición previa (rastro)
            val px2d = (s.x / s.prevZ) * cx + cx
            val py2d = (s.y / s.prevZ) * cy + cy
            
            // Omitir si está fuera de pantalla
            if (x2d < 0 || x2d > w || y2d < 0 || y2d > h) {
                // Forzar reinicio en el siguiente ciclo
                s.z = 0f
                continue
            }
            
            // Ancho del rastro según velocidad y cercanía
            val thickness = (1.0f - (s.z / 1000f)) * 4.5f + 1f
            
            // Si vamos rápido, dibuja líneas (rastro de movimiento/warp)
            if (isFast) {
                trailPaint.strokeWidth = thickness
                trailPaint.color = s.color
                canvas.drawLine(px2d, py2d, x2d, y2d, trailPaint)
            } else {
                // Dibujar como puntos redondos en velocidad normal
                pointPaint.color = s.color
                canvas.drawCircle(x2d, y2d, thickness * 0.7f, pointPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isWarping = true
                touchX = event.x
                touchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isWarping = false
            }
        }
    }

    override fun onVisibilityChanged(visible: Boolean) {
        super.onVisibilityChanged(visible)
        if (!visible) {
            isWarping = false
        }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        initStars(width, height)
    }

    data class Star(
        var x: Float,
        var y: Float,
        var z: Float,
        var prevZ: Float,
        val color: Int
    )
}
