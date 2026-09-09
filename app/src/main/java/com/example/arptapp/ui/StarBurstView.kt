package com.example.arptapp.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Decorative star field for the training portal. It has no business-logic role. */
class StarBurstView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = mutableListOf<Star>()
    private var orbitProgress = 0f
    private var burstProgress = 0f
    private var orbitAnimator: ValueAnimator? = null
    private var burstAnimator: ValueAnimator? = null

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        stars.clear()
        val random = Random(20260910)
        repeat(STAR_COUNT) {
            stars += Star(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                radius = 1.4f + random.nextFloat() * 2.8f,
                phase = random.nextFloat() * (Math.PI * 2).toFloat()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        stars.forEachIndexed { index, star ->
            val orbitAngle = star.phase + orbitProgress * 2.2f + index * 0.12f
            val burstDistance = burstProgress * (32f + (index % 5) * 16f)
            val x = star.x + cos(orbitAngle) * burstDistance
            val y = star.y + sin(orbitAngle) * burstDistance
            val twinkle = 0.45f + 0.55f * ((sin(orbitAngle * 1.8f) + 1f) / 2f)
            paint.color = if (index % 4 == 0) Color.rgb(145, 211, 255) else Color.WHITE
            paint.alpha = (255 * twinkle * (1f - burstProgress * 0.35f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, star.radius * (1f + burstProgress), paint)
        }
        paint.color = Color.argb((55 * orbitProgress).toInt(), 131, 195, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawCircle(centerX, centerY, 26f + orbitProgress * 70f, paint)
        paint.style = Paint.Style.FILL
    }

    fun startOrbit() {
        if (orbitAnimator?.isRunning == true) return
        orbitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { orbitProgress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stopOrbit() {
        orbitAnimator?.cancel()
        orbitAnimator = null
        orbitProgress = 0f
        invalidate()
    }

    fun burst(onFinished: () -> Unit) {
        if (burstAnimator?.isRunning == true) return
        burstAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { burstProgress = it.animatedValue as Float; invalidate() }
            doOnEndCompat {
                burstProgress = 0f
                invalidate()
                onFinished()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        orbitAnimator?.cancel()
        burstAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun ValueAnimator.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }

    private companion object {
        const val STAR_COUNT = 34
    }
}
