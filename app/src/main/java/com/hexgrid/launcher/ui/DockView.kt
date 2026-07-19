package com.hexgrid.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.hexgrid.launcher.R
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.util.SettingsManager
import kotlin.math.abs

/**
 * Dock bar with pinned apps and an inline app-search field.
 *
 * One UI 8 styling: a pill-shaped floating island. The pill doubles as a
 * "slide-to-trigger" control — drag it right to open search, left to launch
 * the Assistant, revealing a directional colour ribbon that arms at a notch.
 * Settings is no longer part of the dock; it lives as a corner button owned
 * by MainActivity.
 *
 * Motion uses one shared curve — PathInterpolator(0.22, 0.25, 0, 1) — which
 * matches One UI's basic motion and Material 3's standard-emphasized easing:
 * a quick start with a gentle settle.
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dockApps = mutableListOf<AppInfo>()

    // View references
    private lateinit var pill: LinearLayout
    private lateinit var searchIcon: ImageView
    private lateinit var appsScrollView: HorizontalScrollView
    private lateinit var appsContainer: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var micIcon: ImageView
    private lateinit var searchCloseIcon: ImageView
    // Invisible right-side counterweight so pinned apps stay centered in the pill
    // now that Settings no longer occupies the right slot (it moved to the corner).
    private lateinit var rightSpacer: View

    // State
    private var isSearchMode = false

    // Callbacks
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    var onSearchTextChanged: ((String) -> Unit)? = null
    var onAssistantSwipe: (() -> Unit)? = null
    var onMicClick: (() -> Unit)? = null
    var onSearchModeChanged: ((Boolean) -> Unit)? = null

    private var allAppsRef: List<AppInfo> = emptyList()

    // ── Motion ────────────────────────────────────────────────────────────────
    private val emphasized = PathInterpolator(0.22f, 0.25f, 0f, 1f)
    private val durMorph = 300L
    private val durFast = 200L

    // ── Slide-to-trigger ribbon ────────────────────────────────────────────────
    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val searchColor = ContextCompat.getColor(context, R.color.ribbon_search)
    private val assistColor = ContextCompat.getColor(context, R.color.ribbon_assist)
    private val ribbonSearchIcon: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_search_24)?.mutate()?.apply { setTint(Color.WHITE) }
    private val ribbonAssistIcon: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_assistant_24)?.mutate()?.apply { setTint(Color.WHITE) }

    private var pillTx = 0f          // current visual translation of the pill
    private var dragRawDx = 0f       // raw finger delta (drives arm threshold + progress)
    private var swiping = false
    private var downX = 0f
    private var downY = 0f
    private var downOnApp = false
    private var armed = false
    private val touchSlop = (8 * resources.displayMetrics.density)
    private val commitThreshold get() = width * 0.20f   // catch/commit at 20%
    private val maxTravel get() = width * 0.55f          // full-drag ceiling
    private val ribbonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15 * resources.displayMetrics.density
        isFakeBoldText = true
    }

    init {
        orientation = HORIZONTAL
        elevation = 8f
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        setWillNotDraw(false)

        buildPill()
        setupDragListener()
        applyTransparency()
        rebuildDock()
    }

    private fun buildPill() {
        pill = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            setBackgroundResource(R.drawable.dock_bg_oneui)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        addView(pill)
        initViews()
    }

    private fun initViews() {
        // 1. Search Icon (Left) — anchor; taps enter search
        searchIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply { marginEnd = dpToPx(8) }
            setImageResource(R.drawable.ic_search_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener { if (isSearchMode) exitSearchMode() else enterSearchMode() }
        }

        // 2. Apps ScrollView (Center)
        appsScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dpToPx(20))
        }
        appsContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        appsScrollView.addView(appsContainer)

        // 3. Search EditText (hidden by default)
        searchEditText = EditText(context).apply {
            layoutParams = LayoutParams(0, dpToPx(40), 1f).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(4)
            }
            hint = "Search apps…"
            setHintTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            background = null
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
            visibility = View.GONE
            alpha = 0f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onSearchTextChanged?.invoke(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false
            }
        }

        // 4. Mic Icon (inside field, trailing) — gated by search_with_mic
        micIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(36), dpToPx(36)).apply { marginEnd = dpToPx(2) }
            setImageResource(R.drawable.ic_mic_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { onMicClick?.invoke() }
        }

        // 5. Search Close Icon
        searchCloseIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(36), dpToPx(36))
            setImageResource(R.drawable.ic_close_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { exitSearchMode() }
        }

        // Counterweight mirroring the search icon (40dp + 8dp margin) so the apps
        // strip is centered on the pill's true centre.
        rightSpacer = View(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply { marginStart = dpToPx(8) }
        }

        pill.addView(searchIcon)
        pill.addView(appsScrollView)
        pill.addView(rightSpacer)
        pill.addView(searchEditText)
        pill.addView(micIcon)
        pill.addView(searchCloseIcon)
    }

    private fun applyTransparency() {
        val transparency = SettingsManager.getDockTransparency(context)
        val alpha = (transparency * 2.55).toInt().coerceIn(0, 255)
        val bg = pill.background
        if (bg is GradientDrawable) bg.alpha = alpha else bg?.alpha = alpha
    }

    fun refreshSettings() {
        applyTransparency()
        // Re-apply mic gate so it reflects the current search_with_mic setting.
        if (isSearchMode) {
            val show = SettingsManager.getSearchWithMic(context)
            micIcon.visibility = if (show) View.VISIBLE else View.GONE
            micIcon.alpha = if (show) 1f else 0f
        }
    }

    // ── Slide-to-trigger gesture ────────────────────────────────────────────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Swipe gestures are active only in idle (non-search) mode.
        if (isSearchMode) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                downOnApp = isOverApps(ev.x, ev.y)
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (downOnApp) return false          // let app icons handle reorder
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!swiping && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.3f) {
                    swiping = true
                    dragRawDx = dx
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isSearchMode) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                downOnApp = isOverApps(ev.x, ev.y)
                return !downOnApp
            }
            MotionEvent.ACTION_MOVE -> {
                if (!swiping) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (!downOnApp && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.3f) swiping = true
                }
                if (swiping) {
                    dragRawDx = ev.x - downX
                    pillTx = dragRawDx.coerceIn(-maxTravel, maxTravel)  // 1:1 full drag
                    pill.translationX = pillTx
                    val nowArmed = abs(dragRawDx) >= commitThreshold
                    if (nowArmed && !armed) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    armed = nowArmed
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swiping) {
                    val committed = abs(dragRawDx) >= commitThreshold
                    val toSearch = dragRawDx > 0
                    swiping = false; armed = false
                    settlePill {
                        if (committed) {
                            if (toSearch) enterSearchMode() else onAssistantSwipe?.invoke()
                        }
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    /** Animate the pill (and its revealed ribbon) back to rest, then run [then]. */
    private fun settlePill(then: () -> Unit) {
        val start = pillTx
        val anim = android.animation.ValueAnimator.ofFloat(start, 0f)
        anim.duration = durFast
        anim.interpolator = emphasized
        anim.addUpdateListener {
            pillTx = it.animatedValue as Float
            pill.translationX = pillTx
            invalidate()
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: android.animation.Animator) {
                pillTx = 0f; pill.translationX = 0f; invalidate(); then()
            }
        })
        anim.start()
    }

    /**
     * True only when (x,y) sits over an actual pinned-app icon — NOT the empty
     * space of the (weight-filled) apps strip. Empty space stays swipeable so the
     * left/right slide gestures work even when the dock is wide.
     */
    private fun isOverApps(x: Float, y: Float): Boolean {
        if (dockApps.isEmpty()) return false
        if (y < pill.top || y > pill.bottom) return false
        val px = x - pill.translationX                       // pill-local x
        val baseLeft = appsScrollView.left - appsScrollView.scrollX
        for (i in 0 until appsContainer.childCount) {
            val c = appsContainer.getChildAt(i)
            val left = baseLeft + c.left
            if (px >= left && px <= left + c.width) return true
        }
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        drawRibbon(canvas)
        super.dispatchDraw(canvas)
    }

    private fun drawRibbon(canvas: Canvas) {
        if (pillTx == 0f) return
        val h = height.toFloat()
        val progress = (abs(dragRawDx) / commitThreshold).coerceIn(0f, 1f)
        val a = (150 + 105 * progress).toInt().coerceIn(0, 255)
        val iconSize = dpToPx(22)
        val cy = height / 2f
        val textBaseline = cy - (ribbonTextPaint.descent() + ribbonTextPaint.ascent()) / 2f
        val reveal = abs(pillTx)
        val labelAlpha = (((reveal - (iconSize + dpToPx(30))) / dpToPx(40)).coerceIn(0f, 1f) * 255).toInt()
        // Extend the fill under the pill's rounded cap (radius = h/2) so the
        // curved corners don't leave a gap between ribbon edge and pill edge.
        val capR = h / 2f

        if (pillTx > 0f) {
            // Drag right → Search (teal), revealed strip on the left
            ribbonPaint.color = searchColor; ribbonPaint.alpha = a
            canvas.drawRect(0f, 0f, pillTx + capR, h, ribbonPaint)
            val iconLeft = dpToPx(16)
            ribbonSearchIcon?.let {
                if (reveal > iconSize + dpToPx(20)) {
                    it.setBounds(iconLeft, (cy - iconSize / 2).toInt(), iconLeft + iconSize, (cy + iconSize / 2).toInt())
                    it.alpha = (progress * 255).toInt(); it.draw(canvas)
                }
            }
            if (labelAlpha > 0) {
                ribbonTextPaint.alpha = labelAlpha
                canvas.drawText("Search", (iconLeft + iconSize + dpToPx(10)).toFloat(), textBaseline, ribbonTextPaint)
            }
        } else {
            // Drag left → Assistant (indigo), revealed strip on the right
            ribbonPaint.color = assistColor; ribbonPaint.alpha = a
            canvas.drawRect(width + pillTx - capR, 0f, width.toFloat(), h, ribbonPaint)
            val iconRight = width - dpToPx(16) - iconSize
            ribbonAssistIcon?.let {
                if (reveal > iconSize + dpToPx(20)) {
                    it.setBounds(iconRight, (cy - iconSize / 2).toInt(), iconRight + iconSize, (cy + iconSize / 2).toInt())
                    it.alpha = (progress * 255).toInt(); it.draw(canvas)
                }
            }
            if (labelAlpha > 0) {
                ribbonTextPaint.alpha = labelAlpha
                val label = "Assistant"
                val tw = ribbonTextPaint.measureText(label)
                canvas.drawText(label, iconRight - dpToPx(10) - tw, textBaseline, ribbonTextPaint)
            }
        }
    }

    // ── Search mode (fade-through container transform) ──────────────────────────

    fun enterSearchMode() {
        if (isSearchMode) return
        isSearchMode = true
        onSearchModeChanged?.invoke(true)
        searchIcon.setColorFilter(themeColor(com.google.android.material.R.attr.colorPrimary))

        cancelAnims()
        rightSpacer.visibility = View.GONE

        // Apps fade-through OUT (fade + scale down), no cross-slide.
        appsScrollView.animate()
            .alpha(0f).scaleX(0.92f).scaleY(0.92f)
            .setDuration(durFast).setInterpolator(emphasized)
            .withEndAction { appsScrollView.visibility = View.GONE }
            .start()

        // Field expands IN from the anchor (fade + scale up).
        searchEditText.visibility = View.VISIBLE
        searchEditText.alpha = 0f
        searchEditText.scaleX = 0.94f; searchEditText.scaleY = 0.94f
        searchEditText.pivotX = 0f
        searchEditText.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(70).setDuration(durMorph).setInterpolator(emphasized)
            .start()

        // Mic (gated) + close fade/scale in.
        val showMic = SettingsManager.getSearchWithMic(context)
        if (showMic) fadeScaleIn(micIcon, 90) else { micIcon.visibility = View.GONE; micIcon.alpha = 0f }
        fadeScaleIn(searchCloseIcon, 120)

        searchEditText.postDelayed({ searchEditText.requestFocus(); showKeyboard() }, durMorph)
    }

    fun exitSearchMode() {
        if (!isSearchMode) return
        isSearchMode = false
        onSearchModeChanged?.invoke(false)
        searchIcon.clearColorFilter()

        searchEditText.setText("")
        onSearchTextChanged?.invoke("")
        hideKeyboard()
        cancelAnims()

        searchEditText.animate()
            .alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setDuration(durFast).setInterpolator(emphasized)
            .withEndAction { searchEditText.visibility = View.GONE }
            .start()
        fadeScaleOut(micIcon)
        fadeScaleOut(searchCloseIcon)

        rightSpacer.visibility = View.VISIBLE
        appsScrollView.visibility = View.VISIBLE
        appsScrollView.alpha = 0f
        appsScrollView.scaleX = 0.92f; appsScrollView.scaleY = 0.92f
        appsScrollView.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(60).setDuration(durMorph).setInterpolator(emphasized)
            .start()
    }

    private fun fadeScaleIn(v: View, delay: Long) {
        v.visibility = View.VISIBLE
        v.alpha = 0f; v.scaleX = 0.8f; v.scaleY = 0.8f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(durFast).setInterpolator(emphasized).start()
    }

    private fun fadeScaleOut(v: View) {
        v.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
            .setDuration(durFast).setInterpolator(emphasized)
            .withEndAction { v.visibility = View.GONE }.start()
    }

    private fun cancelAnims() {
        searchEditText.animate().cancel()
        micIcon.animate().cancel()
        searchCloseIcon.animate().cancel()
        appsScrollView.animate().cancel()
    }

    fun isInSearchMode(): Boolean = isSearchMode

    /** Set the field text (used by voice input); keeps focus for continued typing. */
    fun setSearchText(text: String) {
        searchEditText.setText(text)
        searchEditText.setSelection(text.length)
    }

    fun getSearchText(): String = searchEditText.text?.toString() ?: ""

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun themeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // ── Drop-to-pin drag listener ────────────────────────────────────────────────
    private fun setupDragListener() {
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { animate().scaleX(1.02f).scaleY(1.02f).duration = 100; true }
                DragEvent.ACTION_DRAG_EXITED -> { animate().scaleX(1f).scaleY(1f).duration = 100; true }
                DragEvent.ACTION_DROP -> {
                    animate().scaleX(1f).scaleY(1f).duration = 100
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        clipData.getItemAt(0).text?.toString()?.let { addAppByPackage(it) }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { animate().scaleX(1f).scaleY(1f).duration = 100; true }
                else -> false
            }
        }
    }

    fun addAppByPackage(packageName: String): Boolean {
        if (dockApps.any { it.packageName == packageName }) return false
        val app = allAppsRef.find { it.packageName == packageName } ?: return false
        dockApps.add(app)
        saveDockApps()
        rebuildDock()
        return true
    }

    fun setDockApps(apps: List<AppInfo>) {
        dockApps.clear()
        dockApps.addAll(apps)
        saveDockApps()
        rebuildDock()
    }

    fun addApp(app: AppInfo): Boolean {
        if (dockApps.any { it.packageName == app.packageName }) return false
        dockApps.add(app)
        saveDockApps()
        rebuildDock()
        return true
    }

    fun removeApp(app: AppInfo) {
        dockApps.removeAll { it.packageName == app.packageName }
        saveDockApps()
        rebuildDock()
    }

    private fun saveDockApps() {
        SettingsManager.setDockApps(context, dockApps.map { it.packageName }.toSet())
    }

    fun loadDockApps(allApps: List<AppInfo>) {
        allAppsRef = allApps
        val savedPackages = SettingsManager.getDockApps(context)
        dockApps.clear()
        savedPackages.forEach { pkg -> allApps.find { it.packageName == pkg }?.let { dockApps.add(it) } }
        rebuildDock()
    }

    private fun rebuildDock() {
        appsContainer.removeAllViews()
        dockApps.forEachIndexed { index, app -> appsContainer.addView(createAppSlot(app, index)) }
    }

    private fun createAppSlot(app: AppInfo, index: Int): View {
        return ImageView(context).apply {
            val size = dpToPx(48)
            layoutParams = LayoutParams(size, size).apply {
                marginStart = dpToPx(6); marginEnd = dpToPx(6)
            }
            tag = index
            try {
                val iconDrawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
                setImageDrawable(iconDrawable)
            } catch (e: Exception) {
                setImageDrawable(app.icon)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER

            var startX = 0f
            var startY = 0f
            var isLongPress = false
            val longPressHandler = Handler(Looper.getMainLooper())
            val longPressRunnable = Runnable {
                isLongPress = true
                showDockAppMenu(this, app, index)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; isLongPress = false
                        longPressHandler.postDelayed(longPressRunnable, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (distance > dpToPx(10) && !isLongPress) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            startDragReorder(v, app, index)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (!isLongPress) {
                            val dx = event.rawX - startX
                            val dy = event.rawY - startY
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (distance < dpToPx(10)) onAppClick?.invoke(app)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun showDockAppMenu(anchor: View, app: AppInfo, index: Int) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Remove from Dock")
        popup.menu.add(0, 2, 1, "App Info")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { dockApps.removeAt(index); saveDockApps(); rebuildDock(); true }
                2 -> {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent); true
                }
                else -> false
            }
        }
        popup.show()
    }

    // Drag reorder state
    private var draggedIndex = -1
    private var draggedApp: AppInfo? = null
    private var isOutsideDock = false

    private fun startDragReorder(view: View, app: AppInfo, index: Int) {
        draggedIndex = index; draggedApp = app; isOutsideDock = false
        val shadow = View.DragShadowBuilder(view)
        val clipData = android.content.ClipData.newPlainText("dock_reorder", app.packageName)
        view.startDragAndDrop(clipData, shadow, index, 0)
        view.alpha = 0.3f
        setupReorderDragListener()
    }

    private fun setupReorderDragListener() {
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { isOutsideDock = false; true }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    isOutsideDock = false
                    for (i in 0 until appsContainer.childCount) {
                        if (i == draggedIndex) {
                            appsContainer.getChildAt(i).alpha = 0.3f
                            appsContainer.getChildAt(i).scaleX = 1f
                            appsContainer.getChildAt(i).scaleY = 1f
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    isOutsideDock = true
                    for (i in 0 until appsContainer.childCount) {
                        if (i == draggedIndex) {
                            appsContainer.getChildAt(i).alpha = 0.1f
                            appsContainer.getChildAt(i).scaleX = 0.5f
                            appsContainer.getChildAt(i).scaleY = 0.5f
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DROP -> true
                DragEvent.ACTION_DRAG_ENDED -> {
                    if (isOutsideDock && draggedApp != null && draggedIndex in 0 until dockApps.size) {
                        dockApps.removeAt(draggedIndex); saveDockApps()
                    }
                    isOutsideDock = false; draggedIndex = -1; draggedApp = null
                    setOnDragListener(null); appsContainer.setOnDragListener(null)
                    setupDragListener()   // restore drop-to-pin listener
                    rebuildDock()
                    true
                }
                else -> false
            }
        }

        appsContainer.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_LOCATION -> {
                    val dropIndex = findDropIndex(event.x)
                    if (dropIndex != draggedIndex && dropIndex >= 0 && draggedIndex >= 0) {
                        val app = draggedApp ?: return@setOnDragListener true
                        dockApps.removeAt(draggedIndex)
                        dockApps.add(dropIndex, app)
                        draggedIndex = dropIndex
                        rebuildDockAnimated()
                    }
                    true
                }
                DragEvent.ACTION_DROP -> { saveDockApps(); true }
                else -> true
            }
        }
    }

    private fun findDropIndex(x: Float): Int {
        var accumulatedWidth = 0f
        for (i in 0 until appsContainer.childCount) {
            val child = appsContainer.getChildAt(i)
            val childWidth = child.width + dpToPx(12)
            if (x < accumulatedWidth + childWidth / 2) return i
            accumulatedWidth += childWidth
        }
        return dockApps.size - 1
    }

    private fun rebuildDockAnimated() {
        appsContainer.removeAllViews()
        dockApps.forEachIndexed { index, app ->
            val view = createAppSlot(app, index)
            if (index == draggedIndex) view.alpha = 0.3f
            appsContainer.addView(view)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
