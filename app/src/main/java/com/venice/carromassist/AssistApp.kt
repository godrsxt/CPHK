package com.venice.carromassist

import android.app.Application

class AssistApp : Application() {
    companion object {
        lateinit var prefs: Prefs
            private set
    }
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }
}
