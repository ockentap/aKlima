package com.example.aklima

import android.app.Application

class AKlimaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.record(this, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
