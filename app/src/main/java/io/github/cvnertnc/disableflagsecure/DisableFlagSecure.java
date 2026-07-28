package io.github.cvnertnc.disableflagsecure;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Arrays;

import io.github.libxposed.api.XposedModule;

@SuppressLint({"PrivateApi", "BlockedPrivateApi"})
public class DisableFlagSecure extends XposedModule {
    private static final String TAG = "DisableFlagSecure";

    private static XposedModule module;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        module = this;
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        // Only Android 11 (API 30) and below should work
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            return;
        }

        var classLoader = param.getClassLoader();

        // secureLocked flag (Android 11 and below)
        try {
            hookWindowState(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook WindowState failed", t);
        }

        // oplus dumpsys
        try {
            hookOplus(classLoader);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log(Log.ERROR, TAG, "hook Oplus failed", t);
            }
        }
    }

    @SuppressLint("PrivateApi")
    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;

        // Don't run if you're on Android 11
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) return;

        try {
            hookOnResume();
        } catch (Throwable ignored) {
        }
    }

    private void hookWindowState(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        var windowStateClazz = classLoader.loadClass("com.android.server.wm.WindowState");
        
        Method isSecureLockedMethod;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30)
            isSecureLockedMethod = windowStateClazz.getDeclaredMethod("isSecureLocked");
        } else {
            // Android 10 and below (API 29-)
            var windowManagerServiceClazz = classLoader.loadClass("com.android.server.wm.WindowManagerService");
            isSecureLockedMethod = windowManagerServiceClazz.getDeclaredMethod("isSecureLocked", windowStateClazz);
        }

        hook(isSecureLockedMethod).intercept(chain -> false);
        /*
         Chain interceptor her zaman 'false' döner.
         Thus, the system assumes that the window is not 'FLAG_SECURE' protected.
        */
    }

    private void hookOplus(ClassLoader classLoader) throws ClassNotFoundException {
        var longshotMainClazz = classLoader.loadClass("com.android.server.wm.OplusLongshotMainWindow");
        hookMethods(longshotMainClazz, chain -> false, "hasSecure");
    }

    private void hookMethods(Class<?> clazz, Hooker hooker, String... names) {
        var list = Arrays.asList(names);
        Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> list.contains(method.getName()))
                .forEach(method -> hook(method).intercept(hooker));
    }

    private void hookOnResume() throws NoSuchMethodException {
        var method = Activity.class.getDeclaredMethod("onResume");
        hook(method).intercept(chain -> {
            var activity = (Activity) chain.getThisObject();
            new AlertDialog.Builder(activity)
                    .setTitle("Enable Screenshot")
                    .setMessage("Incorrect module usage, remove this app from scope.")
                    .setCancelable(false)
                    .setPositiveButton("OK", (dialog, which) -> System.exit(0))
                    .show();
            return chain.proceed();
        });
    }
}
