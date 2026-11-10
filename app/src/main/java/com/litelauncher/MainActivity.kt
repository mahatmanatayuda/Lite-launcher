package com.litelauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconManager
    private lateinit var model: HomeModel
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var pageAdapter: PageAdapter
    private lateinit var snap: PagerSnapHelper

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

    private var allApps: List<AppInfo> = emptyList()
    private var drawerOpen = false
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
            val target = currentPage() + edgeDir
            if (target < 0) return
            if (target >= model.pages) {
                if (model.pages >= MAX_PAGES) return
                model.pages++
                pageAdapter.notifyItemInserted(model.pages - 1)
                updateDots()
            }
            pager.smoothScrollToPosition(target)
            pager.postDelayed(this, 800)
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

    override fun onResume() {
        super.onResume()
        icons.reloadConfig()
        model = HomeModel.load(this)
        applyGridPrefs()
        if (model.normalize()) model.save(this)
        clockBox.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        hotseat.layoutParams.height = dp(prefs.iconSizeDp) + dp(24)
        hotseat.requestLayout()
        (grid.layoutManager as GridLayoutManager).spanCount = prefs.drawerCols
        drawerAdapter.iconSizePx = dp(prefs.iconSizeDp)
        drawerAdapter.showLabels = prefs.showLabels
        applyFilter()
        refreshHome()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        worker.shutdown()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) icons.clear()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            closeFolder()
            closeDrawer()
            pager.smoothScrollToPosition(0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            openFolderId != null -> closeFolder()
            drawerOpen -> closeDrawer()
        }
    }

    private fun applyGridPrefs() {
        model.cols = prefs.cols
        model.rows = prefs.rows
        model.hotseat = prefs.hotseat
    }

    // ---------- Home pager ----------

    inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val cell: CellLayout) : RecyclerView.ViewHolder(cell)

        override fun getItemCount() = model.pages

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
            fillCells(holder.cell, HomeItem.DESKTOP, position, model.cols, model.rows, prefs.showLabels)
        }
    }

    private fun refreshHome() {
        pageAdapter.notifyDataSetChanged()
        fillCells(hotseat, HomeItem.HOTSEAT, 0, model.hotseat, 1, false)
        updateDots()
    }

    private fun fillCells(cell: CellLayout, container: Long, screen: Int, cols: Int, rows: Int, labels: Boolean) {
        cell.removeAllViews()
        cell.cols = cols
        cell.rows = rows
        for (item in model.items) {
            if (item.container == container && (container != HomeItem.DESKTOP || item.screen == screen) &&
                item.x < cols && item.y < rows
            ) {
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
        label.text = item.title
        label.visibility = if (labels) View.VISIBLE else View.GONE
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
        v.setOnLongClickListener { startItemDrag(v, item); true }
        return v
    }

    private fun currentPage(): Int {
        val v = snap.findSnapView(pager.layoutManager) ?: return 0
        return pager.getChildAdapterPosition(v).coerceAtLeast(0)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHomeGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                if (abs(dy) > abs(dx) && abs(vy) > 600) {
                    if (dy < 0) openDrawer() else expandNotifications()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val action = prefs.doubleTap
                if (action == 0) return false
                // Only empty space counts, so double-tapping an icon never triggers this.
                val page = snap.findSnapView(pager.layoutManager) as? CellLayout
                if (page != null) {
                    val cell = page.cellAt(e.x - page.left, e.y - page.top)
                    if (model.at(HomeItem.DESKTOP, currentPage(), cell.first, cell.second) != null) return false
                }
                when (action) {
                    1 -> openDrawer()
                    2 -> expandNotifications()
                    3 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                return true
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
            getString(R.string.add_page) to {
                if (model.pages < MAX_PAGES) {
                    model.pages++
                    model.save(this)
                    refreshHome()
                    pager.scrollToPosition(model.pages - 1)
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

    // ---------- Drag and drop ----------

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
                rectIn(actRemove).contains(x, y) -> model.remove(item)
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

        val page = snap.findSnapView(pager.layoutManager) as? CellLayout ?: return false
        val pr = rectIn(page)
        if (!pr.contains(x, y)) return false
        val cell = page.cellAt((x - pr.left).toFloat(), (y - pr.top).toFloat())
        return dropOn(item, HomeItem.DESKTOP, pager.getChildAdapterPosition(page), cell.first, cell.second)
    }

    private fun dropOn(item: HomeItem, container: Long, screen: Int, x: Int, y: Int): Boolean {
        val target = model.at(container, screen, x, y)
        if (target === item) {
            // Dropped back where it was: treat as a tap-and-hold menu.
            showItemDialog(item)
            return true
        }
        val ok = model.dropAt(item, container, screen, x, y)
        model.save(this)
        return ok
    }

    private fun showItemDialog(item: HomeItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (item.type == HomeItem.TYPE_APP) {
            labels += getString(R.string.edit_icon)
            actions += { comp(item.component)?.let { editIcon(it) } }
        } else {
            labels += getString(R.string.rename)
            actions += { openFolder(item) }
        }
        labels += getString(R.string.remove)
        actions += { model.remove(item); model.save(this); refreshHome() }
        if (item.type == HomeItem.TYPE_APP) {
            labels += getString(R.string.app_info)
            actions += { comp(item.component)?.let { openAppInfo(it.packageName) } }
            labels += getString(R.string.uninstall)
            actions += { comp(item.component)?.let { uninstall(it.packageName) } }
        }
        AlertDialog.Builder(this)
            .setTitle(item.title)
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
            label.text = child.title
            v.setOnClickListener { launchComponent(child.component) }
            v.setOnLongClickListener { startItemDrag(v, child); true }
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
        menu.menu.add(0, 3, 2, R.string.hide)
        menu.menu.add(0, 4, 3, R.string.app_info)
        menu.menu.add(0, 5, 4, R.string.uninstall)
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
            }
            true
        }
        menu.show()
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
        val list = allApps.filter { it.packageName !in hidden }
            .let { l -> if (q.isEmpty()) l else l.filter { it.label.lowercase().contains(q) } }
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

    // ---------- Icon editing ----------

    private fun editIcon(component: ComponentName) {
        val flat = component.flattenToString()
        val options = arrayOf(getString(R.string.choose_from_pack), getString(R.string.reset_icon))
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                if (which == 1) {
                    prefs.setIconOverride(flat, null)
                    refreshAllIcons()
                } else {
                    val packs = icons.listPacks()
                    when {
                        packs.isEmpty() -> Toast.makeText(this, R.string.no_icon_packs, Toast.LENGTH_LONG).show()
                        packs.size == 1 -> openPicker(component, packs[0])
                        else -> AlertDialog.Builder(this)
                            .setItems(packs.map { it.label }.toTypedArray()) { _, i -> openPicker(component, packs[i]) }
                            .show()
                    }
                }
            }
            .show()
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
        drawerAdapter.notifyDataSetChanged()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_PAGES = 9
    }
}
