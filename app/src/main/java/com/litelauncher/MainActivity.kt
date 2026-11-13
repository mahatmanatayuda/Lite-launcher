package com.litelauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconManager
    private lateinit var model: HomeModel
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var pageAdapter: PageAdapter
    private lateinit var snap: PagerSnapHelper
    private lateinit var awm: AppWidgetManager
    private lateinit var host: LauncherWidgetHost

    private lateinit var root: View
    private lateinit var home: View
    private lateinit var clockBox: View
    private lateinit var pager: RecyclerView
    private lateinit var dots: LinearLayout
    private lateinit var hotseat: CellLayout
    private lateinit var actionBar: View
    private lateinit var actRemove: TextView
    private lateinit var actInfo: TextView
    private lateinit var actUninstall: TextView
    private lateinit var folderPanel: View
    private lateinit var folderTitle: EditText
    private lateinit var folderGrid: GridLayout
    private lateinit var drawer: View
    private lateinit var grid: RecyclerView
    private lateinit var search: EditText
    private lateinit var groupScroll: HorizontalScrollView
    private lateinit var groupBar: LinearLayout

    private var allApps: List<AppInfo> = emptyList()
    private var customLabels: Map<String, String> = emptyMap()
    private var groups: Map<String, List<String>> = emptyMap()
    private var currentGroup: String? = null
    private var pendingIcon: ComponentName? = null
    private var pendingWidgetId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null
    private var drawerOpen = false
    private var dragging = false
    private var lastLoop = false
    private var lastPages = -1
    private var openFolderId: Long? = null
    private var pagesBeforeDrag = 1
    private var edgeDir = 0
    private val worker = Executors.newSingleThreadExecutor()

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // During an app update Android sends REMOVED(replacing) then ADDED: ignore the first.
            if (i.action == Intent.ACTION_PACKAGE_REMOVED && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            reloadApps()
        }
    }

    private val edgeRun = object : Runnable {
        override fun run() {
            if (edgeDir == 0) return
            val target = rawCurrent() + edgeDir
            if (!looping()) {
                if (target < 0) return
                if (target >= model.pages) {
                    if (model.pages >= MAX_PAGES) return
                    model.pages++
                    pageAdapter.notifyItemInserted(model.pages - 1)
                    updateDots()
                }
            }
            pager.smoothScrollToPosition(target)
            pager.postDelayed(this, 800)
        }
    }

    /** Redraw icons when notifications change (skipped while dragging). */
    private val badgeRun = Runnable {
        if (!dragging) {
            refreshHome()
            drawerAdapter.notifyDataSetChanged()
        }
    }

    // ---------- Lifecycle ----------

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        prefs = Prefs(this)
        icons = IconManager(this)
        model = HomeModel.load(this)
        awm = AppWidgetManager.getInstance(this)
        host = LauncherWidgetHost(applicationContext, HOST_ID)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        clockBox = findViewById(R.id.clock_box)
        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)
        hotseat = findViewById(R.id.hotseat)
        actionBar = findViewById(R.id.action_bar)
        actRemove = findViewById(R.id.act_remove)
        actInfo = findViewById(R.id.act_info)
        actUninstall = findViewById(R.id.act_uninstall)
        folderPanel = findViewById(R.id.folder_panel)
        folderTitle = findViewById(R.id.folder_title)
        folderGrid = findViewById(R.id.folder_grid)
        drawer = findViewById(R.id.drawer)
        grid = findViewById(R.id.grid)
        search = findViewById(R.id.search)

        root.setOnApplyWindowInsetsListener { _, insets ->
            val l = insets.systemWindowInsetLeft
            val t = insets.systemWindowInsetTop
            val r = insets.systemWindowInsetRight
            val b = insets.systemWindowInsetBottom
            home.setPadding(l, t, r, b)
            drawer.setPadding(l, t, r, b)
            actionBar.setPadding(l, t, r, 0)
            folderPanel.setPadding(l, t, r, b)
            insets
        }

        // Home pager
        pager.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        snap = PagerSnapHelper()
        snap.attachToRecyclerView(pager)
        pageAdapter = PageAdapter()
        pager.adapter = pageAdapter
        pager.itemAnimator = null
        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) updateDots()
            }
        })
        setupHomeGestures()

        // Drawer
        drawerAdapter = AppAdapter(icons, ::launch, ::showDrawerMenu)
        grid.layoutManager = GridLayoutManager(this, prefs.drawerCols)
        grid.adapter = drawerAdapter
        grid.itemAnimator = null
        setupDrawer()
        setupGroupBar()

        // Folder panel
        folderPanel.setOnClickListener { closeFolder() }
        findViewById<View>(R.id.folder_card).setOnClickListener { }

        root.setOnDragListener { _, e -> onDrag(e) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, filter)
        reloadApps()
    }

    override fun onStart() {
        super.onStart()
        try { host.startListening() } catch (e: Exception) { /* widgets update later */ }
    }

    override fun onStop() {
        try { host.stopListening() } catch (e: Exception) { /* ignore */ }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        icons.reloadConfig()
        customLabels = prefs.customLabels()
        groups = prefs.groups()
        model = HomeModel.load(this)
        applyGridPrefs()
        if (model.normalize()) model.save(this)
        clockBox.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        hotseat.layoutParams.height = dp(prefs.iconSizeDp) + dp(24)
        hotseat.requestLayout()
        (grid.layoutManager as GridLayoutManager).spanCount = prefs.drawerCols
        drawerAdapter.iconSizePx = dp(prefs.iconSizeDp)
        drawerAdapter.showLabels = prefs.showLabels
        drawerAdapter.showBadges = prefs.notifBadges
        NotifState.listener = {
            pager.removeCallbacks(badgeRun)
            pager.postDelayed(badgeRun, 300)
        }
        rebuildGroupBar()
        applyFilter()
        refreshHome()
    }

    override fun onPause() {
        NotifState.listener = null
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        worker.shutdown()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) icons.clear()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            closeFolder()
            closeDrawer()
            scrollToPage(0, true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            openFolderId != null -> closeFolder()
            drawerOpen -> closeDrawer()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_IMAGE -> {
                val uri = data?.data
                val target = pendingIcon
                pendingIcon = null
                if (resultCode == RESULT_OK && uri != null && target != null) saveCustomIcon(target, uri)
            }
            REQ_WIDGET_BIND, REQ_WIDGET_CONFIG -> {
                val id = pendingWidgetId
                val info = pendingProvider
                if (id < 0 || info == null) return
                if (resultCode != RESULT_OK) {
                    pendingWidgetId = -1
                    pendingProvider = null
                    deleteWidgetId(id)
                } else if (requestCode == REQ_WIDGET_BIND) {
                    finishWidget(id, info)
                } else {
                    pendingWidgetId = -1
                    pendingProvider = null
                    placeWidgetOrFail(id, info)
                }
            }
        }
    }

    private fun applyGridPrefs() {
        model.cols = prefs.cols
        model.rows = prefs.rows
        model.hotseat = prefs.hotseat
    }

    /** Name shown for an app: the user's custom name if there is one. */
    private fun labelOf(flat: String?, fallback: String): String =
        flat?.let { customLabels[it] } ?: fallback

    // ---------- Home pager (with optional page loop) ----------

    private fun looping() = prefs.loopPages && model.pages > 1

    /** Which home page an adapter position shows (positions repeat when the loop is on). */
    private fun pageOf(position: Int): Int = if (model.pages > 0) position % model.pages else 0

    private fun rawCurrent(): Int {
        val v = snap.findSnapView(pager.layoutManager!!) ?: return 0
        return pager.getChildAdapterPosition(v).coerceAtLeast(0)
    }

    private fun currentPage(): Int = pageOf(rawCurrent())

    private fun startPosition(page: Int): Int {
        if (!looping()) return page
        val mid = LOOP_COUNT / 2
        return mid - mid % model.pages + page
    }

    private fun scrollToPage(page: Int, smooth: Boolean) {
        val raw = rawCurrent()
        val target = if (looping()) raw - pageOf(raw) + page else page
        if (smooth) pager.smoothScrollToPosition(target) else pager.scrollToPosition(target)
    }

    inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val cell: CellLayout) : RecyclerView.ViewHolder(cell)

        override fun getItemCount() = if (looping()) LOOP_COUNT else model.pages

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val c = CellLayout(parent.context)
            c.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            c.isLongClickable = true
            c.setOnLongClickListener { showHomeMenu(); true }
            return VH(c)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            fillCells(holder.cell, HomeItem.DESKTOP, pageOf(position), model.cols, model.rows, prefs.showLabels)
        }
    }

    private fun refreshHome() {
        val keep = currentPage()
        pageAdapter.notifyDataSetChanged()
        val loop = looping()
        if (loop != lastLoop || model.pages != lastPages) {
            lastLoop = loop
            lastPages = model.pages
            pager.scrollToPosition(startPosition(keep.coerceIn(0, (model.pages - 1).coerceAtLeast(0))))
        }
        fillCells(hotseat, HomeItem.HOTSEAT, 0, model.hotseat, 1, false)
        updateDots()
    }

    private fun fillCells(cell: CellLayout, container: Long, screen: Int, cols: Int, rows: Int, labels: Boolean) {
        cell.removeAllViews()
        cell.cols = cols
        cell.rows = rows
        for (item in model.items) {
            if (item.container != container) continue
            if (container == HomeItem.DESKTOP && item.screen != screen) continue
            if (item.x >= cols || item.y >= rows) continue
            if (item.type == HomeItem.TYPE_WIDGET) {
                val w = makeWidgetView(item) ?: continue
                cell.addView(w, CellLayout.LP(item.x, item.y, item.spanX, item.spanY))
            } else {
                cell.addView(makeItemView(item, labels), CellLayout.LP(item.x, item.y))
            }
        }
    }

    private fun comp(flat: String?): ComponentName? = flat?.let { ComponentName.unflattenFromString(it) }

    private fun makeItemView(item: HomeItem, labels: Boolean): View {
        val v = layoutInflater.inflate(R.layout.item_app, null, false)
        val size = dp(prefs.iconSizeDp)
        val icon = v.findViewById<ImageView>(R.id.icon)
        val folderIcon = v.findViewById<FolderIconView>(R.id.folder_icon)
        val label = v.findViewById<TextView>(R.id.label)
        val badge = v.findViewById<View>(R.id.badge)
        label.text = if (item.type == HomeItem.TYPE_APP) labelOf(item.component, item.title) else item.title
        label.visibility = if (labels) View.VISIBLE else View.GONE
        val hasDot = if (item.type == HomeItem.TYPE_APP) {
            NotifState.has(comp(item.component)?.packageName)
        } else {
            model.children(item.id).any { NotifState.has(comp(it.component)?.packageName) }
        }
        badge.visibility = if (prefs.notifBadges && hasDot) View.VISIBLE else View.GONE
        if (item.type == HomeItem.TYPE_APP) {
            folderIcon.visibility = View.GONE
            icon.visibility = View.VISIBLE
            comp(item.component)?.let { bindIcon(icon, it, size, icons) }
        } else {
            icon.visibility = View.GONE
            folderIcon.visibility = View.VISIBLE
            val lp = folderIcon.layoutParams
            lp.width = size; lp.height = size
            folderIcon.layoutParams = lp
            folderIcon.clearIcons()
            val mini = (size * 0.4f).toInt().coerceAtLeast(16)
            model.children(item.id).take(4).forEachIndexed { i, child ->
                comp(child.component)?.let { c -> icons.load(c, mini) { bmp -> folderIcon.setIcon(i, bmp) } }
            }
        }
        v.setOnClickListener {
            if (item.type == HomeItem.TYPE_APP) launchComponent(item.component) else openFolder(item)
        }
        v.setOnLongClickListener { dragOrLocked(v, item); true }
        if (item.type == HomeItem.TYPE_APP && prefs.iconSwipe != "none") attachIconSwipe(v, item)
        return v
    }

    /** Swiping up on an app icon opens its menu or its info, depending on the setting. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachIconSwipe(v: View, item: HomeItem) {
        var fired = false
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                if (e1.y - e2.y > dp(32) && abs(vy) > abs(vx) && vy < -500) {
                    fired = true
                    if (prefs.iconSwipe == "info") {
                        comp(item.component)?.let { openAppInfo(it.packageName) }
                    } else {
                        showItemDialog(item)
                    }
                    return true
                }
                return false
            }
        })
        v.setOnTouchListener { _, ev ->
            fired = false
            detector.onTouchEvent(ev)
            fired
        }
    }

    /** True when a touch that began at (x, y) in the pager was on an app icon. */
    private fun startedOnAppIcon(x: Float, y: Float): Boolean {
        val page = pager.findChildViewUnder(x, y) as? CellLayout ?: return false
        val cell = page.cellAt(x - page.left, y - page.top)
        val item = model.at(HomeItem.DESKTOP, pageOf(pager.getChildAdapterPosition(page)), cell.first, cell.second)
        return item != null && item.type == HomeItem.TYPE_APP
    }

    private fun updateDots() {
        dots.removeAllViews()
        if (model.pages <= 1) return
        val cur = currentPage()
        val d = dp(6)
        val m = dp(4)
        repeat(model.pages) { i ->
            val dot = View(this)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (i == cur) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
            }
            dots.addView(dot, LinearLayout.LayoutParams(d, d).apply { setMargins(m, 0, m, 0) })
        }
    }

    // ---------- Home gestures & menu ----------

    /** Run a gesture action chosen in Settings. Returns false when the action is "none". */
    private fun runGesture(action: String): Boolean {
        when (action) {
            "drawer" -> openDrawer()
            "notifications" -> expandNotifications()
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return false
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHomeGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onDoubleTap(e: MotionEvent): Boolean = runGesture(prefs.doubleTap)

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) > abs(dx) && abs(vy) > 600) {
                    // A swipe up that starts on an icon belongs to the icon, not to the home screen.
                    if (dy < 0 && prefs.iconSwipe != "none" && startedOnAppIcon(e1.x, e1.y)) return false
                    return runGesture(if (dy < 0) prefs.swipeUp else prefs.swipeDown)
                }
                return false
            }
        })
        // Observe touches without ever stealing them from the pager or the icons.
        pager.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) { detector.onTouchEvent(e) }
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun showHomeMenu() {
        val entries: List<Pair<String, () -> Unit>> = listOf(
            getString(R.string.wallpaper) to { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper))) },
            getString(R.string.widgets) to { showWidgetPicker() },
            getString(R.string.add_page) to {
                if (model.pages < MAX_PAGES) {
                    model.pages++
                    model.save(this)
                    refreshHome()
                    scrollToPage(model.pages - 1, false)
                    pager.post { updateDots() }
                }
            },
            getString(R.string.remove_page) to {
                if (model.removePage(currentPage())) {
                    model.save(this)
                    refreshHome()
                } else {
                    Toast.makeText(this, R.string.page_not_removable, Toast.LENGTH_SHORT).show()
                }
            },
            getString(R.string.settings) to { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
        AlertDialog.Builder(this)
            .setItems(entries.map { it.first }.toTypedArray()) { _, i -> entries[i].second() }
            .show()
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {
            // Not available on this ROM.
        }
    }

    // ---------- Widgets ----------

    private fun makeWidgetView(item: HomeItem): View? {
        return try {
            val info = awm.getAppWidgetInfo(item.widgetId) ?: return null
            val hv = host.createView(this, item.widgetId, info)
            if (hv is LauncherWidgetView) hv.onLongPress = { dragOrLocked(hv, item) }
            hv
        } catch (e: Exception) {
            null
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun showWidgetPicker() {
        val pm = packageManager
        val providers = awm.installedProviders
            .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .sortedBy { it.loadLabel(pm).lowercase() }
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.widget_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val names = providers.map { it.loadLabel(pm) + " – " + appLabel(it.provider.packageName) }
        AlertDialog.Builder(this)
            .setTitle(R.string.widgets)
            .setItems(names.toTypedArray()) { _, i -> addWidget(providers[i]) }
            .show()
    }

    private fun addWidget(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            pendingWidgetId = id
            pendingProvider = info
            finishWidget(id, info)
            return
        }
        // Not allowed silently: ask the user, as the system requires for non-system launchers.
        pendingWidgetId = id
        pendingProvider = info
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        try {
            startActivityForResult(i, REQ_WIDGET_BIND)
        } catch (e: Exception) {
            pendingWidgetId = -1
            pendingProvider = null
            deleteWidgetId(id)
            Toast.makeText(this, R.string.widget_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** The widget is bound: run its configuration screen if it has one, then place it. */
    private fun finishWidget(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_WIDGET_CONFIG, null)
                return
            } catch (e: Exception) {
                // Configuration screen unavailable: place the widget as it is.
            }
        }
        pendingWidgetId = -1
        pendingProvider = null
        placeWidgetOrFail(id, info)
    }

    private fun placeWidgetOrFail(id: Int, info: AppWidgetProviderInfo) {
        if (!placeWidget(id, info)) {
            deleteWidgetId(id)
            Toast.makeText(this, R.string.cannot_drop, Toast.LENGTH_SHORT).show()
        }
    }

    /** Default size: the smallest block of cells that covers the widget's minimum size. */
    private fun spansFor(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val cw = (pager.width / model.cols).coerceAtLeast(1)
        val ch = (pager.height / model.rows).coerceAtLeast(1)
        val sx = ((info.minWidth + cw - 1) / cw).coerceIn(1, model.cols)
        val sy = ((info.minHeight + ch - 1) / ch).coerceIn(1, model.rows)
        return sx to sy
    }

    private fun placeWidget(id: Int, info: AppWidgetProviderInfo): Boolean {
        val (sx, sy) = spansFor(info)
        val current = currentPage()
        val order = listOf(current) + (0 until model.pages).filter { it != current }
        for (s in order) {
            val pos = model.findSpace(s, sx, sy) ?: continue
            addWidgetItem(id, info, s, pos.first, pos.second, sx, sy)
            return true
        }
        if (model.pages < MAX_PAGES) {
            val s = model.pages
            model.pages++
            val pos = model.findSpace(s, sx, sy)
            if (pos != null) {
                addWidgetItem(id, info, s, pos.first, pos.second, sx, sy)
                return true
            }
            model.pages--
        }
        return false
    }

    private fun addWidgetItem(id: Int, info: AppWidgetProviderInfo, screen: Int, x: Int, y: Int, sx: Int, sy: Int) {
        model.items.add(
            HomeItem(
                model.nextId(), HomeItem.TYPE_WIDGET, HomeItem.DESKTOP, screen, x, y,
                info.loadLabel(packageManager), info.provider.flattenToString(), sx, sy, id,
            )
        )
        model.save(this)
        refreshHome()
        scrollToPage(screen, false)
    }

    private fun deleteWidgetId(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (e: Exception) { /* already gone */ }
    }

    /** Remove an item from the home screen, releasing its widget id if it is a widget. */
    private fun removeItem(item: HomeItem) {
        if (item.type == HomeItem.TYPE_WIDGET && item.widgetId >= 0) deleteWidgetId(item.widgetId)
        model.remove(item)
    }

    private fun showResizeDialog(item: HomeItem) {
        val options = listOf(
            R.string.widget_wider to (1 to 0),
            R.string.widget_narrower to (-1 to 0),
            R.string.widget_taller to (0 to 1),
            R.string.widget_shorter to (0 to -1),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.resize_widget)
            .setItems(options.map { getString(it.first) }.toTypedArray()) { _, i ->
                val d = options[i].second
                val nx = item.spanX + d.first
                val ny = item.spanY + d.second
                if (nx >= 1 && ny >= 1 && model.resize(item, nx, ny)) {
                    model.save(this)
                    refreshHome()
                } else {
                    Toast.makeText(this, R.string.widget_cannot_resize, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ---------- Drag and drop ----------

    private fun dragOrLocked(v: View, item: HomeItem) {
        if (prefs.lockDesktop) {
            Toast.makeText(this, R.string.desktop_locked, Toast.LENGTH_SHORT).show()
        } else {
            startItemDrag(v, item)
        }
    }

    private fun startItemDrag(v: View, item: HomeItem) {
        v.startDragAndDrop(ClipData.newPlainText("item", item.id.toString()), View.DragShadowBuilder(v), item.id, 0)
    }

    private fun rectIn(v: View): Rect {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationOnScreen(a)
        root.getLocationOnScreen(b)
        return Rect(a[0] - b[0], a[1] - b[1], a[0] - b[0] + v.width, a[1] - b[1] + v.height)
    }

    private fun onDrag(e: DragEvent): Boolean {
        val itemId = e.localState as? Long ?: return false
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                dragging = true
                pagesBeforeDrag = model.pages
                closeFolder(refresh = false)
                actionBar.visibility = View.VISIBLE
                return true
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                val dir = when {
                    e.x < dp(40) -> -1
                    e.x > root.width - dp(40) -> 1
                    else -> 0
                }
                if (dir != edgeDir) {
                    pager.removeCallbacks(edgeRun)
                    edgeDir = dir
                    if (dir != 0) pager.postDelayed(edgeRun, 450)
                }
                highlightZone(e.x.toInt(), e.y.toInt())
                return true
            }
            DragEvent.ACTION_DROP -> return handleDrop(itemId, e.x.toInt(), e.y.toInt())
            DragEvent.ACTION_DRAG_ENDED -> {
                dragging = false
                pager.removeCallbacks(edgeRun)
                edgeDir = 0
                actionBar.visibility = View.GONE
                clearZoneHighlight()
                while (model.pages > pagesBeforeDrag && model.pages > 1 &&
                    model.items.none { it.container == HomeItem.DESKTOP && it.screen == model.pages - 1 }
                ) model.pages--
                model.save(this)
                refreshHome()
                return true
            }
        }
        return true
    }

    private fun highlightZone(x: Int, y: Int) {
        clearZoneHighlight()
        if (y > rectIn(actionBar).bottom) return
        for (z in listOf(actRemove, actInfo, actUninstall)) {
            if (rectIn(z).contains(x, y)) z.setBackgroundColor(0x55FFFFFF)
        }
    }

    private fun clearZoneHighlight() {
        for (z in listOf(actRemove, actInfo, actUninstall)) z.setBackgroundColor(0)
    }

    private fun handleDrop(itemId: Long, x: Int, y: Int): Boolean {
        val item = model.find(itemId) ?: return false

        if (actionBar.visibility == View.VISIBLE && y <= rectIn(actionBar).bottom) {
            when {
                rectIn(actRemove).contains(x, y) -> removeItem(item)
                rectIn(actInfo).contains(x, y) -> comp(item.component)?.let { openAppInfo(it.packageName) }
                rectIn(actUninstall).contains(x, y) -> comp(item.component)?.let { uninstall(it.packageName) }
            }
            model.save(this)
            return true
        }

        val hr = rectIn(hotseat)
        if (hr.contains(x, y)) {
            val cell = hotseat.cellAt((x - hr.left).toFloat(), (y - hr.top).toFloat())
            return dropOn(item, HomeItem.HOTSEAT, 0, cell.first, 0)
        }

        val page = snap.findSnapView(pager.layoutManager!!) as? CellLayout ?: return false
        val pr = rectIn(page)
        if (!pr.contains(x, y)) return false
        val cell = page.cellAt((x - pr.left).toFloat(), (y - pr.top).toFloat())
        return dropOn(item, HomeItem.DESKTOP, pageOf(pager.getChildAdapterPosition(page)), cell.first, cell.second)
    }

    private fun dropOn(item: HomeItem, container: Long, screen: Int, x: Int, y: Int): Boolean {
        val target = model.at(container, screen, x, y)
        if (target === item) {
            // Dropped back where it was: treat as a tap-and-hold menu.
            showItemDialog(item)
            return true
        }
        val ok = model.dropAt(item, container, screen, x, y)
        if (!ok) Toast.makeText(this, R.string.cannot_drop, Toast.LENGTH_SHORT).show()
        model.save(this)
        return ok
    }

    private fun showItemDialog(item: HomeItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        when (item.type) {
            HomeItem.TYPE_APP -> {
                labels += getString(R.string.edit_icon)
                actions += { comp(item.component)?.let { editIcon(it) } }
            }
            HomeItem.TYPE_FOLDER -> {
                labels += getString(R.string.rename)
                actions += { openFolder(item) }
            }
            else -> {
                labels += getString(R.string.resize_widget)
                actions += { showResizeDialog(item) }
            }
        }
        labels += getString(R.string.remove)
        actions += { removeItem(item); model.save(this); refreshHome() }
        if (item.type == HomeItem.TYPE_APP) {
            labels += getString(R.string.app_info)
            actions += { comp(item.component)?.let { openAppInfo(it.packageName) } }
            labels += getString(R.string.uninstall)
            actions += { comp(item.component)?.let { uninstall(it.packageName) } }
        }
        AlertDialog.Builder(this)
            .setTitle(if (item.type == HomeItem.TYPE_APP) labelOf(item.component, item.title) else item.title)
            .setItems(labels.toTypedArray()) { _, i -> actions[i]() }
            .show()
    }

    // ---------- Folders ----------

    private fun openFolder(folder: HomeItem) {
        openFolderId = folder.id
        folderTitle.setText(folder.title)
        folderGrid.removeAllViews()
        val size = dp(prefs.iconSizeDp)
        for (child in model.children(folder.id)) {
            val v = layoutInflater.inflate(R.layout.item_app, null, false)
            v.findViewById<View>(R.id.folder_icon).visibility = View.GONE
            val icon = v.findViewById<ImageView>(R.id.icon)
            comp(child.component)?.let { bindIcon(icon, it, size, icons) }
            val label = v.findViewById<TextView>(R.id.label)
            label.text = labelOf(child.component, child.title)
            val badge = v.findViewById<View>(R.id.badge)
            badge.visibility =
                if (prefs.notifBadges && NotifState.has(comp(child.component)?.packageName)) View.VISIBLE else View.GONE
            v.setOnClickListener { launchComponent(child.component) }
            v.setOnLongClickListener { dragOrLocked(v, child); true }
            val lp = GridLayout.LayoutParams()
            lp.width = dp(80)
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            folderGrid.addView(v, lp)
        }
        folderPanel.visibility = View.VISIBLE
    }

    private fun closeFolder(refresh: Boolean = true) {
        val id = openFolderId ?: return
        openFolderId = null
        hideKeyboard()
        val folder = model.find(id)
        val newTitle = folderTitle.text.toString().trim()
        if (folder != null && newTitle.isNotEmpty() && newTitle != folder.title) {
            folder.title = newTitle
            model.save(this)
        }
        folderPanel.visibility = View.GONE
        if (refresh) refreshHome()
    }

    // ---------- Dock defaults ----------

    private fun defaultDock(): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull {
            packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.filter { it != "android" }.distinct()
    }

    private fun seedDefaults() {
        var x = 0
        for (pkg in defaultDock()) {
            if (x >= model.hotseat) break
            val app = allApps.firstOrNull { it.packageName == pkg } ?: continue
            model.items.add(
                HomeItem(model.nextId(), HomeItem.TYPE_APP, HomeItem.HOTSEAT, 0, x++, 0, app.label, app.component.flattenToString())
            )
        }
    }

    // ---------- Drawer ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrawer() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                drawerAdapter.first()?.let(::launch)
                true
            } else false
        }
        val handleDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { closeDrawer(); return true }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy > 500) { closeDrawer(); return true }
                return false
            }
        })
        findViewById<View>(R.id.handle).setOnTouchListener { _, ev -> handleDetector.onTouchEvent(ev); true }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawer.visibility = View.VISIBLE
        drawer.translationY = root.height.toFloat()
        drawer.animate().translationY(0f).setDuration(250).setInterpolator(DecelerateInterpolator()).start()
        home.animate().alpha(0f).setDuration(200).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        drawer.animate().translationY(root.height.toFloat()).setDuration(200).withEndAction {
            if (!drawerOpen) {
                drawer.visibility = View.GONE
                search.setText("")
                grid.scrollToPosition(0)
            }
        }.start()
        home.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun showDrawerMenu(anchor: View, app: AppInfo) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.add_to_home)
        menu.menu.add(0, 2, 1, R.string.edit_icon)
        menu.menu.add(0, 6, 2, R.string.add_to_group)
        if (currentGroup != null) menu.menu.add(0, 7, 3, R.string.remove_from_group)
        menu.menu.add(0, 3, 4, R.string.hide)
        menu.menu.add(0, 4, 5, R.string.app_info)
        menu.menu.add(0, 5, 6, R.string.uninstall)
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> {
                    if (model.addApp(app.component.flattenToString(), app.label, currentPage())) {
                        model.save(this)
                        refreshHome()
                        closeDrawer()
                    } else {
                        Toast.makeText(this, R.string.home_full, Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> editIcon(app.component)
                3 -> {
                    prefs.hidden = prefs.hidden + app.packageName
                    applyFilter()
                }
                4 -> openAppInfo(app.packageName)
                5 -> uninstall(app.packageName)
                6 -> addToGroup(app)
                7 -> removeFromGroup(app)
            }
            true
        }
        menu.show()
    }

    // ---------- Drawer groups ----------

    private fun setupGroupBar() {
        groupBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        groupScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(groupBar)
        }
        // Drawer children are: handle, search, grid. Put the group chips between search and grid.
        (drawer as LinearLayout).addView(
            groupScroll, 2,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun rebuildGroupBar() {
        groupBar.removeAllViews()
        if (groups.isEmpty()) {
            groupScroll.visibility = View.GONE
            currentGroup = null
            return
        }
        groupScroll.visibility = View.VISIBLE
        if (currentGroup != null && !groups.containsKey(currentGroup)) currentGroup = null
        addChip(getString(R.string.group_all), null)
        for (name in groups.keys) addChip(name, name)
    }

    private fun addChip(text: String, group: String?) {
        val selected = currentGroup == group
        val chip = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (selected) 0xFF5B6CFF.toInt() else 0x22FFFFFF)
            }
            setOnClickListener {
                currentGroup = group
                rebuildGroupBar()
                applyFilter()
            }
            if (group != null) setOnLongClickListener { confirmDeleteGroup(group); true }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), 0, dp(4), 0)
        groupBar.addView(chip, lp)
    }

    private fun addToGroup(app: AppInfo) {
        val names = groups.keys.toList()
        val items = (names + getString(R.string.group_new)).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.add_to_group)
            .setItems(items) { _, i ->
                if (i < names.size) putInGroup(names[i], app) else promptNewGroup(app)
            }
            .show()
    }

    private fun promptNewGroup(app: AppInfo) {
        val input = EditText(this).apply {
            setSingleLine()
            hint = getString(R.string.group_name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.group_new)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) putInGroup(n, app)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun putInGroup(name: String, app: AppInfo) {
        val m = LinkedHashMap(groups)
        val flat = app.component.flattenToString()
        val current = m[name] ?: emptyList()
        m[name] = if (flat in current) current else current + flat
        saveGroups(m)
    }

    private fun removeFromGroup(app: AppInfo) {
        val g = currentGroup ?: return
        val m = LinkedHashMap(groups)
        m[g] = (m[g] ?: emptyList()) - app.component.flattenToString()
        saveGroups(m)
    }

    private fun confirmDeleteGroup(name: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.group_delete_confirm, name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val m = LinkedHashMap(groups)
                m.remove(name)
                if (currentGroup == name) currentGroup = null
                saveGroups(m)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveGroups(m: Map<String, List<String>>) {
        prefs.setGroups(m)
        groups = prefs.groups()
        rebuildGroupBar()
        applyFilter()
    }

    // ---------- Apps ----------

    private fun reloadApps() {
        worker.execute {
            val apps = AppRepository.load(this)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allApps = apps
                var changed = false
                if (!HomeModel.exists(this)) {
                    seedDefaults()
                    changed = true
                } else if (apps.size > 5) {
                    changed = model.removeMissing(apps.map { it.component.flattenToString() }.toSet())
                    // Widgets whose app was removed (or whose id no longer exists) disappear too.
                    val broken = model.items.filter {
                        it.type == HomeItem.TYPE_WIDGET && awm.getAppWidgetInfo(it.widgetId) == null
                    }
                    if (broken.isNotEmpty()) {
                        broken.forEach { removeItem(it) }
                        changed = true
                    }
                }
                if (changed) model.save(this)
                applyFilter()
                refreshHome()
            }
        }
    }

    private fun applyFilter() {
        val hidden = prefs.hidden
        val q = search.text.toString().trim().lowercase()
        val members = currentGroup?.let { groups[it] }?.toSet()
        val list = allApps
            .map { a -> customLabels[a.component.flattenToString()]?.let { a.copy(label = it) } ?: a }
            .filter { it.packageName !in hidden }
            .filter { members == null || it.component.flattenToString() in members }
            .filter { q.isEmpty() || it.label.lowercase().contains(q) }
            .sortedBy { it.label.lowercase() }
        drawerAdapter.submit(list)
    }

    private fun launch(app: AppInfo) = launchComponent(app.component.flattenToString())

    private fun launchComponent(flat: String?) {
        val c = comp(flat) ?: return
        try {
            startActivity(
                Intent.makeMainActivity(c)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
            closeFolder()
            closeDrawer()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(pkg: String) = startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    )

    private fun uninstall(pkg: String) = startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    // ---------- Icon and name editing ----------

    private fun editIcon(component: ComponentName) {
        val options = arrayOf(
            getString(R.string.choose_from_pack),
            getString(R.string.choose_image),
            getString(R.string.rename),
            getString(R.string.reset_custom),
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> chooseFromPack(component)
                    1 -> pickImage(component)
                    2 -> renameApp(component)
                    3 -> resetCustom(component)
                }
            }
            .show()
    }

    private fun chooseFromPack(component: ComponentName) {
        val packs = icons.listPacks()
        when {
            packs.isEmpty() -> Toast.makeText(this, R.string.no_icon_packs, Toast.LENGTH_LONG).show()
            packs.size == 1 -> openPicker(component, packs[0])
            else -> AlertDialog.Builder(this)
                .setItems(packs.map { it.label }.toTypedArray()) { _, i -> openPicker(component, packs[i]) }
                .show()
        }
    }

    private fun pickImage(component: ComponentName) {
        pendingIcon = component
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        startActivityForResult(i, REQ_IMAGE)
    }

    /** Crop the picked image to a square, shrink it, and store it as this app's icon. */
    private fun saveCustomIcon(component: ComponentName, uri: Uri) {
        worker.execute {
            var ok = false
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (src != null) {
                    val side = minOf(src.width, src.height)
                    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    val scaled = Bitmap.createScaledBitmap(square, 192, 192, true)
                    val dir = File(filesDir, "icons")
                    dir.mkdirs()
                    val flat = component.flattenToString()
                    deleteCustomIconFile(flat)
                    val name = "icon_" + System.currentTimeMillis() + ".png"
                    File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    prefs.setIconOverride(flat, "file|$name")
                    ok = true
                }
            } catch (e: Exception) {
                ok = false
            }
            runOnUiThread {
                if (ok) refreshAllIcons() else Toast.makeText(this, R.string.image_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCustomIconFile(flat: String) {
        val v = prefs.iconOverrides()[flat] ?: return
        if (v.startsWith("file|")) File(filesDir, "icons/" + v.substring(5)).delete()
    }

    private fun renameApp(component: ComponentName) {
        val flat = component.flattenToString()
        val original = allApps.firstOrNull { it.component == component }?.label ?: component.packageName
        val input = EditText(this).apply {
            setSingleLine()
            setText(labelOf(flat, original))
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val t = input.text.toString().trim()
                prefs.setCustomLabel(flat, if (t.isEmpty() || t == original) null else t)
                customLabels = prefs.customLabels()
                refreshAllIcons()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetCustom(component: ComponentName) {
        val flat = component.flattenToString()
        deleteCustomIconFile(flat)
        prefs.setIconOverride(flat, null)
        prefs.setCustomLabel(flat, null)
        customLabels = prefs.customLabels()
        refreshAllIcons()
    }

    private fun openPicker(component: ComponentName, pack: IconPack) {
        icons.withPack(pack.pkg) { loaded ->
            if (loaded.names.isEmpty()) {
                Toast.makeText(this, R.string.pack_empty, Toast.LENGTH_LONG).show()
                return@withPack
            }
            showIconPicker(component, loaded)
        }
    }

    private fun showIconPicker(component: ComponentName, pack: IconPack) {
        val dlg = Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val sizePx = dp(48)
        val pickAdapter = IconPickAdapter(pack, icons, sizePx) { name ->
            deleteCustomIconFile(component.flattenToString())
            prefs.setIconOverride(component.flattenToString(), pack.pkg + "|" + name)
            dlg.dismiss()
            refreshAllIcons()
        }
        val search = EditText(this).apply {
            hint = getString(R.string.search_icons)
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = pickAdapter.filter(s?.toString() ?: "")
            })
        }
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 5)
            this.adapter = pickAdapter
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(32), dp(12), dp(12))
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        dlg.setContentView(box)
        dlg.show()
    }

    private fun refreshAllIcons() {
        icons.reloadConfig()
        refreshHome()
        applyFilter()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_PAGES = 9
        private const val LOOP_COUNT = 100000
        private const val HOST_ID = 1024
        private const val REQ_IMAGE = 51
        private const val REQ_WIDGET_BIND = 52
        private const val REQ_WIDGET_CONFIG = 53
    }
}
