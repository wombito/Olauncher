package app.olauncher.ui

import android.Manifest
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.WidgetType
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.ui.widgets.CalendarWidget
import app.olauncher.ui.widgets.EventsWidget
import app.olauncher.ui.widgets.ObsidianNoteWidget
import app.olauncher.ui.widgets.WidgetsAdapter
import app.olauncher.ui.widgets.YearProgressWidget
import kotlin.math.abs

private const val LONG_PRESS_MS = 1000L

class WidgetsFragment : BaseFragment() {

    private var _binding: FragmentWidgetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private lateinit var adapter: WidgetsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var draggingOverTrash = false
    private var dragMoved = false

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var touchDownX = 0f
    private var touchDownY = 0f

    private val calendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        adapter = WidgetsAdapter(
            viewFactory = ::createWidgetView,
            onReorder = { prefs.homeWidgets = it; updateEmptyHint() },
            onResize = { type, px -> prefs.widgetHeights = prefs.widgetHeights + (type to px) },
            onInnerScroll = ::cancelLongPress,
            onExitEditMode = ::exitEditMode,
        )
        binding.widgetList.layoutManager = LinearLayoutManager(requireContext())
        binding.widgetList.adapter = adapter
        binding.widgetList.itemAnimator?.changeDuration = 0

        itemTouchHelper = ItemTouchHelper(dragCallback())
        itemTouchHelper.attachToRecyclerView(binding.widgetList)
        binding.widgetList.addOnItemTouchListener(gestureObserver())

        binding.widgetAddButton.setOnClickListener { showAddWidgetDialog() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        exitEditMode()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        cancelLongPress()
        exitEditMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelLongPress()
        _binding = null
    }

    private fun refresh() {
        adapter.submit(prefs.homeWidgets, prefs.widgetHeights)
        updateEmptyHint()
    }

    private fun updateEmptyHint() {
        binding.widgetEmptyHint.isVisible = prefs.homeWidgets.isEmpty()
        val missing = WidgetType.entries.filterNot { prefs.homeWidgets.contains(it) }
        binding.widgetAddButton.isVisible = missing.isNotEmpty() && !adapter.editMode
    }

    private fun createWidgetView(type: WidgetType): View = when (type) {
        WidgetType.YEAR_PROGRESS -> YearProgressWidget(requireContext()).apply { bind() }

        WidgetType.CALENDAR -> CalendarWidget(requireContext()).apply {
            bind(viewLifecycleOwner.lifecycleScope) { requestCalendarPermission() }
        }

        WidgetType.EVENTS -> EventsWidget(requireContext()).apply {
            bind(viewLifecycleOwner.lifecycleScope) { requestCalendarPermission() }
        }

        WidgetType.OBSIDIAN_NOTE -> ObsidianNoteWidget(requireContext()).apply {
            bind(viewLifecycleOwner.lifecycleScope) {
                (requireActivity() as MainActivity).pickWidgetNote()
            }
        }
    }

    private fun showAddWidgetDialog() {
        val missing = WidgetType.entries.filterNot { prefs.homeWidgets.contains(it) }
        if (missing.isEmpty()) return
        val labels = missing.map { getString(labelOf(it)) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_widget)
            .setItems(labels) { _, which ->
                prefs.homeWidgets = prefs.homeWidgets + missing[which]
                refresh()
                binding.widgetList.post { binding.widgetList.smoothScrollToPosition(adapter.itemCount - 1) }
            }
            .show()
    }

    private fun labelOf(type: WidgetType): Int = when (type) {
        WidgetType.CALENDAR -> R.string.widget_calendar
        WidgetType.YEAR_PROGRESS -> R.string.widget_year_progress
        WidgetType.EVENTS -> R.string.widget_events
        WidgetType.OBSIDIAN_NOTE -> R.string.widget_obsidian_note
    }

    fun requestCalendarPermission() = calendarPermission.launch(Manifest.permission.READ_CALENDAR)

    // Light tick so the user feels that edit mode turned on.
    private fun buzz() = binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    private fun enterEditMode(holder: RecyclerView.ViewHolder) {
        if (!adapter.editMode) {
            adapter.setEditMode(true)
            binding.widgetAddButton.isVisible = false
            buzz()
        }
        showTrash(true)
        itemTouchHelper.startDrag(holder)
    }

