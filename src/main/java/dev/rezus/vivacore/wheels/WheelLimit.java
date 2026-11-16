package dev.rezus.vivacore.wheels;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.rezus.vivacore.VivaConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Лимит водяных колёс Create: в одной кинетической сети работают не больше N колёс
 * (конфиг wheels.maxPerNetwork). Заметка «13 Мир, спавнер, колёса», тест 17 в «09 Тесты».
 * <p>
 * Как считаем. Кандидаты — колёса сети, у которых есть поток воды (flowScore != 0), только из загруженных
 * чанков (Create держит в {@link KineticNetwork#members} только загруженные блоки). Порядок — по времени
 * первого тика колеса (сохраняется в NBT колеса), при равенстве — по позиции. Первые N работают,
 * остальные «лишние»: getGeneratedSpeed() = 0, и Create сам переводит их в режим вала.
 * <p>
 * Решение зависит только от состава сети и сохранённого порядка, поэтому оно стабильно: перезапуск
 * и выгрузка чанков набор не меняют (флаг «лишнее» тоже сохраняется в NBT), колёса не мигают.
 * Мир не обходим: каждое колесо раз в checkIntervalTicks смотрит только свою сеть, список кандидатов
 * сети кешируется на текущий тик.
 */
public final class WheelLimit {
    private WheelLimit() {}

    private static final Comparator<WaterWheelBlockEntity> ORDER = Comparator
            .comparingLong((WaterWheelBlockEntity w) -> ((VivaWaterWheel) w).vivacore$order())
            .thenComparingLong(w -> w.getBlockPos().asLong());

    /** Кеш кандидатов сети на один тик: сеть → (тик, отсортированный список). Только серверный поток. */
    private static final Map<KineticNetwork, Cached> CACHE = new WeakHashMap<>();

    private record Cached(long gameTime, List<WaterWheelBlockEntity> ordered) {}

    /** Вызывается в конце тика колеса (миксин в GeneratingKineticBlockEntity#tick). */
    public static void tick(WaterWheelBlockEntity wheel) {
        Level level = wheel.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        VivaWaterWheel viva = (VivaWaterWheel) wheel;
        assignOrderIfMissing(wheel, level);

        boolean due = viva.vivacore$consumeCheckNow()
                || Math.floorMod(level.getGameTime() + wheel.getBlockPos().asLong(), VivaConfig.wheelsCheckInterval()) == 0;
        if (!due) {
            return;
        }
        boolean shouldLimit = shouldBeLimited(wheel, viva.vivacore$isLimited(), true);
        if (shouldLimit != viva.vivacore$isLimited()) {
            viva.vivacore$setLimited(shouldLimit);
            CACHE.clear();
            wheel.setChanged();
            // Create сам пересчитает скорость, мощность и сеть, как при изменении потока воды
            wheel.updateGeneratedRotation();
        }
    }

    /**
     * Вызывается перед updateGeneratedRotation() в setFlowScoreAndUpdate (миксин): поток воды изменился —
     * сразу решаем, лишнее ли колесо, чтобы новое 4-е колесо не успело крутнуться.
     */
    public static void beforeFlowUpdate(WaterWheelBlockEntity wheel) {
        Level level = wheel.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        VivaWaterWheel viva = (VivaWaterWheel) wheel;
        assignOrderIfMissing(wheel, level);
        // без кеша: поток этого колеса только что изменился
        boolean shouldLimit = shouldBeLimited(wheel, viva.vivacore$isLimited(), false);
        if (shouldLimit != viva.vivacore$isLimited()) {
            viva.vivacore$setLimited(shouldLimit);
            CACHE.clear();
        }
    }

    private static void assignOrderIfMissing(WaterWheelBlockEntity wheel, Level level) {
        VivaWaterWheel viva = (VivaWaterWheel) wheel;
        if (viva.vivacore$order() == VivaWaterWheel.NO_ORDER) {
            viva.vivacore$setOrder(level.getGameTime());
            wheel.setChanged();
        }
    }

    /**
     * @param current текущее состояние — возвращается, если по сети ничего нельзя сказать
     *                (сеть ещё не собрана после загрузки чанка), чтобы не мигать
     */
    public static boolean shouldBeLimited(WaterWheelBlockEntity wheel, boolean current, boolean useCache) {
        Level level = wheel.getLevel();
        if (level == null || level.getBlockEntity(wheel.getBlockPos()) != wheel) {
            // колесо не в мире (контрапция, виртуальный мир, уже удалено) — не трогаем
            return current;
        }
        if (!VivaConfig.wheelsEnabled()) {
            return false;
        }
        if (wheel.flowScore == 0) {
            // без воды колесо и так не крутит; в подсчёте не участвует
            return false;
        }
        if (!wheel.hasNetwork()) {
            // одно колесо само по себе — работает
            return false;
        }
        KineticNetwork network = wheel.getOrCreateNetwork();
        if (network == null) {
            return current;
        }
        List<WaterWheelBlockEntity> ordered = useCache
                ? candidates(network, level.getGameTime())
                : collect(network);
        int index = ordered.indexOf(wheel);
        if (index < 0) {
            return current;
        }
        return index >= VivaConfig.wheelsMaxPerNetwork();
    }

    /** Колёса сети с потоком воды, в порядке «кто работает первым». */
    public static List<WaterWheelBlockEntity> candidates(KineticNetwork network, long gameTime) {
        Cached cached = CACHE.get(network);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.ordered();
        }
        List<WaterWheelBlockEntity> list = collect(network);
        CACHE.put(network, new Cached(gameTime, list));
        return list;
    }

    private static List<WaterWheelBlockEntity> collect(KineticNetwork network) {
        List<WaterWheelBlockEntity> list = new ArrayList<>();
        for (KineticBlockEntity member : network.members.keySet()) {
            if (member instanceof WaterWheelBlockEntity w
                    && w.flowScore != 0
                    && !w.isRemoved()
                    && w.getLevel() != null
                    && w.getLevel().getBlockEntity(w.getBlockPos()) == w) {
                list.add(w);
            }
        }
        list.sort(ORDER);
        return list;
    }

    /** Сколько колёс работает при n кандидатах — чистая логика для тестов. */
    public static int working(int candidates, int max) {
        return Math.max(0, Math.min(candidates, max));
    }
}
