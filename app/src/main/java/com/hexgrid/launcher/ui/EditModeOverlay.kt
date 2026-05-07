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

    private val panelRect   = Rect()
    private val toolbarRect = Rect()

    var onDone: (() -> Unit)? = null
    var onMore: (() -> Unit)? = null

    private val longPressDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (!panelRect.contains(e.x.toInt(), e.y.toInt()) &&
                    !toolbarRect.contains(e.x.toInt(), e.y.toInt())) {
                    showBlockedIconChip()
                }
            }
        })

    init {
        setupToolbarButtons()
        binding.toolbarPill.alpha = 0f
        binding.toolbarPill.translationY = 80f.dpToPx()
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
        listOf(binding.btnShape, binding.btnStyle, binding.btnOrder).forEach { btn ->
            btn.setBackgroundResource(
                if (btn.isSelected) com.hexgrid.launcher.R.drawable.tile_squircle_primary
                else                com.hexgrid.launcher.R.drawable.tile_squircle_secondary
            )
        }

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
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val systemBars = compat.getInsets(WindowInsetsCompat.Type.systemBars())
        val tappable   = compat.getInsets(WindowInsetsCompat.Type.tappableElement())

        val marginDp16 = (16f * resources.displayMetrics.density).toInt()
        val marginDp8  = (8f  * resources.displayMetrics.density).toInt()
        val computedBottomMargin = maxOf(
            systemBars.bottom + marginDp16,
            tappable.bottom   + marginDp8
        )

        // FIX: original plan body had `bottomMargin = bottomMargin` (self-assign of shadowed
        // local). The fix uses a renamed local `computedBottomMargin` and assigns it directly
        // to the LayoutParams field via `also`/explicit field access.
        val toolbarLp = binding.toolbarPill.layoutParams as LayoutParams
        toolbarLp.bottomMargin = computedBottomMargin
        binding.toolbarPill.layoutParams = toolbarLp

        val toolbarHeightPx = (72f * resources.displayMetrics.density).toInt()
        val panelLp = binding.panelContainer.layoutParams as LayoutParams
        panelLp.bottomMargin = computedBottomMargin + toolbarHeightPx + marginDp8
        binding.panelContainer.layoutParams = panelLp

        return super.onApplyWindowInsets(insets)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val pc = binding.panelContainer
        panelRect.set(pc.left, pc.top, pc.right, pc.bottom)
        val tb = binding.toolbarPill
        toolbarRect.set(tb.left, tb.top, tb.right, tb.bottom)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        val insidePanel   = panelRect.contains(x, y)
        val insideToolbar = toolbarRect.contains(x, y)
        if (!insidePanel && !insideToolbar) {
            longPressDetector.onTouchEvent(ev)
            return true
        }
        return false
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
            bottomMargin = (toolbarRect.height() + (80f * resources.displayMetrics.density).toInt())
        }
        addView(chip, params)

        Handler(Looper.getMainLooper()).postDelayed({
            removeView(chip)
        }, 2000)
    }

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}
