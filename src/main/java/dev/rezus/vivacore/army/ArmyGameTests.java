package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 24 (первый шаг армии): дрон-разведчик появляется, летает у точки появления, не падает, никого не атакует.
 * Внешний вид (модель, анимации, свечение) GameTest не проверяет — только клиент.
 * Игрока в мире в GameTest нет (игрок в мире роняет тестовый сервер пакетами Create — см. CLAUDE.md),
 * поэтому «не атакует» проверяем так: у дрона нет целей и нет атаки, а житель рядом не теряет здоровье.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmyGameTests {
    private static final int WATCH_TICKS = 200;

    private ArmyGameTests() {}

    @GameTest(template = "wheels", timeoutTicks = WATCH_TICKS + 100)
    public static void scoutDroneHoversPeacefully(GameTestHelper h) {
        BlockPos spawnRel = new BlockPos(6, 4, 7);
        ScoutDrone drone = VivaRegistries.SCOUT_DRONE.get().spawn(h.getLevel(), h.absolutePos(spawnRel), MobSpawnType.SPAWN_EGG);
        h.assertTrue(drone != null, "дрон появился");
        Villager villager = h.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        float villagerHealth = villager.getHealth();
        Vec3 start = drone.position();
        AtomicReference<Double> maxShift = new AtomicReference<>(0.0);

        h.assertTrue(drone.getMaxHealth() == VivaConfig.scoutDroneMaxHealth(), "здоровье из конфига: " + drone.getMaxHealth());
        h.assertTrue(drone.hasNoTarget(), "у дрона нет целей");
        h.assertTrue(drone.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == null,
                "у дрона нет атаки");

        h.startSequence()
                .thenExecuteFor(WATCH_TICKS, () -> {
                    h.assertTrue(drone.isAlive() && !drone.isRemoved(), "дрон жив");
                    h.assertTrue(drone.getTarget() == null, "дрон ни на кого не нацелился");
                    h.assertTrue(drone.isNoGravity(), "без гравитации");
                    maxShift.set(Math.max(maxShift.get(), drone.position().distanceTo(start)));
                })
                .thenExecute(() -> {
                    BlockPos home = drone.home();
                    h.assertTrue(home != null && home.equals(h.absolutePos(spawnRel)), "точка появления запомнена: " + home);
                    double horizontal = Math.sqrt(Math.pow(drone.getX() - (home.getX() + 0.5), 2)
                            + Math.pow(drone.getZ() - (home.getZ() + 0.5), 2));
                    h.assertTrue(horizontal <= VivaConfig.scoutDroneHoverRadius() + 2,
                            "дрон держится у точки появления: " + horizontal);
                    h.assertTrue(drone.getY() >= home.getY() - VivaConfig.scoutDroneHoverHeight() - 1.5,
                            "дрон не упал: y=" + drone.getY() + ", дом y=" + home.getY());
                    h.assertTrue(maxShift.get() > 0.5, "дрон кружит, а не висит на месте: " + maxShift.get());
                    h.assertTrue(villager.getHealth() == villagerHealth, "житель рядом не пострадал");
                    drone.discard();
                })
                .thenSucceed();
    }
}
