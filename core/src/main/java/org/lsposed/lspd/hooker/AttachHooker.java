package org.lsposed.lspd.hooker;

import android.app.ActivityThread;

import de.robv.android.xposed.XposedInit;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@XposedHooker
public class AttachHooker implements XposedInterface.Hooker {

    @AfterInvocation
    public static void afterHookedMethod(XposedInterface.AfterHookCallback callback) {
        XposedInit.loadModules((ActivityThread) callback.getThisObject());
        // Install vendor reflection guards (best-effort, Android 15+ only)
        try {
            VendorHiddenApiWorkaroundHooker.installForCurrentProcess();
        } catch (Throwable ignored) {
        }
        try {
            HiddenApiInvokeNoopHooker.installForCurrentProcess(de.robv.android.xposed.XposedInit.startsSystemServer);
        } catch (Throwable ignored) {
        }
        try {
            HiddenApiLookupShortCircuitHooker.installForCurrentProcess(de.robv.android.xposed.XposedInit.startsSystemServer);
        } catch (Throwable ignored) {
        }
        try {
            OplusGcNoiseMitigator.installForCurrentProcess();
        } catch (Throwable ignored) {
        }
    }
}
