package com.ixeken.gakuu.engines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.SurfaceHolder
import kotlin.random.Random

class MatrixWallpaperEngine(context: Context) : BaseWallpaperEngine(context) {
    
    override val frameIntervalMs = 45L // ~22 FPS para movimiento cinemático clásico de Matrix

    private val headPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        setShadowLayer(14f, 0f, 0f, 0xFF34D399.toInt())
    }

    private val bodyPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val singleCharBuffer = CharArray(1)
    
    private var cols = 0
    private var charSize = 40f
    private val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ日ハミヒヘホマミムメモヤユヨラリルレロ".toCharArray()
    private val drops = mutableListOf<MatrixDrop>()

    companion object {
        private const val BG_COLOR = 0xFF020402.toInt()
    }
    
    data class MatrixDrop(
        val x: Float,
        var y: Float, // posición de la cabeza de la gota en caracteres
        val speed: Float,
        val length: Int,
        val columnChars: MutableList<Char>
    )

    override fun onCreate(holder: SurfaceHolder) {
        super.onCreate(holder)
        initMatrix(holder.surfaceFrame.width(), holder.surfaceFrame.height())
    }

    private fun initMatrix(width: Int, height: Int) {
        charSize = width.toFloat() / 25f // Fijar a 25 columnas para mejor densidad
        if (charSize < 32f) charSize = 32f
        
        val textSize = charSize * 0.85f
        headPaint.textSize = textSize
        bodyPaint.textSize = textSize
        cols = (width / charSize).toInt() + 1
        
        drops.clear()
        val rowsCount = (height / charSize).toInt() + 5
        
        repeat(cols) { colIndex ->
            val length = Random.nextInt(8, 20)
            val columnChars = MutableList(rowsCount) { chars[Random.nextInt(chars.size)] }
            drops.add(
                MatrixDrop(
                    x = colIndex * charSize + charSize / 2f,
                    y = Random.nextFloat() * -rowsCount, // Iniciar arriba aleatoriamente
                    speed = Random.nextFloat() * 0.35f + 0.15f, // velocidad lenta y variada
                    length = length,
                    columnChars = columnChars
                )
            )
        }
    }

    override fun onUpdatePhysics() {
        val rowsCount = ((currentHolder?.surfaceFrame?.height() ?: 1920) / charSize).toInt() + 2
        for (drop in drops) {
            // Actualizar posición de la cabeza
            drop.y += drop.speed
            if (drop.y - drop.length > rowsCount) {
                drop.y = -Random.nextInt(4, 12).toFloat()
            }
            
            // Mutar aleatoriamente caracteres del rastro
            if (Random.nextFloat() > 0.96f) {
                val idx = Random.nextInt(drop.columnChars.size)
                drop.columnChars[idx] = chars[Random.nextInt(chars.size)]
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Fondo negro profundo y sólido
        canvas.drawColor(BG_COLOR)
        
        for (drop in drops) {
            // Dibujar el trail de abajo hacia arriba (cabeza a cola)
            val headIndex = drop.y.toInt()
            for (j in 0 until drop.length) {
                val charYIdx = headIndex - j
                if (charYIdx < 0 || charYIdx >= drop.columnChars.size) continue
                
                val yPos = charYIdx * charSize + charSize
                singleCharBuffer[0] = drop.columnChars[charYIdx]
                
                if (j == 0) {
                    // La cabeza es blanca brillante con glow neón preconfigurado
                    canvas.drawText(singleCharBuffer, 0, 1, drop.x, yPos, headPaint)
                } else {
                    // El cuerpo es verde degradado sin shadow layer switch
                    val fraction = 1.0f - (j.toFloat() / drop.length.toFloat())
                    val alpha = (fraction * 255).toInt().coerceIn(0, 255)
                    bodyPaint.color = Color.argb(alpha, 16, 185, 129) // Emerald-500
                    canvas.drawText(singleCharBuffer, 0, 1, drop.x, yPos, bodyPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent) {
        if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
            if (charSize > 0f && drops.isNotEmpty()) {
                val colIndex = (event.x / charSize).toInt().coerceIn(0, drops.lastIndex)
                drops[colIndex].y = event.y / charSize
            }
        }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        initMatrix(width, height)
    }
}
