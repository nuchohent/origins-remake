package dev.raceapi.power;

/**
 * Re-entrancy guard for damage-reflection powers ({@code thorns},
 * {@code life_link}). Reflecting or redirecting damage calls {@code hurt()}
 * inside the victim's own {@code LivingIncomingDamageEvent} processing, so two
 * linked/matched players would bounce the damage back and forth in a nested
 * recursion. While the guard is held, further reflections/redirects caused by
 * that nested hit are suppressed.
 */
final class HurtGuard {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private HurtGuard() {
    }

    /** Returns true when the caller may reflect/redirect, false when already inside one. */
    static boolean tryEnter() {
        if (ACTIVE.get()) {
            return false;
        }
        ACTIVE.set(Boolean.TRUE);
        return true;
    }

    static void exit() {
        ACTIVE.set(Boolean.FALSE);
    }
}
