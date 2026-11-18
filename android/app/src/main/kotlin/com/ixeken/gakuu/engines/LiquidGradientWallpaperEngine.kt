package com.ixeken.gakuu.engines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LiquidGradientWallpaperEngine(context: Context) : BaseWallpaperEngine(context) {
    
    private var time = 0f
    private val shaderMatrix = Matrix()

    private var isTouching = false
    private var touchX = 0f
    private var touchY = 0f
    private var touchInfluenceX = 0f
    private var touchInfluenceY = 0f

    companion object {
        private const val BG_COLOR = 0xFF080512.toInt()
    }

    private fun createBlobPaint(colorInt: Int, opacity: Float): Paint {
        val alphaColor = Color.argb(
            (opacity * 255).toInt().coerceIn(0, 255),
            Color.red(colorInt),
            Color.green(colorInt),
            Color.blue(colorInt)
        )
        val colors = intArrayOf(alphaColor, Color.TRANSPARENT)
        val shader = RadialGradient(0f, 0f, 1f, colors, null, Shader.TileMode.CLAMP)
        return Paint().apply {
            isAntiAlias = true
            this.shader = shader
        }
    }

    private val paintBlob1 = createBlobPaint(Color.parseColor("#6366F1"), 0.33f) // Indigo
    private val paintBlob2 = createBlobPaint(Color.parseColor("#EC4899"), 0.28f) // Rose
    private val paintBlob3 = createBlobPaint(Color.parseColor("#06B6D4"), 0.26f) // Cyan
    private val paintBlob4 = createBlobPaint(Color.parseColor("#8B5CF6"), 0.30f) // Violet
    
    override fun onUpdatePhysics() {
        time += 0.002f // Movimiento extremadamente suave y lento

        if (isTouching) {
            touchInfluenceX += (touchX - touchInfluenceX) * 0.05f
            touchInfluenceY += (touchY - touchInfluenceY) * 0.05f
        } else {
            touchInfluenceX *= 0.95f
            touchInfluenceY *= 0.95f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        
        // 1. Fondo base muy oscuro (azul espacial profundo)
        canvas.drawColor(BG_COLOR)

        val txOffset = if (isTouching) (touchInfluenceX - w * 0.5f) * 0.15f else 0f
        val tyOffset = if (isTouching) (touchInfluenceY - h * 0.5f) * 0.15f else 0f
        
        // 2. Dibujar 4 blobs de colores líquidos en movimiento orgánico desfasado
        // Blob 1: Púrpura brillante, se mueve arriba a la izquierda
        val x1 = w * 0.35f + sin(time) * (w * 0.18f) + txOffset
        val y1 = h * 0.3f + cos(time * 0.9f) * (h * 0.12f) + tyOffset
        drawBlob(canvas, x1, y1, w * 0.65f, paintBlob1)
        
        // Blob 2: Magenta, se mueve abajo a la derecha
        val x2 = w * 0.65f + cos(time * 1.1f) * (w * 0.2f) - txOffset * 0.7f
        val y2 = h * 0.7f + sin(time * 0.8f) * (h * 0.15f) - tyOffset * 0.7f
        drawBlob(canvas, x2, y2, w * 0.75f, paintBlob2)
        
        // Blob 3: Cian, se mueve en el centro / Lissajous
        val x3 = w * 0.5f + sin(time * 0.7f) * (w * 0.22f) + txOffset * 0.5f
        val y3 = h * 0.5f + cos(time * 1.3f) * (h * 0.18f) + tyOffset * 0.5f
        drawBlob(canvas, x3, y3, w * 0.6f, paintBlob3)
        
        // Blob 4: Violeta, se mueve arriba a la derecha
        val x4 = w * 0.6f + cos(time * 0.6f) * (w * 0.25f)
        val y4 = h * 0.4f + sin(time * 0.7f) * (h * 0.2f)
        drawBlob(canvas, x4, y4, w * 0.7f, paintBlob4)
    }
    
    private fun drawBlob(canvas: Canvas, x: Float, y: Float, radius: Float, paint: Paint) {
        shaderMatrix.reset()
        shaderMatrix.postScale(radius, radius)
        shaderMatrix.postTranslate(x, y)
        paint.shader?.setLocalMatrix(shaderMatrix)
        
        // Dibujar círculo que contiene el gradiente radial (acelerado por hardware, sin reasignar shaders)
        canvas.drawCircle(x, y, radius, paint)
    }

    override fun onTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isTouching = true
                touchX = event.x
                touchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
            }
        }
    }

    override fun onVisibilityChanged(visible: Boolean) {
        super.onVisibilityChanged(visible)
        if (!visible) {
            isTouching = false
        }
    }
}

