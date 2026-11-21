package dev.debz.debugqs

import android.app.Application
import dev.debz.debugqs.core.Privileged
import rikka.sui.Sui

class DebugQsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Sui (Shizuku inside Magisk/KernelSU) binds without a provider; the Shizuku app
        // binds through the ShizukuProvider declared in the manifest.
        Sui.init(packageName)
        Privileged.start(this)
    }
}
