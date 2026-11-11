package com.litelauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var icons: IconManager
    private lateinit var content: LinearLayout
    private lateinit var packButton: Button
    private lateinit var shapeButton: Button

    private val shapeValues = listOf("system", "circle", "squircle", "rounded")
    private val actionValues = listOf("none", "drawer", "notifications", "settings")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        icons = IconManager(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(content) })

        addTitle(getString(R.string.settings))

        addHeader(getString(R.string.section_desktop))
        addSeek(R.string.desktop_cols, 3, 8, { prefs.cols }, { prefs.cols = it })
        addSeek(R.string.desktop_rows, 3, 8, { prefs.rows }, { prefs.rows = it })
        addSeek(R.string.dock_icons, 3, 7, { prefs.hotseat }, { prefs.hotseat = it })
        addSwitch(R.string.show_clock, prefs.showClock) { prefs.showClock = it }
        addSwitch(R.string.lock_desktop, prefs.lockDesktop) { prefs.lockDesktop = it }

        addHeader(getString(R.string.section_drawer))
        addSeek(R.string.drawer_cols, 3, 6, { prefs.drawerCols }, { prefs.drawerCols = it })

        addHeader(getString(R.string.section_icons))
        addSeek(R.string.icon_size, 40, 72, { prefs.iconSizeDp }, { prefs.iconSizeDp = it }, " dp")
        addSwitch(R.string.show_labels, prefs.showLabels) { prefs.showLabels = it }
        addSwitch(R.string.notif_dots, prefs.notifBadges) { on ->
            prefs.notifBadges = on
            if (on && !NotifState.isEnabled(this)) {
                toast(R.string.notif_dots_help)
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        packButton = addButton("") { choosePack() }
        updatePackButton()
        shapeButton = addButton("") { chooseShape() }
        updateShapeButton()

        addHeader(getString(R.string.section_gestures))
        addActionButton(R.string.swipe_up, { prefs.swipeUp }, { prefs.swipeUp = it })
        addActionButton(R.string.swipe_down, { prefs.swipeDown }, { prefs.swipeDown = it })
        addActionButton(R.string.double_tap, { prefs.doubleTap }, { prefs.doubleTap = it })

        addHeader(getString(R.string.section_backup))
        addButton(getString(R.string.backup_launcher)) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "lite-launcher-backup.json")
            startActivityForResult(i, REQ_BACKUP)
        }
        addButton(getString(R.string.restore_launcher)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.restore_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    startActivityForResult(i, REQ_RESTORE)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        addButton(getString(R.string.import_nova)) { confirmNovaImport() }

        addHeader(getString(R.string.section_other))
        addButton(getString(R.string.unhide_all)) {
            prefs.hidden = emptySet()
            toast(R.string.done)
        }
        addButton(getString(R.string.reset_home)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.reset_home_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    HomeModel.file(this).delete()
                    toast(R.string.done)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        addButton(getString(R.string.set_default)) { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    // ---------- Icon pack & shape ----------

    private fun updatePackButton() {
        val current = prefs.iconPack
        val name = if (current == null) getString(R.string.system_icons)
        else icons.listPacks().firstOrNull { it.pkg == current }?.label ?: current
        packButton.text = getString(R.string.icon_pack) + ": " + name
    }

    private fun choosePack() {
        val packs = icons.listPacks()
        val names = (listOf(getString(R.string.system_icons)) + packs.map { it.label }).toTypedArray()
        AlertDialog.Builder(this)
            .setItems(names) { _, i ->
                prefs.iconPack = if (i == 0) null else packs[i - 1].pkg
                updatePackButton()
            }
            .show()
        if (packs.isEmpty()) toast(R.string.no_icon_packs)
    }

    private fun shapeLabel(v: String) = getString(
        when (v) {
            "circle" -> R.string.shape_circle
            "squircle" -> R.string.shape_squircle
            "rounded" -> R.string.shape_rounded
            else -> R.string.shape_system
        }
    )

    private fun updateShapeButton() {
        shapeButton.text = getString(R.string.icon_shape) + ": " + shapeLabel(prefs.iconShape)
    }

    private fun chooseShape() {
        AlertDialog.Builder(this)
            .setItems(shapeValues.map { shapeLabel(it) }.toTypedArray()) { _, i ->
                prefs.iconShape = shapeValues[i]
                updateShapeButton()
            }
            .show()
    }

    // ---------- Gestures ----------

    private fun actionLabel(v: String) = getString(
        when (v) {
            "drawer" -> R.string.action_drawer
            "notifications" -> R.string.action_notifications
            "settings" -> R.string.action_settings
            else -> R.string.action_none
        }
    )

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
        content.addView(b)
    }

    // ---------- Backup, restore, Nova import ----------

    private fun confirmNovaImport() {
        AlertDialog.Builder(this)
            .setMessage(R.string.import_nova_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                startActivityForResult(i, REQ_NOVA)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        when (requestCode) {
            REQ_NOVA -> importNova(uri)
            REQ_BACKUP -> runIo(R.string.backup_done, false) { BackupManager.write(this, uri) }
            REQ_RESTORE -> runIo(R.string.restore_done, true) { BackupManager.read(this, uri) }
        }
    }

    /** Run file work off the main thread and report the result. */
    private fun runIo(doneMessage: Int, reload: Boolean, block: () -> Unit) {
        Thread {
            try {
                block()
                runOnUiThread {
                    toast(doneMessage)
                    if (reload) recreate()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.backup_failed) + "\n" + (e.message ?: ""))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    private fun importNova(uri: Uri) {
        toast(R.string.importing)
        Thread {
            try {
                val apps = AppRepository.load(this)
                val r = NovaImporter.import(this, uri, apps)
                runOnUiThread {
                    if (r.apps == 0) {
                        AlertDialog.Builder(this).setMessage(R.string.import_empty).setPositiveButton(android.R.string.ok, null).show()
                    } else {
                        prefs.cols = r.cols
                        prefs.rows = r.rows
                        prefs.hotseat = r.hotseat
                        r.model.save(this)
                        AlertDialog.Builder(this)
                            .setMessage(
                                getString(R.string.import_done, r.apps, r.folders, r.model.pages, r.skippedOther, r.missingApps)
                            )
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.import_failed) + "\n" + (e.message ?: ""))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 0, 0, dp(12))
        })
    }

    private fun addHeader(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.accent, null))
            textSize = 14f
            setPadding(0, dp(20), 0, dp(4))
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
        content.addView(row)
        content.addView(bar)
    }

    private fun addSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        content.addView(Switch(this).apply {
            setText(label)
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = checked
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, c -> onChange(c) }
        })
    }

    private fun addButton(text: String, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
        content.addView(b)
        return b
    }

    companion object {
        private const val REQ_NOVA = 42
        private const val REQ_BACKUP = 43
        private const val REQ_RESTORE = 44
    }
}
