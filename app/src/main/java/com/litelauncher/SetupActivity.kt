package com.litelauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * First-run guide: a few choices, then "make this your home app".
 * It has its own icon ("Lite Launcher Setup") and can also be started again from Settings.
 */
class SetupActivity : Activity() {

    private class Preset(val cols: Int, val rows: Int, val hotseat: Int, val iconDp: Int, val label: Int)

    private lateinit var prefs: Prefs
    private lateinit var icons: IconManager
    private lateinit var counterView: TextView
    private lateinit var titleView: TextView
    private lateinit var body: LinearLayout
    private lateinit var backButton: Button
    private lateinit var nextButton: Button
    private var step = 0

    private val presets = listOf(
        Preset(4, 5, 4, 64, R.string.setup_grid_roomy),
        Preset(5, 5, 5, 56, R.string.setup_grid_standard),
        Preset(6, 6, 5, 48, R.string.setup_grid_dense),
    )
    private val shapeValues = listOf("system", "circle", "squircle", "rounded")
    private val actionValues = listOf("none", "drawer", "notifications", "settings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        icons = IconManager(this)

        counterView = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            setPadding(0, dp(8), 0, dp(12))
        }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        backButton = Button(this).apply {
            setText(R.string.setup_back)
            setOnClickListener { show(step - 1) }
        }
        nextButton = Button(this).apply {
            setOnClickListener { if (step == TOTAL - 1) finish() else show(step + 1) }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(nextButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(16))
            addView(counterView)
            addView(titleView)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        setContentView(root)
        show(0)
    }

    private fun show(i: Int) {
        step = i.coerceIn(0, TOTAL - 1)
        body.removeAllViews()
        counterView.text = getString(R.string.setup_step, step + 1, TOTAL)
        backButton.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        nextButton.setText(if (step == TOTAL - 1) R.string.setup_finish else R.string.setup_next)
        when (step) {
            0 -> stepWelcome()
            1 -> stepLayout()
            2 -> stepIcons()
            3 -> stepGestures()
            4 -> stepExtras()
            else -> stepFinish()
        }
    }

    // ---------- Steps ----------

    private fun stepWelcome() {
        titleView.setText(R.string.setup_welcome_title)
        addText(getString(R.string.setup_welcome_body))
        addButton(getString(R.string.setup_import)) { pickFile(REQ_NOVA) }
        addButton(getString(R.string.wiz_restore)) { pickFile(REQ_RESTORE) }
        addText(getString(R.string.setup_or_manual))
    }

    private fun stepLayout() {
        titleView.setText(R.string.setup_layout_title)
        addText(getString(R.string.wiz_layout_hint))
        addHeader(getString(R.string.setup_grid))
        for (p in presets) {
            val selected = prefs.cols == p.cols && prefs.rows == p.rows && prefs.iconSizeDp == p.iconDp
            addButton((if (selected) "✓  " else "") + getString(p.label)) {
                prefs.cols = p.cols
                prefs.rows = p.rows
                prefs.hotseat = p.hotseat
                prefs.iconSizeDp = p.iconDp
                show(1)
            }
        }
        addSeek(R.string.desktop_cols, 3, 8, { prefs.cols }, { prefs.cols = it })
        addSeek(R.string.desktop_rows, 3, 8, { prefs.rows }, { prefs.rows = it })
        addSeek(R.string.dock_icons, 3, 7, { prefs.hotseat }, { prefs.hotseat = it })
        addSeek(R.string.icon_size, 40, 72, { prefs.iconSizeDp }, { prefs.iconSizeDp = it }, " dp")
        addSwitch(R.string.show_labels, prefs.showLabels) { prefs.showLabels = it }
        addSwitch(R.string.show_clock, prefs.showClock) { prefs.showClock = it }
    }

    private fun stepIcons() {
        titleView.setText(R.string.setup_icons_title)
        addText(getString(R.string.wiz_icons_hint))
        addHeader(getString(R.string.icon_shape))
        for (v in shapeValues) {
            addButton((if (prefs.iconShape == v) "✓  " else "") + shapeLabel(v)) {
                prefs.iconShape = v
                show(2)
            }
        }
        addHeader(getString(R.string.icon_pack))
        val packs = icons.listPacks()
        if (packs.isEmpty()) {
            addText(getString(R.string.no_icon_packs))
        } else {
            val current = prefs.iconPack
            val name = if (current == null) getString(R.string.system_icons)
            else packs.firstOrNull { it.pkg == current }?.label ?: current
            addButton(getString(R.string.icon_pack) + ": " + name) { choosePack(packs) }
        }
        addSeek(R.string.drawer_cols, 3, 6, { prefs.drawerCols }, { prefs.drawerCols = it })
    }

