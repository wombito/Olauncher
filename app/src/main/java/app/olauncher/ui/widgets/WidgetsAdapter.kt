package app.olauncher.ui.widgets

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.WidgetType
import app.olauncher.helper.dpToPx

private const val PAYLOAD_CHROME = "chrome"
private val MIN_HEIGHT_PX = 120.dpToPx()
private val MAX_HEIGHT_PX = 900.dpToPx()

class WidgetsAdapter(
    private val viewFactory: (WidgetType) -> View,
    private val onReorder: (List<WidgetType>) -> Unit,
    private val onResize: (WidgetType, Int) -> Unit,
    private val onInnerScroll: () -> Unit,
    private val onExitEditMode: () -> Unit,
) : RecyclerView.Adapter<WidgetsAdapter.VH>() {

    val items = mutableListOf<WidgetType>()
    private val heights = mutableMapOf<WidgetType, Int>()

    /** Edit mode: dim every card, show its resize handle, block widget taps. */
    var editMode = false
        private set

    /** True while a resize handle is being dragged, so the list suppresses reorder drags. */
    var isResizing = false
        private set

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        if (!enabled) isResizing = false
        notifyItemRangeChanged(0, itemCount, PAYLOAD_CHROME)
    }

    fun updateHeight(type: WidgetType, px: Int) {
        heights[type] = px
    }

    fun submit(list: List<WidgetType>, storedHeights: Map<WidgetType, Int>) {
        items.clear(); items.addAll(list)
        heights.clear(); heights.putAll(storedHeights)
        notifyDataSetChanged()
    }

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    fun persistOrder() = onReorder(items.toList())

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        onReorder(items.toList())
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_widget_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_CHROME)) {
            applyChrome(holder)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val type = items[position]
        holder.body.removeAllViews()
        val widgetView = viewFactory(type)
        holder.body.addView(
            widgetView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        findScrollView(widgetView)?.onUserScroll = onInnerScroll

        val defaultHeight = type.defaultHeightDp.takeIf { it > 0 }?.dpToPx()
        holder.viewport.updateLayoutParams {
            height = heights[type] ?: defaultHeight ?: ViewGroup.LayoutParams.WRAP_CONTENT
        }
        holder.resizeHandle.setOnTouchListener(ResizeTouchListener(holder, type))
        holder.manageOverlay.setOnClickListener { onExitEditMode() }
        applyChrome(holder)
    }

    override fun onViewRecycled(holder: VH) {
        holder.body.removeAllViews()
        holder.resizeHandle.setOnTouchListener(null)
    }

    private fun applyChrome(holder: VH) {
        holder.resizeHandle.isVisible = editMode
        holder.manageOverlay.isVisible = editMode
        holder.itemView.animate()
            .scaleX(if (editMode) 0.97f else 1f)
            .scaleY(if (editMode) 0.97f else 1f)
            .setDuration(140).start()
    }

    private fun findScrollView(root: View): WidgetScrollView? = when {
        root is WidgetScrollView -> root
        root is ViewGroup -> (0 until root.childCount)
            .firstNotNullOfOrNull { findScrollView(root.getChildAt(it)) }

        else -> null
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val body: ViewGroup = view.findViewById(R.id.widgetBody)
        val viewport: View = view.findViewById(R.id.widgetViewport)
        val resizeHandle: View = view.findViewById(R.id.widgetResizeHandle)
        val manageOverlay: View = view.findViewById(R.id.widgetManageOverlay)
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class ResizeTouchListener(
        private val holder: VH,
        private val type: WidgetType,
    ) : View.OnTouchListener {
        private var startY = 0f
        private var startHeight = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = holder.viewport.height
                    isResizing = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val target = (startHeight + (event.rawY - startY)).toInt()
                        .coerceIn(MIN_HEIGHT_PX, MAX_HEIGHT_PX)
                    holder.viewport.updateLayoutParams { height = target }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    val finalHeight = holder.viewport.height
                    updateHeight(type, finalHeight)
                    onResize(type, finalHeight)
                    v.performClick()
                    // Resizing keeps edit mode active — the user taps elsewhere to leave.
                }
            }
            return true
        }
    }
}
