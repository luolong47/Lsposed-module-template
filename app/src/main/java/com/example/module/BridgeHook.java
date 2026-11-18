package com.example.module;

import android.content.ComponentName;
import android.util.Log;


import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import java.lang.reflect.Method;

/**
 * Chastify device-owner bridge.
 *
 * <p>Runs inside {@code system_server} and makes the system's
 * {@code DevicePolicyManagerService} treat the Chastify app as the device
 * owner (and profile owner) for its own calls. This lets Chastify's
 * DevicePolicyManager requests — e.g. {@code addUserRestriction} — actually be
 * applied by the system, without a second app (Owndroid) and without really
 * provisioning Chastify as device owner.</p>
 *
 * <p>Scope must be {@code system}. Chastify itself still has to be enabled as a
 * normal device-admin (Settings &rarr; device admin apps); this module only
 * fakes the elevation from admin to owner.</p>
 *
 * <p>The ownership checks in {@code DevicePolicyManagerService} are internal,
 * non-SDK and version-specific, so the methods are located reflectively at
 * runtime instead of being referenced at compile time.</p>
 */
public class BridgeHook extends XposedModule {

    private static final String TAG = "ChastifyBridge";

    private static final String CHASTIFY_PKG = "net.chastify.app";

    private static final String DPMS_CLASS =
            "com.android.server.devicepolicy.DevicePolicyManagerService";
    private static final String CALLER_IDENTITY_CLASS =
            "com.android.server.devicepolicy.CallerIdentity";

    public BridgeHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        final Class<?> dpms;
        try {
            dpms = param.getClassLoader().loadClass(DPMS_CLASS);
        } catch (ClassNotFoundException e) {
            log(Log.ERROR, TAG, "DevicePolicyManagerService not found, aborting", e);
            return;
        }

        int hooked = 0;
        for (Method m : dpms.getDeclaredMethods()) {
            final String name = m.getName();
            if (!name.equals("isDeviceOwner") && !name.equals("isProfileOwner")) {
                continue;
            }
            // Only hook overloads whose arguments let us identify the caller:
            // a CallerIdentity, or a ComponentName (the app's admin component).
            if (!hasIdentifiableCaller(m)) {
                continue;
            }
            try {
                hook(m).intercept(OwnerHooker.INSTANCE);
                hooked++;
                log(Log.INFO, TAG, "hooked " + name
                        + "(" + paramNames(m) + ")");
            } catch (Throwable t) {
                log(Log.WARN, TAG, "failed to hook " + name, t);
            }
        }
        log(Log.INFO, TAG, "installed " + hooked + " owner-check hook(s) for " + CHASTIFY_PKG);
    }

    private static boolean hasIdentifiableCaller(Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            if (p == ComponentName.class) {
                return true;
            }
            if (CALLER_IDENTITY_CLASS.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String paramNames(Method m) {
        final StringBuilder sb = new StringBuilder();
        final Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ps[i].getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Returns {@code true} from an ownership check whenever the caller in that
     * particular call is Chastify; otherwise lets the original method run, so no
     * other app or system component is affected.
     */
    private static final class OwnerHooker implements XposedInterface.Hooker {

        static final OwnerHooker INSTANCE = new OwnerHooker();

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            for (Object arg : chain.getArgs()) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof ComponentName) {
                    if (CHASTIFY_PKG.equals(((ComponentName) arg).getPackageName())) {
                        return Boolean.TRUE;
                    }
                } else if (CALLER_IDENTITY_CLASS.equals(arg.getClass().getName())) {
                    if (CHASTIFY_PKG.equals(callerPackage(arg))) {
                        return Boolean.TRUE;
                    }
                }
            }
            return chain.proceed();
        }

        private static String callerPackage(Object callerIdentity) {
            try {
                final Method gp = callerIdentity.getClass().getMethod("getPackageName");
                final Object r = gp.invoke(callerIdentity);
                return r == null ? null : r.toString();
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
