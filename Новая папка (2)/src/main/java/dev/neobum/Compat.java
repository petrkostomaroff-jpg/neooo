package dev.neobum;

import java.lang.reflect.Method;

/** Safe reflection helpers so a missing API method never breaks the whole plugin. */
final class Compat {
    private Compat() {}

    static void call(Object target, String method, Class<?> argType, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, argType);
            m.invoke(target, arg);
        } catch (Throwable ignored) {
            // method not present on this server version - feature is optional
        }
    }

    /** Lights a TNT minecart if it is not lit yet. */
    static void ignitePrimed(Object minecart) {
        try {
            Method isIgnited = minecart.getClass().getMethod("isIgnited");
            if (Boolean.TRUE.equals(isIgnited.invoke(minecart))) return;
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            minecart.getClass().getMethod("ignite").invoke(minecart);
            return;
        } catch (Throwable ignored) {
            // fall through
        }
        call(minecart, "setFuseTicks", int.class, 80);
    }
}
