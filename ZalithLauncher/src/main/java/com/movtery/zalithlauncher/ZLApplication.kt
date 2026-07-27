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

package com.movtery.zalithlauncher

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.kyant.fishnet.Fishnet
import com.movtery.zalithlauncher.context.refreshContext
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.path.GamePathManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.loadAllSettings
import com.movtery.zalithlauncher.ui.activities.showFatalError
import com.movtery.zalithlauncher.ui.activities.showLauncherCrash
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.writeCrashFile
import com.tencent.mmkv.MMKV
import com.umeng.commonsdk.UMConfigure
import com.umeng.message.PushAgent
import com.umeng.message.api.UPushRegisterCallback
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import kotlin.properties.Delegates

@HiltAndroidApp
class ZLApplication : Application(), SingletonImageLoader.Factory {
    companion object {
        private const val TAG = "ZLApplication"

        @JvmStatic
        var DEVICE_ARCHITECTURE by Delegates.notNull<Int>()

        @JvmStatic
        var instance: ZLApplication? = null
            private set

        const val UMENG_APPKEY = "6a65eb27d679c36d9c7dc574"
        const val UMENG_CHANNEL = "GitHub"
        const val UMENG_MESSAGE_SECRET = "4890e5af9c2530d9b72215b5e3015979"

        /** 当前设备的推送 Device Token，注册成功后会更新 */
        @JvmStatic
        var deviceToken: String? = null
            private set

        /** Device Token 存储文件名 */
        const val DEVICE_TOKEN_FILE = "device_token.txt"

        /**
         * 获取当前 Device Token。
         * 优先返回内存中的值，其次从本地文件读取。
         */
        @JvmStatic
        fun getDeviceToken(context: Context): String? {
            // 优先返回内存中的最新token
            deviceToken?.let { return it }
            // 回退：从本地文件读取
            return readDeviceTokenFromFile(context)
        }

        fun initUmeng(context: Context) {
            UMConfigure.init(
                context,
                UMENG_APPKEY,
                UMENG_CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                UMENG_MESSAGE_SECRET
            )

            val pushAgent = PushAgent.getInstance(context)
            pushAgent.register(object : UPushRegisterCallback {
                override fun onSuccess(token: String) {
                    deviceToken = token
                    Log.i(TAG, "Push registration success, deviceToken: $token")
                    writeDeviceTokenToFile(context, token)
                }

                override fun onFailure(errCode: String, errDesc: String) {
                    Log.e(TAG, "Push registration failed! code: $errCode, desc: $errDesc")
                }
            })
        }

        private fun writeDeviceTokenToFile(context: Context, token: String) {
            try {
                File(context.filesDir, DEVICE_TOKEN_FILE).bufferedWriter().use { writer ->
                    writer.write("Device Token (Push notification unique identifier)\n")
                    writer.write("deviceToken：$token\n")
                    Log.i(TAG, "Device token written to file: ${context.filesDir}/$DEVICE_TOKEN_FILE")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write device token to file", e)
            }
        }

        /**
         * 从本地文件读取 Device Token
         */
        private fun readDeviceTokenFromFile(context: Context): String? {
            return try {
                val file = File(context.filesDir, DEVICE_TOKEN_FILE)
                if (!file.exists()) return null
                file.readLines().firstOrNull { it.startsWith("deviceToken：") }
                    ?.removePrefix("deviceToken：")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read device token from file", e)
                null
            }
        }
    }

    override fun onCreate() {
        instance = this

        refreshContext(this)

        // 友盟预初始化（合规要求：必须在 super.onCreate 之前，不采集设备信息）
        UMConfigure.preInit(this, UMENG_APPKEY, UMENG_CHANNEL)

        Thread.setDefaultUncaughtExceptionHandler { _, th ->
            //停止所有任务
            TaskSystem.stopAll()

            val throwable = if (th is SplashException) th.cause!!
            else th

            Logger.error("Startup", "An exception occurred", throwable)

            writeCrashFile(
                file = PathManager.FILE_CRASH_REPORT,
                throwable = throwable
            ) { t ->
                Logger.error("AppCrash", "An exception occurred while saving the crash report", t)
            }

            showLauncherCrash(this@ZLApplication, throwable, th !is SplashException)
            Process.killProcess(Process.myPid())
        }

        super.onCreate()

        UMConfigure.setLogEnabled(true)

        runCatching {
            Fishnet.init(this, PathManager.DIR_NATIVE_LOGS.absolutePath)

            MMKV.initialize(this)
            loadAllSettings(this)

            Logger.initialize(this)

            initializeData()
            PathManager.DIR_FILES_PRIVATE = getDir("files", MODE_PRIVATE)
            DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture()
            //Force x86 lib directory for Asus x86 based zenfones
            if (Architecture.isx86Device() && Architecture.is32BitsDevice) {
                val originalJNIDirectory = applicationInfo.nativeLibraryDir
                applicationInfo.nativeLibraryDir = originalJNIDirectory.take(originalJNIDirectory.lastIndexOf("/")) + "/x86"
            }
        }.onFailure { launchTh ->
            writeCrashFile(
                file = PathManager.FILE_CRASH_REPORT,
                throwable = launchTh
            ) {
                Log.w("ZLApplication", "An exception occurred while saving the crash report", it)
            }
            showFatalError(this, launchTh)
        }

        // 友盟正式初始化 + 推送注册（建议在子线程中执行）
        Thread {
            initUmeng(this)
        }.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshContext(this)
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(20L * 1024 * 1024) // 20MB
                    .weakReferencesEnabled(true) //弱引用
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB
                    .directory(PathManager.DIR_IMAGE_CACHE.toOkioPath())
                    .build()
            }
            .components {
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    private fun initializeData() {
        AccountsManager.initialize(this)
        GamePathManager.initialize(this)
    }
}
