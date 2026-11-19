package com.applens.util;

import android.os.RemoteException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;

/** Shizuku user service that executes shell operations as the Shizuku service user. */
public final class ShizukuUserService extends IAppLensUserService.Stub {

    public ShizukuUserService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String execute(String command) throws RemoteException {
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RemoteException("Shell command failed (" + exitCode + "): " + output);
            }
            return output.toString();
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("Unable to execute shell command", e);
        }
    }
}
