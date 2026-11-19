/*
 * SPDX-FileCopyrightText: 2026 Gallbladderz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */



package com.gallbladderz.openkick

import android.app.Application
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.gallbladderz.openkick.di.appModule
import com.gallbladderz.openkick.features.notifications.StreamCheckWorker
import okhttp3.OkHttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import java.io.File
import java.util.concurrent.TimeUnit

class OpenKickApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        setupCrashLog()
        startKoin {
            androidContext(this@OpenKickApp)
            workManagerFactory()
            modules(appModule)
        }

        setupStreamNotifications()
    }

    /** 未捕获异常写入外部私有目录 logs/，文件管理器可取：Android/data/<pkg>/files/logs/ */
    private fun setupCrashLog() {
        val dir = getExternalFilesDir("logs") ?: return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
                file.writeText(
                    "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE}\n" +
                        "Thread: ${thread.name}\n\n" +
                        android.util.Log.getStackTraceString(throwable)
                )
                // 只保留最近 5 份
                dir.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun setupStreamNotifications() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<StreamCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StreamCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                coil.disk.DiskCache.Builder().directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05).build()
            }
            .okHttpClient { get<OkHttpClient>() }
            .crossfade(true)
            .build()
    }
}