    private fun stepGestures() {
        titleView.setText(R.string.setup_gestures_title)
        addText(getString(R.string.wiz_gestures_hint))
        addActionButton(R.string.swipe_up, { prefs.swipeUp }, { prefs.swipeUp = it })
        addActionButton(R.string.swipe_down, { prefs.swipeDown }, { prefs.swipeDown = it })
        addActionButton(R.string.double_tap, { prefs.doubleTap }, { prefs.doubleTap = it })
    }

    private fun stepExtras() {
        titleView.setText(R.string.setup_extras_title)
        addText(getString(R.string.wiz_extras_hint))
        addSwitch(R.string.notif_dots, prefs.notifBadges) { on ->
            prefs.notifBadges = on
            if (on && !NotifState.isEnabled(this)) {
                toast(R.string.notif_dots_help)
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        addSwitch(R.string.lock_desktop, prefs.lockDesktop) { prefs.lockDesktop = it }
    }

    private fun stepFinish() {
        titleView.setText(R.string.setup_finish_title)
        addText(getString(R.string.setup_finish_body))
        addText(getString(R.string.wiz_summary, prefs.cols, prefs.rows, prefs.hotseat, prefs.iconSizeDp))
        addButton(getString(R.string.set_default)) { openHomeChooser() }
        addButton(getString(R.string.setup_open_settings)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun openHomeChooser() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            // Some ROMs have no such screen; pressing Home shows the chooser instead.
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
    }

    // ---------- Import and restore ----------

    private fun pickFile(code: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        startActivityForResult(i, code)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQ_NOVA -> importNova(uri)
            REQ_RESTORE -> restore(uri)
        }
    }

    private fun importNova(uri: Uri) {
        toast(R.string.importing)
        Thread {
            try {
                val apps = AppRepository.load(this)
                val r = NovaImporter.import(this, uri, apps)
                runOnUiThread {
                    if (r.apps == 0) {
                        AlertDialog.Builder(this)
                            .setMessage(R.string.import_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        prefs.cols = r.cols
                        prefs.rows = r.rows
                        prefs.hotseat = r.hotseat
                        r.model.save(this)
                        AlertDialog.Builder(this)
                            .setMessage(
                                getString(R.string.import_done, r.apps, r.folders, r.model.pages, r.skippedOther, r.missingApps)
                            )
                            .setPositiveButton(android.R.string.ok) { _, _ -> show(1) }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.import_failed), e) }
            }
        }.start()
    }

    private fun restore(uri: Uri) {
        Thread {
            try {
                BackupManager.read(this, uri)
                runOnUiThread {
                    toast(R.string.restore_done)
                    show(1)
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.backup_failed), e) }
            }
        }.start()
    }

    private fun showError(message: String, e: Exception) {
        AlertDialog.Builder(this)
            .setMessage(message + "\n" + (e.message ?: ""))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- Choices ----------

    private fun shapeLabel(v: String) = getString(
        when (v) {
            "circle" -> R.string.shape_circle
            "squircle" -> R.string.shape_squircle
            "rounded" -> R.string.shape_rounded
            else -> R.string.shape_system
        }
    )

    private fun actionLabel(v: String) = getString(
        when (v) {
            "drawer" -> R.string.action_drawer
            "notifications" -> R.string.action_notifications
            "settings" -> R.string.action_settings
            else -> R.string.action_none
        }
    )

    private fun choosePack(packs: List<IconPack>) {
        val names = (listOf(getString(R.string.system_icons)) + packs.map { it.label }).toTypedArray()
        AlertDialog.Builder(this)
            .setItems(names) { _, i ->
                prefs.iconPack = if (i == 0) null else packs[i - 1].pkg
                show(2)
            }
            .show()
    }

    private fun addActionButton(label: Int, get: () -> String, set: (String) -> Unit) {
        val b = Button(this)
        fun refresh() {
            b.text = getString(label) + ": " + actionLabel(get())
        }
        b.setOnClickListener {
            AlertDialog.Builder(this)
                .setItems(actionValues.map { actionLabel(it) }.toTypedArray()) { _, i ->
                    set(actionValues[i])
                    refresh()
                }
                .show()
        }
        refresh()
        body.addView(b)
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun addText(text: String) {
        body.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 15f
            setPadding(0, dp(4), 0, dp(12))
        })
    }

    private fun addHeader(text: String) {
        body.addView(TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.accent, null))
            textSize = 14f
            setPadding(0, dp(12), 0, dp(4))
        })
    }

    private fun addButton(text: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
        body.addView(b)
        return b
    }

    private fun addSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        body.addView(Switch(this).apply {
            setText(label)
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = checked
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
    }

    private fun addSeek(label: Int, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit, suffix: String = "") {
        val name = TextView(this).apply {
            setText(label)
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val value = TextView(this).apply {
            text = get().toString() + suffix
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 16f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = get().coerceIn(min, max) - min
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    set(p + min)
                    value.text = (p + min).toString() + suffix
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        body.addView(row)
        body.addView(bar)
    }

    companion object {
        private const val TOTAL = 6
        private const val REQ_NOVA = 61
        private const val REQ_RESTORE = 62
    }
}
