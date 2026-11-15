package com.example.ddlreminder

import android.app.Application
import com.example.ddlreminder.reminder.NotificationHelper
import com.example.ddlreminder.work.CleanupScheduler

class TodoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        CleanupScheduler.enqueuePeriodic(this)
    }
}
