package com.example

import android.app.Application
import com.example.utils.GlobalConsoleLogger

class BudgetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Setup Global Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            GlobalConsoleLogger.e(
                "CRASH",
                "Uncaught exception on thread ${thread.name}: ${throwable.localizedMessage}\n${throwable.stackTraceToString()}"
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }

        GlobalConsoleLogger.i("SYSTEM", "BudgetApplication initialized successfully.")
    }
}
