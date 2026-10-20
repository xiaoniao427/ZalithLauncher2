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

package com.movtery.zalithlauncher.push

import android.app.NotificationManager as SysNotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.movtery.zalithlauncher.notification.NotificationChannelData
import com.movtery.zalithlauncher.ui.activities.MainActivity
import org.json.JSONObject

/**
 * 友盟推送消息接收服务。
 *
 * 当推送消息到达时，友盟 SDK 会通过 Intent 调起此服务，
 * 负责解析消息内容并通过系统通知展示给用户。
 */
class PushMessageService : Service() {

    companion object {
        private const val TAG = "PushMessageService"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val body = intent.getStringExtra("body")
            val extras = intent.getStringExtra("extra")
                ?.let { JSONObject(it) }
                ?: JSONObject()

            // 解析消息
            val title = extras.optString("title", "消息推送")
            val text = if (body.isNullOrEmpty() && !extras.has("text")) {
                "收到一条新消息"
            } else {
                body ?: extras.optString("text", "收到一条新消息")
            }

            // 展示通知
            showNotification(title, text, extras)

            Log.i(TAG, "Push message received: title=$title, text=$text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process push message", e)
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun showNotification(title: String, text: String, extras: JSONObject) {
        val channelId = NotificationChannelData.PUSH_MESSAGE_CHANNEL.channelId
        val notificationId = NOTIFICATION_ID_BASE + System.currentTimeMillis().toInt()

        // 点击通知跳转到 MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 将 extra 信息传递到 MainActivity
            extras.optString("url")?.let { url -> putExtra("push_url", url) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as
            SysNotificationManager
        manager.notify(notificationId, builder.build())
    }
}