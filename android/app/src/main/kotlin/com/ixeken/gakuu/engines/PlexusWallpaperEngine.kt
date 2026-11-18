package com.ixeken.gakuu.engines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.random.Random

class PlexusWallpaperEngine(context: Context) : BaseWallpaperEngine(context) {
    
    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.CYAN
    }

    private val touchLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.CYAN
    }

    private val nodePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 210
    }

    private val particles = mutableListOf<Node>()
    private val numNodes = 50
    private val connectionDistance = 250f
    private val connectionDistSq = connectionDistance * connectionDistance

    private var isTouching = false
    private var touchX = 0f
    private var touchY = 0f

    companion object {
        private const val BG_COLOR = 0xFF050A10.toInt()
    }

    override fun onCreate(holder: SurfaceHolder) {
        super.onCreate(holder)
        initNodes(holder.surfaceFrame.width(), holder.surfaceFrame.height())
    }

    private fun initNodes(width: Int, height: Int) {
        particles.clear()
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 1920
        repeat(numNodes) {
            particles.add(Node(
                x = Random.nextFloat() * w,
                y = Random.nextFloat() * h,
                vx = (Random.nextFloat() - 0.5f) * 2f,
                vy = (Random.nextFloat() - 0.5f) * 2f
            ))
        }
    }

    override fun onUpdatePhysics() {
        val w = currentHolder?.surfaceFrame?.width()?.toFloat() ?: 1080f
        val h = currentHolder?.surfaceFrame?.height()?.toFloat() ?: 1920f

        // Interacción táctil: atracción hacia el dedo
        if (isTouching) {
            for (n in particles) {
                val dx = touchX - n.x
                val dy = touchY - n.y
                val distSq = dx * dx + dy * dy
                if (distSq in 1f..160000f) { // Radio de 400px
                    val dist = kotlin.math.sqrt(distSq)
                    val force = (1f - dist / 400f) * 1.5f
                    n.vx += (dx / dist) * force
                    n.vy += (dy / dist) * force
                }
            }
        }

        // Actualizar posiciones y amortiguar suavemente
        for (n in particles) {
            n.vx *= 0.98f
            n.vy *= 0.98f
            n.x += n.vx
            n.y += n.vy
            if (n.x < 0 || n.x > w) n.vx *= -1f
            if (n.y < 0 || n.y > h) n.vy *= -1f
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG_COLOR)
        
        // Dibujar conexiones entre nodos
        for (i in 0 until particles.size) {
            val p1 = particles[i]
            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val distSq = dx * dx + dy * dy
                
                if (distSq < connectionDistSq) {
                    val dist = kotlin.math.sqrt(distSq)
                    val alpha = ((1.0f - dist / connectionDistance) * 150f).toInt()
                    linePaint.alpha = alpha
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                }
            }

            // Conexiones dinámicas hacia el punto de toque
            if (isTouching) {
                val dx = touchX - p1.x
                val dy = touchY - p1.y
                val distSq = dx * dx + dy * dy
                val touchMaxSq = 320f * 320f
                if (distSq < touchMaxSq) {
                    val dist = kotlin.math.sqrt(distSq)
                    val alpha = ((1.0f - dist / 320f) * 220f).toInt()
                    touchLinePaint.alpha = alpha
                    canvas.drawLine(touchX, touchY, p1.x, p1.y, touchLinePaint)
                }
            }
        }

        // Dibujar nodos
        for (n in particles) {
            canvas.drawCircle(n.x, n.y, 4f, nodePaint)
        }
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

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        initNodes(width, height)
    }

    data class Node(var x: Float, var y: Float, var vx: Float, var vy: Float)
}

