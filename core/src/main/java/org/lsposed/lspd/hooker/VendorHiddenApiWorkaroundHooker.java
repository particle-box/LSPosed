package org.lsposed.lspd.hooker;

import static de.robv.android.xposed.XposedBridge.hookMethod;

import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Best-effort guards for OEM/framework reflection helpers that spam logs or NPE
 * when hidden APIs are blocked on Android 15. We install null-check wrappers
 * around utility methods that accept java.lang.reflect members, so a null Method
 * or Field won't cause a crash/noisy stacktraces during transitions.
 *
 * Scope is intentionally narrow: only runs on Android 15+ and only touches
 * com.android.wm.shell.util.ReflectionUtils if present in the process.
 */
public final class VendorHiddenApiWorkaroundHooker {
    private VendorHiddenApiWorkaroundHooker() {}

    public static void installForCurrentProcess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return; // Android 15+
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        tryHookReflectionUtils(cl);
    }

    private static void tryHookReflectionUtils(ClassLoader cl) {
        final Class<?> clazz = XposedHelpers.findClassIfExists("com.android.wm.shell.util.ReflectionUtils", cl);
        if (clazz == null) return;
        for (Method m : clazz.getDeclaredMethods()) {
            final Class<?>[] p = m.getParameterTypes();
            boolean takesReflectMember = Arrays.stream(p).anyMatch(pt ->
                pt == Method.class || pt == Constructor.class || pt == java.lang.reflect.Field.class);
            if (!takesReflectMember) continue;
            // Wrap calls: if any reflect member arg is null, short-circuit with a default value
            hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam<?> param) {
                    for (Object arg : param.args) {
                        if (arg == null) {
                            param.setResult(defaultValue(m.getReturnType()));
                            return;
                        }
                    }
                }
            });
        }
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
