package org.lsposed.lspd.hooker;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Best-effort no-op for OEM GC suppression helpers which reflect into
 * VMRuntime methods that are missing on A15, causing NPE/log spam.
 * Attempts several likely class names and silently skips if not present.
 */
public final class OplusGcNoiseMitigator {
    private OplusGcNoiseMitigator() {}

    private static volatile boolean installed;

    private static final String[] CANDIDATE_CLASSES = new String[] {
            // Observed/likely helpers on OPlus/OnePlus devices
            "com.android.wm.shell.util.OplusTransitionReflectionHelper",
            "com.oplus.wmshell.util.OplusTransitionReflectionHelper",
            "com.oplus.transition.OplusTransitionReflectionHelper",
            "android.app.OplusActivityThreadExtImpl",
            "com.oplus.app.OplusActivityThreadExtImpl",
            "com.oplus.os.OplusActivityThreadExtImpl",
            "com.oplus.appheap.OplusAppHeapManager",
            "com.oplus.app.OplusAppHeapManager",
            "com.oplus.os.OplusAppHeapManager",
            "com.heytap.os.OplusAppHeapManager"
    };

    public static void installForCurrentProcess() {
        if (installed) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String fqcn : CANDIDATE_CLASSES) {
            Class<?> clazz = XposedHelpers.findClassIfExists(fqcn, cl);
            if (clazz == null) continue;
            hookSuppressMethods(clazz);
        }
        installed = true;
    }

    private static void hookSuppressMethods(Class<?> clazz) {
        List<Method> toHook = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            String n = m.getName();
            if (containsIgnoreCase(n, "Supression") || containsIgnoreCase(n, "Desupression")
                    || containsIgnoreCase(n, "SuppressGc") || containsIgnoreCase(n, "DesuppressGc")
                    || containsIgnoreCase(n, "GcSuppress") || containsIgnoreCase(n, "GcDesuppress")) {
                toHook.add(m);
            }
        }
        for (Method m : toHook) {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam<?> param) {
                    // No-op these helpers to avoid triggering missing/hidden APIs
                    param.setResult(defaultValue(m.getReturnType()));
                }
            });
        }
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
