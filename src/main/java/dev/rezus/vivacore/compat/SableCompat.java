package dev.rezus.vivacore.compat;

import dev.rezus.vivacore.VivaCore;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Sable (корабли) — необязательная зависимость, только рефлексией (тест 25, задача 9). Viva Core не компилируется
 * против Sable.
 * <p>
 * Блоки кораблей живут в «участках» (plot) далеко от места корабля, и {@code level.clip}, который подменяет Sable,
 * возвращает точку попадания в координатах участка. В мир её переводит API, которым пользуется сам Sable (его миксин
 * взрывов {@code ServerLevelMixin#sable$preExplode} делает то же):
 * {@code dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(Level, Vec3)} — библиотека
 * sable-companion, вложена в jar Sable. Для точки вне корабля возвращает её же.
 * <p>
 * Хрупкие места при обновлении Sable: имя интерфейса, поле INSTANCE, метод projectOutOfSubLevel(Level, Vec3).
 * Не нашлось → точка не переводится, одна строка в логе.
 */
public final class SableCompat {
    private SableCompat() {}

    static final String COMPANION_CLASS = "dev.ryanhcode.sable.companion.SableCompanion";

    private static volatile boolean resolved;
    private static Object companion;
    private static Method project;

    /** Быстрый путь без блокировки — вызывается на каждый выстрел. */
    private static boolean ready() {
        return resolved ? project != null : resolve();
    }

    private static synchronized boolean resolve() {
        if (resolved) return project != null;
        try {
            if (!ModList.get().isLoaded("sable")) return false;
            Class<?> c = Class.forName(COMPANION_CLASS, true, SableCompat.class.getClassLoader());
            Object instance = c.getField("INSTANCE").get(null);
            Method m = c.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            companion = instance;
            project = m;
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            VivaCore.LOGGER.warn("Viva Core: Sable загружен, но API перевода координат не найдено ({}) — точки попаданий в корабли не переводятся", e.toString());
            return false;
        } finally {
            resolved = true; // после записи полей (volatile — видно другим потокам вместе с ними)
        }
    }

    /** Точка в мировых координатах: если она в участке корабля Sable — переводит в мир, иначе возвращает как есть. */
    public static Vec3 toWorld(Level level, Vec3 pos) {
        if (!ready()) return pos;
        try {
            Object r = project.invoke(companion, level, pos);
            return r instanceof Vec3 v ? v : pos;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return pos;
        }
    }

    /** Для тестов: скорость корабля в точке (блоков/с), или null без Sable. */
    public static Vec3 velocity(Level level, Vec3 pos) {
        if (!ready()) return null;
        try {
            Object r = companion.getClass().getMethod("getVelocity", Level.class, Vec3.class).invoke(companion, level, pos);
            return r instanceof Vec3 v ? v : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Для тестов: API Sable найдено. */
    public static boolean available() {
        return ready();
    }
}
