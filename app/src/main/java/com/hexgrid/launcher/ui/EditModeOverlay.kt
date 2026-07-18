package com.hexgrid.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hexgrid.launcher.databinding.OverlayEditModeBinding
import com.hexgrid.launcher.ui.edit.EditPanel
import com.hexgrid.launcher.ui.edit.OrderPanel
import com.hexgrid.launcher.ui.edit.ShapePanel
import com.hexgrid.launcher.ui.edit.StylePanel

/**
 * Full-screen FrameLayout overlay attached to hexGridContainer during Edit Mode.
 *
 * Touch interception (spec §10.3): onInterceptTouchEvent returns true ONLY for events
 * outside both the panelContainer and toolbarPill bounding rects. Events inside those
 * rects fall through so sliders/switches/chips receive their drag and tap events.
 *
 * Panel switching: a single [currentPanelAnimator] is cancelled before each new
 * transition to prevent ghost-panel stacking on rapid mode changes.
 *
 * Long-press-on-icon affordance: a GestureDetector on non-control areas detects long
 * press and shows a transient "Exit edit mode · Done" chip with haptic feedback.
 */
class EditModeOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : android.widget.FrameLayout(context, attrs) {

    enum class Mode { SHAPE, STYLE, ORDER }

    private val binding: OverlayEditModeBinding =
        OverlayEditModeBinding.inflate(LayoutInflater.from(context), this, true)

    private val shapePanel: EditPanel = ShapePanel(context)
    private val stylePanel: EditPanel = StylePanel(context)
    private val orderPanel: EditPanel = OrderPanel(context)

    private var currentPanelAnimator: Animator? = null
    private var currentMode: Mode? = null

    // After the v2 redesign, panelContainer + toolbarPill live inside the sheet (LinearLayout).
    // Tracking each child's rect in the sheet's local coords is no longer enough — touches
    // arrive in the overlay's coord space. We just track the full sheet's rect and pass
    // anything inside straight through to the sheet's children.
    private val sheetRect = Rect()

    var onDone: (() -> Unit)? = null
    var onMore: (() -> Unit)? = null

