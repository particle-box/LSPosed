package org.lsposed.lspd.hooker;

import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Intercepts reflective invocations to certain hidden APIs that are denied
 * for app-domain processes on Android 15+, to avoid hiddenapi denial spam.
 */
public final class HiddenApiInvokeNoopHooker {
    private HiddenApiInvokeNoopHooker() {}

    private static volatile boolean installed;

    public static void installForCurrentProcess(boolean isSystemServer) {
        if (installed) return;
        if (isSystemServer) return; // allow in system_server
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return; // Android 15+
        try {
            Method invoke = XposedHelpers.findMethodExact(Method.class, "invoke", Object.class, Object[].class);
            XposedBridge.hookMethod(invoke, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam<?> param) {
                    Method target = (Method) param.thisObject;
                    if (target == null) return; // shouldn't happen
                    String owner = target.getDeclaringClass().getName();
                    String name = target.getName();
                    if ("dalvik.system.VMRuntime".equals(owner)) {
                        if ("setHiddenApiExemptions".equals(name)
                            || equalsIgnoreCase(name, "SupressionGC")
                            || equalsIgnoreCase(name, "DesupressionGC")
                            || equalsIgnoreCase(name, "updateProcessValue")) {
                            // Short-circuit: do nothing to avoid denial/noise
                            param.setResult(null);
                        }
                    }
                }
            });
            installed = true;
        } catch (Throwable ignored) {
        }
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
