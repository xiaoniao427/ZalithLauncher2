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

package com.movtery.zalithlauncher.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.Date

/**
 * AdMob GMA Next-Gen 开屏广告管理器（单例）。
 *
 * 管理开屏广告的加载、展示、过期检查。广告在 4 小时后过期，展示前需重新加载。
 * API 对齐 Google 官方示例：
 * https://github.com/googleads/gma-next-gen-sdk-android-examples
 */
object AppOpenAdManager {
    private const val TAG = "AppOpenAdManager"

    /** 开屏广告单元 ID */
    const val AD_UNIT_ID = "ca-app-pub-4002076249242835/9269005994"

    /** 广告加载后 4 小时过期（单位：毫秒） */
    private const val AD_EXPIRATION_MS = 4L * 60 * 60 * 1000

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
        private set

    private var loadTime: Long = 0L

    /**
     * 加载一则开屏广告。
     * 如果当前已有未使用的广告（未过期）或正在加载中，则跳过本次请求。
     *
     * @param context 用于 UI 相关操作（如 Toast）。加载广告本身不需要 context。
     */
    fun loadAd(context: Context) {
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        AppOpenAd.load(
            AdRequest.Builder(AD_UNIT_ID).build(),
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.i(TAG, "App open ad loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.w(TAG, "App open ad failed to load: $loadAdError")
                }
            }
        )
    }

    /**
     * 在当前 [Activity] 中展示开屏广告。
     *
     * @param activity 当前 Activity
     * @param onShowAdCompleteListener 广告完成回调（关闭/失败后触发）
     * @return `true` 如果广告已成功展示，`false` 如果广告不可用
     */
    fun showAdIfAvailable(activity: Activity, onShowAdCompleteListener: (() -> Unit)?): Boolean {
        if (isShowingAd) {
            onShowAdCompleteListener?.invoke()
            return false
        }

        if (!isAdAvailable()) {
            onShowAdCompleteListener?.invoke()
            return false
        }

        appOpenAd?.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.i(TAG, "App open ad showed")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.i(TAG, "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                onShowAdCompleteListener?.invoke()
                // 预加载下一则广告
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.w(TAG, "App open ad failed to show: $error")
                appOpenAd = null
                isShowingAd = false
                onShowAdCompleteListener?.invoke()
                loadAd(activity)
            }

            override fun onAdImpression() {
                Log.d(TAG, "App open ad recorded an impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "App open ad recorded a click")
            }
        }

        isShowingAd = true
        appOpenAd?.show(activity)
        return true
    }

    /** 检查广告是否在 4 小时内加载（未过期） */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3_600_000L
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /**
     * 检查当前是否有可用的开屏广告（非空且未过期）。
     * 开屏广告会在 4 小时后过期。
     */
    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }
}