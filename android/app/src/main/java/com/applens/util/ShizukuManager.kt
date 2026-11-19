package com.applens.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object ShizukuManager {
    private val lock = Object()
    @Volatile private var appContext: Context? = null
    @Volatile private var userService: IAppLensUserService? = null
    @Volatile private var bindRequested = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = IAppLensUserService.Stub.asInterface(binder)
            synchronized(lock) {
                bindRequested = false
                lock.notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            synchronized(lock) {
                bindRequested = false
                lock.notifyAll()
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun checkPermission(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun requestPermission(requestCode: Int = 0) {
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    /** Execute a shell command via a Shizuku user service. */
    fun executeShell(command: String): String {
        val service = getUserService()
        return try {
            service.execute(command)
        } catch (e: RemoteException) {
            userService = null
            throw RuntimeException("Shizuku shell command failed: ${e.message}", e)
        }
    }

    private fun getUserService(): IAppLensUserService {
        userService?.let { return it }
        val context = appContext ?: throw IllegalStateException("ShizukuManager is not initialized")
        if (!isShizukuRunning()) throw IllegalStateException("Shizuku is not running")
        if (!checkPermission()) throw SecurityException("Shizuku permission has not been granted")

        synchronized(lock) {
            userService?.let { return it }
            if (!bindRequested) {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(context, ShizukuUserService::class.java)
                )
                    .daemon(false)
                    .processNameSuffix("applens")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE)
                Shizuku.bindUserService(args, connection)
                bindRequested = true
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (userService == null) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) {
                    bindRequested = false
                    throw IllegalStateException("Timed out waiting for the Shizuku user service")
                }
                TimeUnit.NANOSECONDS.timedWait(lock, remaining)
            }
            return userService!!
        }
    }

    fun uiautomatorDump(): String {
        val result = executeShell("uiautomator dump --compressed /dev/tty 2>/dev/null")
        return if (result.contains("<hierarchy")) result.trim()
        else executeShell("cat /sdcard/window_dump.xml 2>/dev/null").trim()
    }

    fun getCurrentActivity(): String = try {
        val result = executeShell("dumpsys activity activities | grep mResumedActivity")
        Regex("mResumedActivity.*?\\{.*?\\s(\\S+?)\\s").find(result)?.groupValues?.get(1) ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }

    fun forceStopApp(packageName: String) {
        executeShell("am force-stop $packageName")
    }

    fun launchApp(packageName: String) {
        executeShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_META_DATA
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
    }
}
