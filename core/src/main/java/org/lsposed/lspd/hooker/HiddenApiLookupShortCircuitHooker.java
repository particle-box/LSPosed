package org.lsposed.lspd.hooker;

import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Short-circuits reflective lookups of specific hidden APIs that cause
 * denial logs like "hiddenapi: Accessing hidden method ... (CorePlatformApi, domain=core-platform)".
 *
 * We intercept Class.getDeclaredMethod/getMethod for VMRuntime.setHiddenApiExemptions(String[])
 * and throw NoSuchMethodException before ART's AccessChecker runs, preventing the denial log
 * and letting callers fall back gracefully.
 */
public final class HiddenApiLookupShortCircuitHooker {
    private HiddenApiLookupShortCircuitHooker() {}

    private static volatile boolean installed;

    public static void installForCurrentProcess(boolean isSystemServer) {
        if (installed) return;
        if (isSystemServer) return; // don't alter system_server
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Method getMethod = Class.class.getDeclaredMethod("getMethod", String.class, Class[].class);

            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam<?> param) throws Throwable {
                    Class<?> target = (Class<?>) param.thisObject;
                    if (target == null) return;
                    String owner = target.getName();
                    if (!"dalvik.system.VMRuntime".equals(owner)) return;
                    String name = (String) param.args[0];
                    Class<?>[] params = (Class<?>[]) param.args[1];
                    if (name.equals("setHiddenApiExemptions")) {
                        // Pretend not found to avoid hiddenapi checks/logs
                        param.setThrowable(new NoSuchMethodException("setHiddenApiExemptions (blocked)"));
                        return;
                    }
                    if (equalsIgnoreCase(name, "SupressionGC")) {
                        param.setThrowable(new NoSuchMethodException("SupressionGC (blocked)"));
                        return;
                    }
                    if (equalsIgnoreCase(name, "DesupressionGC")) {
                        param.setThrowable(new NoSuchMethodException("DesupressionGC (blocked)"));
                        return;
                    }
                    if (name.equals("updateProcessValue")) {
                        param.setThrowable(new NoSuchMethodException("updateProcessValue (blocked)"));
                    }
                }
            };
            XposedBridge.hookMethod(getDeclaredMethod, hook);
            XposedBridge.hookMethod(getMethod, hook);
            installed = true;
        } catch (Throwable ignored) {
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
