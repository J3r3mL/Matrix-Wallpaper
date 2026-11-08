package com.example.matrixwallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.random.Random

class MatrixWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = MatrixEngine()

    inner class MatrixEngine : Engine() {

        // ---- Réglages faciles à modifier ----
        private val fontSize = 42f          // taille des caractères (px)
        private val frameDelayMs = 55L      // ~18 images/s (économise la batterie)
        private val trailFade = 28          // 0-255 : plus grand = traînée plus courte
        private val resetChance = 0.025f    // probabilité qu'une colonne redémarre en haut
        private val symbols =
            "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:.=*+-<>¦"

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0
        private var rows = 0
        private var drops = IntArray(0)     // ligne courante de la "tête" de chaque colonne

        private var buffer: Bitmap? = null
        private var bufferCanvas: Canvas? = null

        private val bgPaint = Paint().apply { color = Color.BLACK }
        private val fadePaint = Paint().apply { color = Color.argb(trailFade, 0, 0, 0) }
        private val headPaint = textPaint(Color.rgb(200, 255, 210))
        private val bodyPaint = textPaint(Color.rgb(0, 230, 70))

        private fun textPaint(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c
            textSize = fontSize
            typeface = Typeface.MONOSPACE
        }

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, frameDelayMs)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            rows = (h / fontSize).toInt() + 1
            val cols = (w / fontSize).toInt() + 1
            drops = IntArray(cols) { -Random.nextInt(rows) }  // départs décalés

            buffer?.recycle()
            buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                bufferCanvas = Canvas(it).apply { drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint) }
            }
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(drawRunnable)
            if (isVisible) handler.post(drawRunnable)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            buffer?.recycle()
            buffer = null
        }

        private fun randomChar(): String = symbols[Random.nextInt(symbols.length)].toString()

        private fun drawFrame() {
            val bc = bufferCanvas ?: return
            val bmp = buffer ?: return

            // 1) Estompe l'image précédente -> effet de traînée
            bc.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)

            // 2) Fait avancer chaque colonne
            for (i in drops.indices) {
                val x = i * fontSize
                val row = drops[i]

                if (row >= 0) {
                    // Ancienne tête (blanche) -> devient verte
                    if (row > 0) {
                        val prevY = row * fontSize
                        bc.drawRect(x, prevY - fontSize, x + fontSize, prevY, bgPaint)
                        bc.drawText(randomChar(), x, prevY, bodyPaint)
                    }
                    // Nouvelle tête brillante
                    val y = (row + 1) * fontSize
                    bc.drawRect(x, y - fontSize, x + fontSize, y, bgPaint)
                    bc.drawText(randomChar(), x, y, headPaint)
                }

                drops[i] = if (row * fontSize > height && Random.nextFloat() < resetChance * 4) {
                    -Random.nextInt(rows / 2 + 1)
                } else if (row * fontSize > height && Random.nextFloat() < resetChance) {
                    0
                } else {
                    row + 1
                }
            }

            // 3) Affiche le buffer à l'écran
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.drawBitmap(bmp, 0f, 0f, null)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
