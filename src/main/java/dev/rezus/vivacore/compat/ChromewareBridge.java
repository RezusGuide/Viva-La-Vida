package dev.rezus.vivacore.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;

/**
 * Вызовы Create: Chromeware 1.3.0 через рефлексию — Viva Core не компилируется против Chromeware.
 * Классы и методы ищутся один раз (при первом вызове), результат кешируется в static-полях.
 * Вызывается только из {@link ChromewareCompat} и только если мод загружен.
 * <p>
 * Хрупкие места (при обновлении Chromeware сверить):
 * <ul>
 *   <li>{@code com.chromeware.cyberware.KiroshiOptics.ALL} — список всех 7 вариантов оптики Kiroshi;</li>
 *   <li>запасной путь, если ALL нет: enum {@code com.chromeware.cyberware.Implant} и имена вариантов в {@link #OPTICS_NAMES};</li>
 *   <li>{@code Implant#installed(Player)}.</li>
 * </ul>
 */
final class ChromewareBridge {
    private ChromewareBridge() {}

    static final String IMPLANT_CLASS = "com.chromeware.cyberware.Implant";
    static final String OPTICS_CLASS = "com.chromeware.cyberware.KiroshiOptics";
    /** Варианты оптики Kiroshi в Chromeware 1.3.0 (тот же состав, что KiroshiOptics.ALL). */
    static final List<String> OPTICS_NAMES = List.of(
            "KIROSHI_OPTICS", "THE_ORACLE", "STALKER", "SENTRY", "DOOMSAYER", "COCKATRICE", "CLAIRVOYANT");

    private static boolean resolved;
    private static List<Object> optics = List.of();
    private static Method installed;

    /** Ищет классы и методы один раз. Бросает ReflectiveOperationException / LinkageError, если не нашлось. */
    private static synchronized void resolve() throws ReflectiveOperationException {
        if (resolved) {
            return;
        }
        ClassLoader loader = ChromewareBridge.class.getClassLoader();
        Class<?> implant = Class.forName(IMPLANT_CLASS, true, loader);
        Method installedMethod = implant.getMethod("installed", Player.class);

        List<Object> found = new ArrayList<>();
        try {
            Class<?> opticsClass = Class.forName(OPTICS_CLASS, true, loader);
            for (Object o : (List<?>) opticsClass.getField("ALL").get(null)) {
                if (implant.isInstance(o)) found.add(o);
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            // нет списка ALL — берём варианты по именам enum
            for (Object constant : implant.getEnumConstants()) {
                if (OPTICS_NAMES.contains(((Enum<?>) constant).name())) found.add(constant);
            }
        }
        if (found.isEmpty()) {
            throw new NoSuchFieldException("в Chromeware не нашлось ни одного варианта оптики Kiroshi");
        }
        optics = List.copyOf(found);
        installed = installedMethod;
        resolved = true;
    }

    /** Стоит ли у игрока любой из вариантов оптики Kiroshi. */
    static boolean hasCyberOptics(Player player) throws ReflectiveOperationException {
        resolve();
        for (Object optic : optics) {
            if ((Boolean) installed.invoke(optic, player)) {
                return true;
            }
        }
        return false;
    }

    /** Сколько вариантов оптики нашлось (для тестов). */
    static int opticsCount() throws ReflectiveOperationException {
        resolve();
        return optics.size();
    }
}
