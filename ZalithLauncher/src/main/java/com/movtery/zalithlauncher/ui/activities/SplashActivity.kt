/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.SplashException
import com.movtery.zalithlauncher.components.Components
import com.movtery.zalithlauncher.components.InstallableItem
import com.movtery.zalithlauncher.components.UnpackComponentsTask
import com.movtery.zalithlauncher.components.UnpackMinecraftTask
import com.movtery.zalithlauncher.components.jre.Jre
import com.movtery.zalithlauncher.components.jre.UnpackJnaTask
import com.movtery.zalithlauncher.components.jre.UnpackJreTask
import com.movtery.zalithlauncher.setting.AllSettings
import com.umeng.message.PushAgent
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity
import com.movtery.zalithlauncher.ui.screens.splash.SplashScreen
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.ui.theme.backgroundColor
import com.movtery.zalithlauncher.ui.theme.onBackgroundColor
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.SplashBackStackViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "SplashActivity"

const val EXTRA_IMPORT_ACTION = "EXTRA_IMPORT_ACTION"
const val EXTRA_IMPORT_URI    = "EXTRA_IMPORT_URI"
const val EXTRA_IMPORT_TYPE   = "EXTRA_IMPORT_TYPE"

const val IMPORT_TYPE_MODPACK = "modpack"
const val IMPORT_TYPE_CONTROLS = "controls"
const val IMPORT_TYPE_UNKNOWN = "unknown"

