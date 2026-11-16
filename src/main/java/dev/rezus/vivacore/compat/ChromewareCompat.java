package dev.rezus.vivacore.compat;

import dev.rezus.vivacore.VivaCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Единственная точка входа Viva Core в Create: Chromeware (необязательная зависимость, тесты 33 и 36).
 * В этом классе нет ни одной ссылки на классы Chromeware — их вызывает только {@link ChromewareBridge},
 * и только если мод загружен. Если Chromeware обновится и метод/поле пропадут, ловим LinkageError:
 * отвечаем «нет» и один раз пишем в лог.
 * <p>
 * Что берём из Chromeware:
 * <ul>
 *   <li>кушетка операций — сущность-сиденье {@code chromeware:surgery_seat} (SurgerySeat extends Entity,
 *       на неё сажает SurgeryChairBlock через Surgery.seat → startRiding). Проверяется по id типа сущности
 *       из реестра — без классов Chromeware вообще;</li>
 *   <li>стоит ли оптика Kiroshi — {@code Implant.KIROSHI_OPTICS.installed(player)} через мост. Слоты Chromeware —
 *       attachment игрока с синхронизацией владельцу, поэтому работает и на сервере, и на клиенте (для себя).</li>
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

    /** Стоит ли у игрока оптика Kiroshi. Без Chromeware или при несовместимой версии — false. */
    public static boolean hasKiroshiOptics(Player player) {
        if (broken || !isLoaded()) {
            return false;
        }
        try {
            return ChromewareBridge.hasKiroshiOptics(player);
        } catch (LinkageError e) {
            broken = true;
            VivaCore.LOGGER.warn("Chromeware: версия не совместима с Viva Core ({}), проверка импланта отключена", e.toString());
            return false;
        }
    }

    /** Для тестов: состояние «сломано». */
    public static boolean isBroken() {
        return broken;
    }
}
