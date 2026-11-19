package com.applens.util;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/** Java bridge for Shizuku's @JvmSynthetic process API, which Kotlin cannot call directly. */
public final class ShizukuProcessHelper {
    private ShizukuProcessHelper() {
    }

    public static ShizukuRemoteProcess startProcess(String[] command) throws Exception {
        return Shizuku.newProcess(command, null, null);
    }
}
