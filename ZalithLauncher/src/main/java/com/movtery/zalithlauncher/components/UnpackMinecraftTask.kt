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

package com.movtery.zalithlauncher.components

import android.content.Context
import com.movtery.zalithlauncher.context.copyAssetFile
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.file.readString
import com.movtery.zalithlauncher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileInputStream

private const val TAG = "UnpackMinecraftTask"

/**
 * 负责将 assets/minecraft 目录下的所有资源递归复制到
 * 应用私有目录 files/.minecraft 下，并通过 version 文件进行版本比对。
 *
 * 注意：assets 端使用 "minecraft"（不带点前缀，否则会被 AAPT 忽略），
 * 目标端使用 ".minecraft"（Minecraft 约定的隐藏目录名）。
 *
 * 首次安装或版本不一致时，将清空目标目录并完整复制。
 */
class UnpackMinecraftTask(private val context: Context) : AbstractUnpackTask() {
    companion object {
        const val ASSETS_DIR_NAME = "minecraft"
        const val TARGET_DIR_NAME = ".minecraft"
        const val VERSION_FILE_NAME = "version"
    }

    private val assetsDirName = ASSETS_DIR_NAME
    private val targetDir: File = File(PathManager.DIR_FILES_EXTERNAL, TARGET_DIR_NAME)
    private val versionFile: File = File(targetDir, VERSION_FILE_NAME)

    private val isCheckFailed: Boolean
    private var assetVersion: String? = null

    init {
        assetVersion = runCatching {
            context.assets.open("$assetsDirName/$VERSION_FILE_NAME").use { it.readString() }
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to init asset version. path=$assetsDirName/$VERSION_FILE_NAME", e)
        }.getOrNull()
        isCheckFailed = assetVersion == null
    }

    fun isCheckFailed() = isCheckFailed

    override fun checkState(): InstallableItem.State {
        if (isCheckFailed) return InstallableItem.State.NOT_EXISTS

        val currentVersion = assetVersion!!

        return if (!versionFile.exists()) {
            requestEmptyParentDir(versionFile)
            Logger.info(TAG, ".minecraft: Not installed yet, will extract...")
            InstallableItem.State.NOT_STARTED
        } else {
            runCatching {
                val installedVersion = FileInputStream(versionFile).use { it.readString() }
                if (currentVersion != installedVersion) {
                    requestEmptyParentDir(versionFile)
                    Logger.info(TAG, ".minecraft: Version mismatch (installed=$installedVersion, asset=$currentVersion), will update...")
                    InstallableItem.State.PENDING
                } else {
                    Logger.info(TAG, ".minecraft: Already up-to-date, skipping...")
                    InstallableItem.State.FINISHED
                }
            }.onFailure { e ->
                Logger.error(TAG, "Failed to check .minecraft version.", e)
            }.getOrElse {
                InstallableItem.State.NOT_STARTED
            }
        }
    }

    override suspend fun run() {
        try {
            val target = targetDir
            FileUtils.deleteDirectory(target)
            target.mkdirs()

            val assetFilePaths = listAssetTree("$assetsDirName")
            var count = 0
            for (assetPath in assetFilePaths) {
                val relativePath = assetPath.removePrefix("$assetsDirName/")
                val destFile = File(target, relativePath)
                updateMessage("Extracting: $relativePath")
                context.copyAssetFile(
                    fileName = assetPath,
                    output = destFile,
                    overwrite = true
                )
                count++
            }
            Logger.info(TAG, ".minecraft: Extracted $count files to ${target.absolutePath}")
        } catch (e: Exception) {
            Logger.error(TAG, ".minecraft extraction failed.", e)
            throw e
        }
    }

    /**
     * 递归列出 assets 目录下所有文件（不包括目录本身）。
     */
    private fun listAssetTree(path: String): List<String> {
        val result = mutableListOf<String>()
        val list = context.assets.list(path) ?: return result
        for (name in list) {
            val childPath = "$path/$name"
            val subList = context.assets.list(childPath)
            if (subList != null && subList.isNotEmpty()) {
                result.addAll(listAssetTree(childPath))
            } else {
                result.add(childPath)
            }
        }
        return result
    }

    private fun requestEmptyParentDir(file: File) {
        file.parentFile?.apply {
            if (exists() && isDirectory) {
                FileUtils.deleteDirectory(this)
            }
            mkdirs()
        }
    }
}