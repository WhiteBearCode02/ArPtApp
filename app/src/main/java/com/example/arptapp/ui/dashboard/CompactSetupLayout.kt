package com.example.arptapp.ui.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Presentation-only, shared by both dashboard layouts. Prefer one compact centered row; switch
 * to a content-height column when translated labels/large fonts cannot fit. Children keep normal
 * click/long-click handling and their 48dp hit targets. GONE children reserve no space.
 */
class CompactSetupLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val bounded = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        var rowWidth = 0L
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val margins = child.layoutParams as LayoutParams
            rowWidth += child.measuredWidth.toLong() + margins.leftMargin + margins.rightMargin
        }
        val next = if (bounded && rowWidth > available) VERTICAL else HORIZONTAL
        if (orientation != next) orientation = next
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
