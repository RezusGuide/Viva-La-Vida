package dev.rezus.vivacore.compat;

import dev.rezus.vivacore.VivaCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Единственная точка входа Viva Core в Create: Chromeware (необязательная зависимость, тесты 33 и 36).
 * Viva Core не компилируется против Chromeware: {@link ChromewareBridge} находит его классы рефлексией,
 * и только если мод загружен. Если Chromeware обновится и класс/поле/метод пропадут — отвечаем «нет»
 * и один раз пишем в лог.
 * <p>
 * Что берём из Chromeware:
 * <ul>
 *   <li>кушетка операций — сущность-сиденье {@code chromeware:surgery_seat} (SurgerySeat extends Entity,
 *       на неё сажает SurgeryChairBlock через Surgery.seat → startRiding). Проверяется по id типа сущности
 *       из реестра — без классов Chromeware вообще;</li>
 *   <li>стоит ли любая оптика Kiroshi (все 7 вариантов из {@code KiroshiOptics.ALL}) — через мост на рефлексии.
 *       Слоты Chromeware — attachment игрока с синхронизацией владельцу, поэтому работает и на сервере,
 *       и на клиенте (для себя). Тест 33.</li>
 * </ul>
 */
public final class ChromewareCompat {
    private ChromewareCompat() {}

    public static final String MOD_ID = "chromeware";
    public static final ResourceLocation SURGERY_SEAT = ResourceLocation.fromNamespaceAndPath(MOD_ID, "surgery_seat");

    private static volatile boolean broken;

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /** Сущность — сиденье кушетки операций Chromeware. Работает и без Chromeware (тогда всегда false). */
    public static boolean isSurgerySeat(Entity entity) {
        return SURGERY_SEAT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    /**
     * Стоит ли у игрока любая оптика Kiroshi (для показа Живы, тест 33).
     * Без Chromeware или при несовместимой версии — false.
     */
    public static boolean hasCyberOptics(Player player) {
        if (broken || !isLoaded()) {
            return false;
        }
        try {
            return ChromewareBridge.hasCyberOptics(player);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            markBroken(e);
            return false;
        }
    }

    /** Сколько вариантов оптики нашлось в Chromeware (0 — мода нет или не совместим). Для тестов и статуса. */
    public static int opticsVariants() {
        if (broken || !isLoaded()) {
            return 0;
        }
        try {
            return ChromewareBridge.opticsCount();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            markBroken(e);
            return 0;
        }
    }

    private static void markBroken(Throwable e) {
        broken = true;
        VivaCore.LOGGER.warn("Chromeware: версия не совместима с Viva Core ({}), проверка импланта отключена", e.toString());
    }

    /** Для тестов: состояние «сломано». */
    public static boolean isBroken() {
        return broken;
    }
}
