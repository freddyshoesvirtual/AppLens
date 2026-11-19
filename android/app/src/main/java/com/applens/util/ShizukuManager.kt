package com.applens.util

import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return if (Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int = 0) {
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    /** Execute a shell command via Shizuku with ADB-level privileges. */
    fun executeShell(command: String): String {
        val process = ShizukuProcessHelper.startProcess(arrayOf("sh", "-c", command))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }
        val errorOutput = StringBuilder()
        while (errorReader.readLine().also { line = it } != null) {
            errorOutput.appendLine(line)
        }
        process.waitFor()
        if (process.exitValue() != 0 && errorOutput.isNotEmpty()) {
            throw RuntimeException("Shell error: $errorOutput")
        }
        return output.toString()
    }

    /** Run uiautomator dump on the current screen. */
    fun uiautomatorDump(): String {
        val result = executeShell("uiautomator dump --compressed /dev/tty 2>/dev/null")
        return if (result.contains("<hierarchy")) {
            result.trim()
        } else {
            executeShell("cat /sdcard/window_dump.xml 2>/dev/null").trim()
        }
    }

    /** Get the current foreground activity name. */
    fun getCurrentActivity(): String {
        return try {
            val result = executeShell("dumpsys activity activities | grep mResumedActivity")
            val regex = Regex("mResumedActivity.*?\\{.*?\\s(\\S+?)\\s")
            regex.find(result)?.groupValues?.get(1) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun forceStopApp(packageName: String) {
        executeShell("am force-stop $packageName")
    }

    fun launchApp(packageName: String) {
        executeShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_META_DATA
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_META_DATA
            )
        }
    }
}