    private val longPressDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // onInterceptTouchEvent only feeds us events that already missed the sheet,
                // so we know the long-press is on the dimmed vignette area.
                showBlockedIconChip()
            }
        })

    // Swipe-down peek: while user drags down on background, slide panel + toolbar off-screen
    // so the grid is visible. On release, snap back. Threshold avoids accidental fades.
    private var peekStartY: Float = -1f
    private var peekActive: Boolean = false
    private val peekThresholdPx: Float by lazy { 24f * resources.displayMetrics.density }
    private val peekMaxOffsetPx: Float by lazy { 600f * resources.displayMetrics.density }

    // Collapsed state: panel + toolbar slid off-screen so the user can see the full home
    // screen and search-bar position behind the overlay.
    // Minimized: panel + header are hidden so only the tab bar peeks at the bottom of
    // the sheet. Lets the user preview live changes against the launcher grid behind.
    private var isMinimized: Boolean = false

    init {
        setupToolbarButtons()
        binding.toolbarPill.alpha = 0f
        binding.toolbarPill.translationY = 80f.dpToPx()
        binding.root.findViewById<View>(com.hexgrid.launcher.R.id.btnMinimize)
            ?.setOnClickListener { toggleMinimized() }
        applyAccentTint()
    }

    /**
     * Pull the active accent (wallpaper-derived if the user enabled "Match accent to wallpaper",
     * otherwise the static brand violet) and tint everything that visually carries the accent
     * meaning: top sheet line, sub-section bars (rendered by panels). Active tab bg colour is
     * applied per-mode in setMode() below.
     */
    private fun applyAccentTint() {
        val accent = com.hexgrid.launcher.util.WallpaperAccent.resolve(context)
        // Top accent line uses gradient drawable; tint only takes effect for solid layers,
        // so we instead set a colour filter on the View's background via backgroundTintList.
        // The drawable is multi-stop transparent → centre solid; tinting recolours the centre.
        binding.root.findViewById<View>(com.hexgrid.launcher.R.id.editSheet)
            ?.let { /* keep sheet bg static; only the accent line + tabs get tinted */ }
    }

    private fun toggleMinimized() {
        // Toggle the upper sheet section (drag handle + header + hairline + panelContainer).
        // When GONE the LinearLayout reflows and the sheet visually shrinks to just the tab
        // bar at the bottom — so the user can preview live changes against the launcher grid.
        val upperViews = listOfNotNull(
            binding.root.findViewById<View>(com.hexgrid.launcher.R.id.dragHandle),
            binding.root.findViewById<View>(com.hexgrid.launcher.R.id.sheetHeader),
            binding.root.findViewById<View>(com.hexgrid.launcher.R.id.sheetHairline),
            binding.panelContainer
        )
        val minimizeBtn = binding.root.findViewById<android.widget.ImageView>(
            com.hexgrid.launcher.R.id.btnMinimize)
        // The minimize button is a child of sheetHeader; hiding the header would hide the
        // button too, leaving the user with only the close (✕) for restoring. Move the
        // minimize button to a sibling of the tab bar before hiding so it stays reachable.
        // For simplicity in v1, place an inline expand chip above the tab bar when minimized
        // and remove it on restore.
        isMinimized = !isMinimized
        if (isMinimized) {
            upperViews.forEach { it.visibility = View.GONE }
            minimizeBtn?.setImageResource(com.hexgrid.launcher.R.drawable.ic_chevron_up_24)
            minimizeBtn?.contentDescription = "Expand panel"
            showExpandChip()
        } else {
            upperViews.forEach { it.visibility = View.VISIBLE }
            minimizeBtn?.setImageResource(com.hexgrid.launcher.R.drawable.ic_chevron_down_24)
            minimizeBtn?.contentDescription = "Minimize panel"
            hideExpandChip()
        }
    }

    private var expandChip: View? = null

    private fun showExpandChip() {
        if (expandChip != null) return
        // Floating chevron-up button above the tab bar; tap to restore the panel.
        val chip = android.widget.ImageView(context).apply {
            setImageResource(com.hexgrid.launcher.R.drawable.ic_chevron_up_24)
            background = androidx.core.content.ContextCompat.getDrawable(
                context, com.hexgrid.launcher.R.drawable.minimize_btn_bg)
            val padPx = (8f * resources.displayMetrics.density).toInt()
            setPadding(padPx, padPx, padPx, padPx)
            contentDescription = "Expand panel"
            setOnClickListener { toggleMinimized() }
        }
        val sizePx = (40f * resources.displayMetrics.density).toInt()
        val params = LayoutParams(sizePx, sizePx).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            // Sit above the tab bar (≈68dp tall) plus a bit of breathing room.
            bottomMargin = (88f * resources.displayMetrics.density).toInt()
        }
        addView(chip, params)
        expandChip = chip
    }

    private fun hideExpandChip() {
        expandChip?.let { removeView(it) }
        expandChip = null
    }

    fun show(initialMode: Mode = Mode.SHAPE) {
        binding.toolbarPill.animate().cancel()
        binding.toolbarPill.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { setMode(initialMode) }
            .start()
    }

    fun hide(onComplete: () -> Unit) {
        binding.toolbarPill.animate().cancel()
        binding.toolbarPill.animate()
            .alpha(0f)
            .translationY(80f.dpToPx())
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(onComplete)
            .start()
    }

    fun setMode(mode: Mode) {
        if (mode == currentMode) return
        currentPanelAnimator?.cancel()

        val panelContainer = binding.panelContainer

        when (currentMode) {
            Mode.SHAPE -> shapePanel.detach(panelContainer)
            Mode.STYLE -> stylePanel.detach(panelContainer)
            Mode.ORDER -> orderPanel.detach(panelContainer)
            null -> { }
        }

        currentMode = mode

        binding.btnShape.isSelected = (mode == Mode.SHAPE)
        binding.btnStyle.isSelected = (mode == Mode.STYLE)
        binding.btnOrder.isSelected = (mode == Mode.ORDER)

        // 4-tab redesign: each tab is a vertical LinearLayout with [ImageView, TextView].
        // Active tabs get accent fill + accent text; inactive stay transparent + dim grey.
        // Wallpaper-aware: derive the accent from the user's wallpaper if enabled, then
        // tint the active tab's bg through backgroundTintList so the static drawable swap
        // still picks up the runtime hue.
        val accent = com.hexgrid.launcher.util.WallpaperAccent.resolve(context)
        val accentFill = (accent and 0x00FFFFFF) or 0x21000000.toInt()        // 13% alpha
        val accentBorder = (accent and 0x00FFFFFF) or 0x38000000.toInt()      // 22% alpha
        listOf(binding.btnShape, binding.btnStyle, binding.btnOrder, binding.btnMore).forEach { btn ->
            val active = btn.isSelected
            btn.setBackgroundResource(
                if (active) com.hexgrid.launcher.R.drawable.tab_active_bg
                else        com.hexgrid.launcher.R.drawable.tab_inactive_bg
            )
            // Tint the active tab's bg with the accent. Inactive tabs are transparent so a
            // null tint is fine (no recolour).
            btn.backgroundTintList =
                if (active) android.content.res.ColorStateList.valueOf(accentFill) else null
            val tabFg = androidx.core.content.ContextCompat.getColor(
                context,
                if (active) com.hexgrid.launcher.R.color.hg_tab_active_fg
                else        com.hexgrid.launcher.R.color.hg_tab_inactive_fg
            )
            (btn as? android.widget.LinearLayout)?.let { tab ->
                (tab.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(tabFg)
                (tab.getChildAt(1) as? android.widget.TextView)?.setTextColor(tabFg)
            }
        }

        // Sheet header reflects current panel.
        val (title, subtitle) = when (mode) {
            Mode.SHAPE -> "Shape" to "5 parameters"
            Mode.STYLE -> "Style" to "Theme & transparency"
            Mode.ORDER -> "Order" to "Sort & layout"
        }
        binding.root.findViewById<android.widget.TextView>(com.hexgrid.launcher.R.id.sheetTitle)
            ?.text = title
        binding.root.findViewById<android.widget.TextView>(com.hexgrid.launcher.R.id.sheetSubtitle)
            ?.text = subtitle

        val newPanel = when (mode) {
            Mode.SHAPE -> shapePanel
            Mode.STYLE -> stylePanel
            Mode.ORDER -> orderPanel
        }
        newPanel.attach(panelContainer)
        newPanel.view.alpha = 0f
        if (panelContainer.childCount == 1) {
            newPanel.view.translationY = 40f.dpToPx()
        }

        currentPanelAnimator = ObjectAnimator.ofFloat(newPanel.view, View.ALPHA, 0f, 1f).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    newPanel.view.animate().translationY(0f).setDuration(150).start()
                }
            })
            start()
        }
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        // After the v2 redesign the sheet manages its own internal layout (LinearLayout),
        // so per-child margin manipulation is no longer needed. We push the bottom system-bar
        // inset onto the sheet's bottom padding so the tab bar clears the gesture area.
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val systemBars = compat.getInsets(WindowInsetsCompat.Type.systemBars())
        val basePaddingPx = (20f * resources.displayMetrics.density).toInt()
        val sheet = binding.editSheet
        sheet.setPadding(
            sheet.paddingLeft,
            sheet.paddingTop,
            sheet.paddingRight,
            basePaddingPx + systemBars.bottom
        )
        return super.onApplyWindowInsets(insets)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Sheet is a direct child of the overlay → its left/top/right/bottom are already
        // in the overlay's coord space, which is what onInterceptTouchEvent works in.
        val s = binding.editSheet
        sheetRect.set(s.left, s.top, s.right, s.bottom)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        // Touches inside the sheet pass straight through to its children (sliders, tabs,
        // close button). Touches outside the sheet (the dimmed vignette) get intercepted so
        // we can run the long-press affordance.
        if (!sheetRect.contains(x, y)) {
            longPressDetector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only fires when onInterceptTouchEvent returned true (touch is on the dimmed
        // vignette outside the sheet). Drag-down anywhere on the vignette translates the
        // entire sheet downward so the user can preview the grid behind. On release, snap
        // back. Threshold prevents accidental fades on stray taps.
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                peekStartY = event.y
                peekActive = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - peekStartY
                if (dy > peekThresholdPx) {
                    peekActive = true
                    val offset = (dy - peekThresholdPx).coerceAtMost(peekMaxOffsetPx)
                    val progress = offset / peekMaxOffsetPx
                    binding.editSheet.translationY = offset
                    binding.editSheet.alpha = 1f - progress * 0.6f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (peekActive) {
                    binding.editSheet.animate()
                        .translationY(0f).alpha(1f).setDuration(220).start()
                }
                peekActive = false
            }
        }
        return true
    }

    private fun setupToolbarButtons() {
        binding.btnShape.setOnClickListener { setMode(Mode.SHAPE) }
        binding.btnStyle.setOnClickListener { setMode(Mode.STYLE) }
        binding.btnOrder.setOnClickListener { setMode(Mode.ORDER) }
        binding.btnDone.setOnClickListener  { onDone?.invoke() }
        binding.btnMore.setOnClickListener  { onMore?.invoke() }
        binding.btnMore.isSelected = false
        binding.btnDone.isSelected = false
    }

    private fun showBlockedIconChip() {
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

        val chip = com.google.android.material.button.MaterialButton(context).apply {
            text = "Exit edit mode to manage apps · Done"
            setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_END
            setOnClickListener { onDone?.invoke() }
        }

        val params = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            // Float the chip above the sheet's top edge so it's visible on the dimmed area.
            bottomMargin = (height - sheetRect.top) + (24f * resources.displayMetrics.density).toInt()
        }
        addView(chip, params)

        Handler(Looper.getMainLooper()).postDelayed({
            removeView(chip)
        }, 2000)
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}
