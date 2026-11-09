package com.nitro.os

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {

    // 0 = Java Boot (3s), 1 = Lock Screen, 2 = Win12 Bottom Pill, 3 = Left Vertical Pill + Apps Drawer
    private var osState = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rootFrame: FrameLayout
    private lateinit var wallpaperView: LiquidShaderWallpaper
    private lateinit var bootLayer: FrameLayout
    private lateinit var lockLayer: FrameLayout
    private lateinit var win12Layer: FrameLayout
    private lateinit var drawerLayer: FrameLayout

    private lateinit var bootStatusTv: TextView
    private lateinit var bootProgressFill: View
    private lateinit var lockClockText: TextView
    private lateinit var lockDateText: TextView
    private lateinit var win12ClockText: TextView
    private lateinit var pythonStatusBanner: TextView
    private lateinit var topStatusClock: TextView
    private lateinit var stageTitleText: TextView
    private lateinit var appsGridContainer: GridLayout

    private val installedApps = mutableListOf<AppItem>()
    private val recentApps = mutableListOf<AppItem>()
    private var wallpaperThemeIndex = 0
    private var pythonStatusText = "🐍 Python Engine Initializing..."

    data class AppItem(val name: String, val pkg: String, val icon: Drawable?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        rootFrame = FrameLayout(this)
        setContentView(rootFrame)

        // 1. Live GPU Liquid Glass Wallpaper
        wallpaperView = LiquidShaderWallpaper(this)
        rootFrame.addView(wallpaperView, matchParent())

        // 2. Build All 4 Native Screens
        buildBootLayer()
        buildLockLayer()
        buildWin12Layer()
        buildFullDrawerLayer()

        showState(0)
        startClockTicker()

        // 3. Start Python + App Scanner in Background
        thread {
            loadAllInstalledApps()
            initPythonEngine()
        }

        // 4. Start Guaranteed 3-Second Java Boot Engine
        NitroBootEngine.startFastBoot(this, object : NitroBootEngine.BootCallback {
            override fun onProgress(percent: Int, statusText: String) {
                bootStatusTv.text = statusText
                val lp = bootProgressFill.layoutParams
                lp.width = (520 * percent) / 100
                bootProgressFill.layoutParams = lp
            }

            override fun onBootComplete(summary: String) {
                showState(1) // Guaranteed switch to Lock Screen!
            }
        })

        syncFirebaseCloud()
    }

    private fun initPythonEngine() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            val py = Python.getInstance()
            val mod = py.getModule("nitro_optimizer")
            val result = mod.callAttr("run_nitro_python_boost", installedApps.size).toString()
            pythonStatusText = result
            handler.post {
                pythonStatusBanner.text = pythonStatusText
            }
        } catch (e: Exception) {
            pythonStatusText = "⚡ Native Core Active | Apps Ready"
        }
    }

    // =========================================================================
    // SCREEN 0: JAVA-POWERED 3-SECOND BOOT SCREEN
    // =========================================================================
    private fun buildBootLayer() {
        bootLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#030610"))
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "N I T R O   O S"
            textSize = 44f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(25f, 0f, 0f, Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
        }

        bootStatusTv = TextView(this).apply {
            text = "Starting Java & Python Core..."
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 32)
        }

        val progressBg = FrameLayout(this).apply {
            background = glassDrawable(Color.parseColor("#1E293B"), Color.parseColor("#334155"), 20f)
        }
        bootProgressFill = View(this).apply {
            background = glassDrawable(Color.parseColor("#00E5FF"), Color.WHITE, 20f)
        }
        progressBg.addView(bootProgressFill, FrameLayout.LayoutParams(20, 14))

        box.addView(logo)
        box.addView(bootStatusTv)
        box.addView(progressBg, LinearLayout.LayoutParams(520, 14))

        bootLayer.addView(box, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        rootFrame.addView(bootLayer, matchParent())
    }

    // =========================================================================
    // SCREEN 1: ULTRA-CLEAN LOCK SCREEN (SWIPE UP / D-PAD UP TO UNLOCK)
    // =========================================================================
    private fun buildLockLayer() {
        lockLayer = FrameLayout(this)

        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        lockClockText = TextView(this).apply {
            text = "07:45"
            textSize = 96f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(30f, 0f, 8f, Color.parseColor("#AA000000"))
            gravity = Gravity.CENTER
        }

        lockDateText = TextView(this).apply {
            text = "Monday, November 9"
            textSize = 20f
            setTextColor(Color.parseColor("#E2E8F0"))
            setShadowLayer(15f, 0f, 4f, Color.BLACK)
            gravity = Gravity.CENTER
        }

        centerBox.addView(lockClockText)
        centerBox.addView(lockDateText)

        lockLayer.addView(centerBox, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        val swipeHintBtn = TextView(this).apply {
            text = "▲   Swipe Up or Press D-Pad UP / OK to Unlock"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(46, 20, 46, 20)
            background = glassDrawable(Color.parseColor("#35FFFFFF"), Color.parseColor("#75FFFFFF"), 50f)
            isFocusable = true
            isClickable = true
            setOnClickListener { showState(2) }
        }

        val hintLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 54 }

        lockLayer.addView(swipeHintBtn, hintLp)
        rootFrame.addView(lockLayer, matchParent())
    }

    // =========================================================================
    // SCREEN 2: WINDOWS 12 CLEAN SCREEN (TOP TIME + 2 BUTTONS + BOTTOM PILL)
    // =========================================================================
    private fun buildWin12Layer() {
        win12Layer = FrameLayout(this)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 36, 48, 0)
        }

        win12ClockText = TextView(this).apply {
            text = "07:45 PM"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(12f, 0f, 2f, Color.BLACK)
        }

        pythonStatusBanner = TextView(this).apply {
            text = pythonStatusText
            textSize = 13f
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(24, 0, 0, 0)
        }

        topBar.addView(win12ClockText)
        topBar.addView(pythonStatusBanner)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        // Top 2 Buttons as requested: Refresh/Clear & Settings/Control Panel
        val refreshBtn = createGlassPillButton("🔄 Refresh & Boost") {
            NitroBootEngine.startFastBoot(this, object : NitroBootEngine.BootCallback {
                override fun onProgress(percent: Int, statusText: String) {}
                override fun onBootComplete(summary: String) {
                    Toast.makeText(this@MainActivity, "⚡ $summary", Toast.LENGTH_SHORT).show()
                }
            })
        }
        val settingsBtn = createGlassPillButton("⚙️ Status Panel") {
            showNitroStatusBar()
        }

        topBar.addView(refreshBtn)
        topBar.addView(View(this), LinearLayout.LayoutParams(18, 1))
        topBar.addView(settingsBtn)

        win12Layer.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        // Windows 12 Floating Bottom Pill
        val win12BottomPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(36, 18, 36, 18)
            background = glassDrawable(Color.parseColor("#440F172A"), Color.parseColor("#8800E5FF"), 60f, 3)
        }

        val downHubBtn = createGlassPillButton("▼  Press DOWN for Apps & Search Hub") {
            showState(3)
        }
        val wallpaperBtn = createGlassPillButton("🌌 Liquid Wallpaper") {
            wallpaperThemeIndex = (wallpaperThemeIndex + 1) % 4
            wallpaperView.setPalette(wallpaperThemeIndex)
        }
        val mapperBtn = createGlassPillButton("🎮 Button Mapper") {
            showButtonMapperDialog()
        }

        win12BottomPill.addView(downHubBtn)
        win12BottomPill.addView(View(this), LinearLayout.LayoutParams(16, 1))
        win12BottomPill.addView(wallpaperBtn)
        win12BottomPill.addView(View(this), LinearLayout.LayoutParams(16, 1))
        win12BottomPill.addView(mapperBtn)

        val dockLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 42 }

        win12Layer.addView(win12BottomPill, dockLp)
        rootFrame.addView(win12Layer, matchParent())
    }

    // =========================================================================
    // SCREEN 3: LEFT VERTICAL PILL BAR + GOOGLE TV APPS HUB
    // =========================================================================
    private fun buildFullDrawerLayer() {
        drawerLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#85020617"))
        }

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(34, 30, 34, 30)
        }

        // Left Vertical Pill-Shaped Bar (Home, Search, Apps, Recents, Status)
        val leftVerticalPill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 28, 16, 28)
            background = glassDrawable(Color.parseColor("#451E293B"), Color.parseColor("#8800E5FF"), 70f, 3)
        }

        val btnHome = createCircleIconBtn("🏠") { showState(2) }
        val btnSearch = createCircleIconBtn("🔍") { showUniversalSearchDialog() }
        val btnApps = createCircleIconBtn("📦") { populateAppGrid(installedApps, "All Installed TV Apps (${installedApps.size})") }
        val btnRecents = createCircleIconBtn("📑") { populateAppGrid(recentApps, "Recent Multitasking Apps (${recentApps.size})") }
        val btnStatus = createCircleIconBtn("⚙️") { showNitroStatusBar() }

        leftVerticalPill.addView(btnHome)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 20))
        leftVerticalPill.addView(btnSearch)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 20))
        leftVerticalPill.addView(btnApps)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 20))
        leftVerticalPill.addView(btnRecents)
        leftVerticalPill.addView(View(this), LinearLayout.LayoutParams(1, 20))
        leftVerticalPill.addView(btnStatus)

        val rightContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 8, 12, 8)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        stageTitleText = TextView(this).apply {
            text = "All Installed TV Apps"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        topStatusClock = TextView(this).apply {
            text = "07:45 PM"
            textSize = 18f
            setTextColor(Color.parseColor("#00E5FF"))
        }

        headerRow.addView(stageTitleText)
        headerRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        headerRow.addView(createGlassPillButton("🐍 Python Debloater") { showPythonDebloatReport() })
        headerRow.addView(View(this), LinearLayout.LayoutParams(16, 1))
        headerRow.addView(topStatusClock)

        val scrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setPadding(0, 22, 0, 22)
        }

        appsGridContainer = GridLayout(this).apply {
            columnCount = 4
        }
        scrollView.addView(appsGridContainer)

        rightContent.addView(headerRow)
        rightContent.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        mainRow.addView(leftVerticalPill, LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.MATCH_PARENT))
        mainRow.addView(rightContent, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        drawerLayer.addView(mainRow, matchParent())
        rootFrame.addView(drawerLayer, matchParent())
    }

    // =========================================================================
    // APP SCANNER & LIQUID GLASS CARDS
    // =========================================================================
    private fun loadAllInstalledApps() {
        val pm = packageManager
        val list = mutableListOf<AppItem>()
        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                    ?: pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null && app.packageName != packageName) {
                    val label = pm.getApplicationLabel(app).toString()
                    val icon = try { pm.getApplicationIcon(app) } catch (_: Exception) { null }
                    list.add(AppItem(label, app.packageName, icon))
                }
            }
            list.sortBy { it.name.lowercase() }
        } catch (_: Exception) {}

        installedApps.clear()
        installedApps.addAll(list)

        handler.post {
            populateAppGrid(installedApps, "All Installed TV Apps (${installedApps.size})")
        }
    }

    private fun populateAppGrid(apps: List<AppItem>, title: String) {
        stageTitleText.text = title
        appsGridContainer.removeAllViews()

        if (apps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No apps found here."
                textSize = 18f
                setTextColor(Color.LTGRAY)
                setPadding(20, 40, 20, 20)
            }
            appsGridContainer.addView(emptyTv)
            return
        }

        for (app in apps) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(20, 22, 20, 18)
                background = glassDrawable(Color.parseColor("#381E293B"), Color.parseColor("#66FFFFFF"), 32f, 2)
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).start()
                        v.background = glassDrawable(Color.parseColor("#660F172A"), Color.parseColor("#00E5FF"), 32f, 4)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start()
                        v.background = glassDrawable(Color.parseColor("#381E293B"), Color.parseColor("#66FFFFFF"), 32f, 2)
                    }
                }

                setOnClickListener {
                    recentApps.removeAll { it.pkg == app.pkg }
                    recentApps.add(0, app)
                    val launchIntent = packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
                        ?: packageManager.getLaunchIntentForPackage(app.pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                }
            }

            val iconView = ImageView(this).apply {
                if (app.icon != null) setImageDrawable(app.icon)
                else setImageResource(android.R.drawable.sym_def_app_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val nameView = TextView(this).apply {
                text = app.name
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 12, 0, 0)
            }

            card.addView(iconView, LinearLayout.LayoutParams(92, 92))
            card.addView(nameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val glp = GridLayout.LayoutParams().apply {
                width = 245
                height = 190
                setMargins(15, 15, 15, 15)
            }
            appsGridContainer.addView(card, glp)
        }
    }

    // =========================================================================
    // STATUS BAR, PYTHON DEBLOATER & BUTTON MAPPER
    // =========================================================================
    private fun showNitroStatusBar() {
        val opti