    private fun exitEditMode() {
        cancelLongPress()
        draggingOverTrash = false
        showTrash(false)
        if (adapter.editMode) adapter.setEditMode(false)
        updateEmptyHint()
    }

    // A deliberate ~1s press enters edit mode; while in it a short press picks a widget up
    // again. A left fling (outside edit mode) leaves the screen.
    private fun gestureObserver(): RecyclerView.OnItemTouchListener {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (adapter.editMode) {
                    exitEditMode()
                    return true
                }
                return false
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!adapter.editMode && e1 != null &&
                    e1.x - e2.x > 120.dpToPx() && abs(velocityX) > abs(velocityY) * 2
                ) {
                    findNavController().popBackStack()
                    return true
                }
                return false
            }
        })
        return object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> scheduleLongPress(rv, e)
                    MotionEvent.ACTION_MOVE ->
                        if (abs(e.x - touchDownX) > slop || abs(e.y - touchDownY) > slop) cancelLongPress()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress()
                }
                detector.onTouchEvent(e)
                return false
            }
        }
    }

    private fun scheduleLongPress(rv: RecyclerView, e: MotionEvent) {
        cancelLongPress()
        touchDownX = e.x
        touchDownY = e.y
        if (adapter.isResizing) return
        val child = rv.findChildViewUnder(e.x, e.y) ?: return
        val handleTop = child.top + child.findViewById<View>(R.id.widgetResizeHandle).top
        if (adapter.editMode && e.y >= handleTop) return // a resize-handle touch, leave it be
        // Faster re-pickup once already in edit mode.
        val delay = if (adapter.editMode) 120L else LONG_PRESS_MS
        pendingLongPress = Runnable {
            pendingLongPress = null
            val holder = rv.findContainingViewHolder(child) ?: return@Runnable
            enterEditMode(holder)
        }.also { longPressHandler.postDelayed(it, delay) }
    }

    private fun cancelLongPress() {
        pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun dragCallback() = object : ItemTouchHelper.SimpleCallback(0, 0) {
        override fun isLongPressDragEnabled() = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            if (!adapter.editMode || adapter.isResizing) return 0
            return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            dragMoved = true
            adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) dragMoved = false
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return
            if (abs(dY) > 8.dpToPx()) dragMoved = true

            // "Over the trash" when the card was dragged downward far enough that its bottom
            // edge reaches the trash icon. Screen coordinates so it survives list auto-scroll.
            val trash = binding.widgetTrash
            val trashXY = IntArray(2).also { trash.getLocationOnScreen(it) }
            val cardXY = IntArray(2).also { viewHolder.itemView.getLocationOnScreen(it) }
            val trashCenterY = trashXY[1] + trash.height / 2
            val cardBottom = cardXY[1] + viewHolder.itemView.height
            val draggedDownEnough = dY > 72.dpToPx()
            val over = trash.isVisible && draggedDownEnough && cardBottom >= trashCenterY

            if (over != draggingOverTrash) {
                draggingOverTrash = over
                trash.animate()
                    .scaleX(if (over) 1.25f else 1f).scaleY(if (over) 1.25f else 1f)
                    .alpha(if (over) 1f else 0.5f).setDuration(120).start()
                if (over) trash.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val wasOverTrash = draggingOverTrash
            val didReorder = dragMoved
            draggingOverTrash = false
            dragMoved = false

            when {
                wasOverTrash -> {
                    viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    adapter.removeAt(viewHolder.bindingAdapterPosition)
                    exitEditMode() // dropping on the trash ends editing
                }

                didReorder -> {
                    adapter.persistOrder()
                    exitEditMode() // finishing a move ends editing
                }

                else -> {
                    // Bare pick-up-and-release: stay in edit mode so the resize handles remain.
                    adapter.persistOrder()
                    draggingOverTrash = false
                }
            }
        }
    }

    private fun showTrash(show: Boolean) {
        val trash = binding.widgetTrash
        trash.animate().cancel()
        if (show) {
            trash.isVisible = true
            trash.alpha = 0f
            trash.scaleX = 1f; trash.scaleY = 1f
            trash.animate().alpha(0.55f).setDuration(150).start()
        } else {
            trash.isVisible = false
            trash.alpha = 0f
        }
    }
}
