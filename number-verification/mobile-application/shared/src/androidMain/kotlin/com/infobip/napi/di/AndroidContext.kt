package com.infobip.napi.di

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object AndroidContext {
    lateinit var applicationContext: Context

    /** CredentialManager shows its consent sheet over an Activity, so the current one is kept here. */
    private var currentActivity: WeakReference<Activity>? = null

    val activity: Activity?
        get() = currentActivity?.get()

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun attachActivity(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (currentActivity?.get() === activity) currentActivity = null
    }
}
