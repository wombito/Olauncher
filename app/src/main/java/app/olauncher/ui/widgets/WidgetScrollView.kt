package app.olauncher.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * A [NestedScrollView] that keeps the touch to itself while its content overflows, so it can
 * scroll independently inside a RecyclerView row instead of the whole list scrolling.
 */
class WidgetScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NestedScrollView(context, attrs) {

    /** Invoked when the user actually scrolls the content — used to abort a pending long-press. */
    var onUserScroll: (() -> Unit)? = null

    private fun overflows() = canScrollVertically(1) || canScrollVertically(-1)

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) onUserScroll?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(overflows())
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(overflows())
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.onTouchEvent(ev)
    }
}
