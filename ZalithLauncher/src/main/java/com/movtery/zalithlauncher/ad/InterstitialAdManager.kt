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
import android.util.Log
import com.umeng.union.UMUnionSdk
import com.umeng.union.api.UMAdConfig
import com.umeng.union.api.UMUnionApi

/**
 * 友盟插屏广告管理器（单例）。
 *
 * 管理插屏广告的加载和展示。插屏广告在应用从后台切回时展示。
 */
object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"

    /** 插屏广告位 ID */
    const val AD_UNIT_ID = "100012745"

    private var isLoading = false
    private var isShowing = false

    /**
     * 加载一则插屏广告。
     * 如果正在加载中或正在展示，则跳过本次请求。
     */
    fun loadAd(activity: Activity) {
        if (isLoading || isShowing) return

        isLoading = true
        val config = UMAdConfig.Builder()
            .setSlotId(AD_UNIT_ID)
            .build()

        UMUnionSdk.getApi().loadInterstitialAd(activity, config,
            object : UMUnionApi.AdLoadListener<UMUnionApi.AdDisplay> {
                override fun onSuccess(type: UMUnionApi.AdType, display: UMUnionApi.AdDisplay) {
                    isLoading = false
                    Log.i(TAG, "Interstitial ad loaded successfully")
                }

                override fun onFailure(type: UMUnionApi.AdType, message: String) {
                    isLoading = false
                    Log.w(TAG, "Interstitial ad load failed: $message")
                }
            }
        )
    }

    /**
     * 展示插屏广告（如果已加载）。
     *
     * @param activity 当前 Activity
     * @return `true` 如果广告已成功展示，`false` 如果广告不可用
     */
    fun showAdIfAvailable(activity: Activity): Boolean {
        if (isShowing) return false

        val config = UMAdConfig.Builder()
            .setSlotId(AD_UNIT_ID)
            .build()

        // 使用 load/show 分离模式
        UMUnionSdk.getApi().loadInterstitialAd(activity, config,
            object : UMUnionApi.AdLoadListener<UMUnionApi.AdDisplay> {
                override fun onSuccess(type: UMUnionApi.AdType, display: UMUnionApi.AdDisplay) {
                    if (isShowing) return
                    isShowing = true

                    display.setAdCloseListener { adType ->
                        Log.i(TAG, "Interstitial ad closed")
                        isShowing = false
                        // 预加载下一则
                        loadAd(activity)
                    }

                    display.setAdEventListener(object : UMUnionApi.AdEventListener {
                        override fun onExposed() {
                            Log.i(TAG, "Interstitial ad exposed")
                        }

                        override fun onClicked(view: android.view.View?) {
                            Log.i(TAG, "Interstitial ad clicked")
                        }

                        override fun onError(code: Int, message: String) {
                            Log.w(TAG, "Interstitial ad error: code=$code, msg=$message")
                            isShowing = false
                        }
                    })

                    display.show(activity)
                }

                override fun onFailure(type: UMUnionApi.AdType, message: String) {
                    Log.w(TAG, "Interstitial ad load failed on show: $message")
                }
            }
        )
        return true
    }
}