/** 友盟开屏广告位ID */
private const val SPLASH_AD_SLOT_ID = "100012689"

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : BaseAppCompatActivity() {
    private val unpackItems: MutableList<InstallableItem> = ArrayList()
    private val finishedTaskCount = AtomicInteger(0)

    private val backStackViewModel: SplashBackStackViewModel by viewModels()

    /** 开屏广告容器 */
    private var splashAdContainer: FrameLayout? = null

    /** 开屏广告是否已关闭 */
    private var isSplashAdDismissed = false

    /** 是否已经跳转到主界面 */
    private var hasNavigatedToMain = false

    /** 是否可以跳转（参考友盟 UMSplashAdDemo：onResume 时 canJump=true 则直接跳转） */
    private var canJump = false

    /** 开屏广告请求超时（参考友盟 UMSplashAdDemo：超时后直接进入主界面） */
    private var mReqTimeout: Runnable? = null

    private val mHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 友盟推送：活跃统计（必须在同意隐私政策后调用）
        PushAgent.getInstance(this).onAppStart()

        initUnpackItems()
        checkAllTask()

        // 加载开屏广告
        if (AllSettings.showSplashAd.getValue()) {
            loadSplashAd()
        }

        if (checkTasksToMain()) {
            return
        }

        setContent {
            ZalithLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    SplashScreen(
                        startAllTask = { startAllTask() },
                        unpackItems = unpackItems,
                        screenViewModel = backStackViewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 参考友盟 UMSplashAdDemo：从后台切回时若已经可以跳转则直接跳转
        if (canJump) {
            goToContentOrHome()
        }
        canJump = true
    }

    override fun onPause() {
        super.onPause()
        // 参考友盟 UMSplashAdDemo：离开页面时不允许跳转
        canJump = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // 参考友盟 UMSplashAdDemo：清理超时任务
        mReqTimeout?.let { mHandler.removeCallbacks(it) }
        mReqTimeout = null
    }

    /**
     * 禁止返回键退出开屏广告（参考友盟 UMSplashAdDemo）
     */
    override fun onBackPressed() {
        // 开屏广告展示期间禁用返回键
    }

    /**
     * 加载并展示友盟开屏广告（参考友盟 UMSplashAdDemo）
     */
    private fun loadSplashAd() {
        splashAdContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val config = UMAdConfig.Builder()
            .setSlotId(SPLASH_AD_SLOT_ID)
            .build()

        // 参考友盟 UMSplashAdDemo：设置请求超时
        mReqTimeout = Runnable {
            mReqTimeout = null
            goToContentOrHome()
        }
        mHandler.postDelayed(mReqTimeout!!, 5000)

        UMUnionSdk.loadSplashAd(config, object : UMUnionApi.AdRenderListener<UMSplashAD> {
            override fun onSuccess(type: UMUnionApi.AdType, display: UMSplashAD) {
                // 广告请求成功，可用于比价
                Logger.info(TAG, "Splash ad request success")
            }

            override fun onFailure(type: UMUnionApi.AdType, message: String) {
                // 广告请求失败，直接跳过
                Logger.warning(TAG, "Splash ad request failure: $message")
                // 参考友盟 UMSplashAdDemo：请求失败移除超时
                mReqTimeout?.let { mHandler.removeCallbacks(it) }
                mReqTimeout = null
                if (isFinishing) return
                goToContentOrHome()
            }

            override fun onRenderSuccess(type: UMUnionApi.AdType, display: UMSplashAD) {
                // 素材加载完成，可以展示
                Logger.info(TAG, "Splash ad render success, showing ad")
                // 参考友盟 UMSplashAdDemo：请求成功后移除超时
                mReqTimeout?.let { mHandler.removeCallbacks(it) }
                mReqTimeout = null

                if (isFinishing || isDestroyed) return

                display.setAdEventListener(object : UMUnionApi.SplashAdListener {
                    override fun onExposed() {
                        Logger.info(TAG, "Splash ad exposed")
                    }

                    override fun onClicked(view: android.view.View) {
                        Logger.info(TAG, "Splash ad clicked")
                    }

                    override fun onDismissed() {
                        Logger.info(TAG, "Splash ad dismissed")
                        isSplashAdDismissed = true
                        goToContentOrHome()
                    }

                    override fun onError(code: Int, message: String) {
                        Logger.warning(TAG, "Splash ad error: code=$code, msg=$message")
                        isSplashAdDismissed = true
                        goToContentOrHome()
                    }
                })

                splashAdContainer?.let { container ->
                    setContentView(container)
                }
                splashAdContainer?.let { display.show(it) }
            }

            override fun onRenderFailure(type: UMUnionApi.AdType, message: String) {
                // 素材渲染失败
                Logger.warning(TAG, "Splash ad render failure: $message")
                goToContentOrHome()
            }
        }, 5000)
    }

    /**
     * 广告关闭/失败时尝试进入内容（参考友盟 UMSplashAdDemo goHome 模式）
     * 使用 canJump 防止在后台误跳转
     */
    private fun goToContentOrHome() {
        Logger.info(TAG, "goToContentOrHome canJump=$canJump")
        if (canJump) {
            if (areAllTasksFinished()) {
                navigateToMain()
            }
        } else {
            canJump = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        //若依赖未完成，忽略所有外部导入
        if (!areAllTasksFinished()) {
            Logger.info(TAG, "Import intent received but dependencies are not ready, ignoring.")
            return
        }

        if (isImportIntent(intent) && !isLauncherIntent(intent)) {
            handleImportIntent(intent)
            finish()
        }
    }

    private fun initUnpackItems() {
        Components.entries.forEach { component ->
            val task = UnpackComponentsTask(this@SplashActivity, component)
            if (!task.isCheckFailed()) {
                unpackItems.add(
                    InstallableItem(
                        component.displayName,
                        getString(component.summary),
                        task
                    )
                )
            }
        }
        Jre.entries.forEach { jre ->
            val task = UnpackJreTask(this@SplashActivity, jre)
            if (!task.isCheckFailed()) {
                unpackItems.add(
                    InstallableItem(
                        jre.jreName,
                        getString(jre.summary),
                        task
                    )
                )
            }
        }
        val jnaTask = UnpackJnaTask(this@SplashActivity)
        if (!jnaTask.isCheckFailed()) {
            unpackItems.add(
                InstallableItem(
                    "JNA",
                    getString(R.string.unpack_screen_jna),
                    jnaTask
                )
            )
        }
        val minecraftTask = UnpackMinecraftTask(this@SplashActivity)
        if (!minecraftTask.isCheckFailed()) {
            unpackItems.add(
                InstallableItem(
                    ".minecraft",
                    getString(R.string.unpack_screen_minecraft),
                    minecraftTask
                )
            )
        }
        unpackItems.sort()
    }

    private fun checkAllTask() {
        //检查应用 assets 目录
        listAssetsPath("runtimes").forEach { filePath ->
            Logger.info(TAG, "The launcher contains the runtime environment: $filePath")
        }

        unpackItems.forEach { item ->
            val state = item.task.checkState()
            item.updateState(state)
            if (state == InstallableItem.State.FINISHED) {
                finishedTaskCount.incrementAndGet()
            }
        }
    }

    private fun listAssetsPath(root: String): List<String> {
        return buildList {
            val rootFiles = runCatching {
                assets.list(root)?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            if (rootFiles != null) {
                rootFiles.forEach { child ->
                    val childPath = "$root/$child"
                    val childFiles = runCatching {
                        assets.list(childPath)?.takeIf { it.isNotEmpty() }
                    }.getOrNull()

                    if (childFiles != null) {
                        addAll(listAssetsPath(childPath))
                    } else {
                        add(childPath)
                    }
                }
            } else {
                add(root)
            }
        }
    }

    private fun startAllTask() {
        lifecycleScope.launch {
            val jobs = unpackItems
                .filter {
                    val state = it.state.value
                    state == InstallableItem.State.NOT_STARTED ||
                    state == InstallableItem.State.PENDING
                }
                .map { item ->
                    launch(Dispatchers.IO) {
                        item.updateState(InstallableItem.State.RUNNING)
                        runCatching {
                            item.task.run()
                        }.onFailure {
                            throw SplashException(it)
                        }
                        finishedTaskCount.incrementAndGet()
                        item.updateState(InstallableItem.State.FINISHED)
                    }
                }
            jobs.joinAll()
        }.invokeOnCompletion {
            AllSettings.javaRuntime.apply {
                //检查并设置默认的Java环境
                if (getValue().isEmpty()) save(Jre.JRE_8.jreName)
            }
            // 如果广告已关闭或未展示，直接跳转主界面
            if (isSplashAdDismissed) {
                navigateToMain()
            }
        }
    }

    private fun checkTasksToMain(): Boolean {
        if (!areAllTasksFinished()) return false

        Logger.info(TAG, "All content that needs to be extracted is already the latest version!")

        if (isImportIntent(intent) && !isLauncherIntent(intent)) {
            val success = handleImportIntent(intent)
            if (success) {
                finish()
                return true
            }
        }

        swapToMain()
        return true
    }

    private fun swapToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        hasNavigatedToMain = true
        finish()
    }

    /** 统一的主界面跳转入口 */
    private fun navigateToMain() {
        if (hasNavigatedToMain) return
        swapToMain()
    }



    private fun handleImportIntent(source: Intent): Boolean {
        if (!isImportIntent(source)) return false

        val uri: Uri? = when (source.action) {
            Intent.ACTION_SEND -> {
                source.clipData?.getItemAt(0)?.uri
                    ?: source.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> source.data
            else -> null
        }

        if (uri == null) {
            Logger.warning(TAG, "No valid import Uri found")
            return false
        } else {
            try {
                //可持久化访问授权
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                Logger.warning(TAG, "No persistable permission granted for $uri")
            }
        }

        val forward = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(EXTRA_IMPORT_ACTION, source.action)
            putExtra(EXTRA_IMPORT_URI, uri)
            putExtra(EXTRA_IMPORT_TYPE, resolveImportType(source))
        }

        startActivity(forward)
        return true
    }

    /**
     * 根据 AndroidManifest 内为 activity-alias 配置的 meta-data 来判断导入类型
     */
    private fun resolveImportType(intent: Intent): String {
        val comp = intent.component ?: return IMPORT_TYPE_UNKNOWN
        val info = packageManager.getActivityInfo(comp, PackageManager.GET_META_DATA)
        return info.metaData?.getString("import_type") ?: IMPORT_TYPE_UNKNOWN
    }

    private fun isLauncherIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == Intent.ACTION_MAIN &&
                intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun isImportIntent(intent: Intent?): Boolean {
        val comp = intent?.component ?: return false
        val info = packageManager.getActivityInfo(comp, PackageManager.GET_META_DATA)
        return info.metaData?.getString("import_type") != null
    }

    private fun areAllTasksFinished(): Boolean {
        return finishedTaskCount.get() >= unpackItems.size
    }
}