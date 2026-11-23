package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Экономия в бою (задача 9, тест 38): пули — мгновенный выстрел без сущности, лимиты снарядов на участок и взрывов
 * за тик. Каждый тест — в своей пачке (машины воюют между собой).
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatLimitGameTests {
    private static final String AREA = "wheels";

    private CombatLimitGameTests() {}

    private static Pillager target(GameTestHelper h, BlockPos rel) {
        Pillager p = h.spawn(EntityType.PILLAGER, rel);
        p.setNoAi(true);
        p.setPersistenceRequired();
        return p;
    }

    private static OrionMachine machine(GameTestHelper h, MachineType t, BlockPos rel) {
        OrionMachine m = ArmyEntities.type(t).spawn(h.getLevel(), h.absolutePos(rel), MobSpawnType.SPAWN_EGG);
        m.setNoAi(true);
        return m;
    }

    /** Пехота стреляет по цели: урон есть, а снарядов-сущностей ОРИОНа рядом нет ни в один тик. */
    @GameTest(template = AREA, batch = "combat_hitscan", timeoutTicks = 260)
    public static void bulletsAreHitscan(GameTestHelper h) {
        Pillager t = target(h, new BlockPos(9, 1, 7));
        float hp = t.getHealth();
        OrionMachine m = ArmyEntities.type(MachineType.INFANTRY_LIGHT).spawn(h.getLevel(), h.absolutePos(new BlockPos(2, 1, 7)),
                MobSpawnType.SPAWN_EGG);
        AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40);
        int[] projectiles = {0};
        // только снаряды этой машины: снаряды машин соседних тестов могут залететь в область
        h.onEachTick(() -> projectiles[0] += h.getLevel().getEntitiesOfClass(OrionProjectile.class, area, p -> p.getOwner() == m).size());
        h.succeedWhen(() -> {
            h.assertTrue(m.combat().shotsFired() > 0, "выстрелы были");
            h.assertTrue(t.getHealth() < hp || t.isDeadOrDying(), "цель получила урон");
            h.assertTrue(projectiles[0] == 0, "снарядов-сущностей не было, а насчитано " + projectiles[0]);
            m.discard();
            t.discard();
        });
    }

    /** Мгновенный выстрел: за обсидианом урона нет; сквозной проходит 2 доски и бьёт за ними; стена ОРИОНа — стоп. */
    @GameTest(template = AREA, batch = "combat_hitscan_wall")
    public static void hitscanRespectsWallsAndPiercing(GameTestHelper h) {
        OrionMachine shooter = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(1, 1, 1));
        Pillager behindStone = target(h, new BlockPos(9, 1, 3));
        Pillager behindPlanks = target(h, new BlockPos(9, 1, 7));
        Pillager behindOrion = target(h, new BlockPos(9, 1, 11));
        for (int y = 1; y <= 3; y++) {
            h.setBlock(new BlockPos(6, y, 3), Blocks.OBSIDIAN);
            h.setBlock(new BlockPos(5, y, 7), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(6, y, 7), Blocks.OAK_PLANKS);
            h.setBlock(new BlockPos(6, y, 11), OrionBlocks.ORION_WALL.get());
        }
        float hp = behindStone.getHealth();
        var level = h.getLevel();
        Vec3 dir = new Vec3(1, 0, 0);
        Hitscan.Result a = Hitscan.fire(level, shooter, Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 3))), dir, 20, 5f,
                null, ArmyNetwork.KIND_BULLET);
        Hitscan.Result b = Hitscan.fire(level, shooter, Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 7))), dir, 20, 5f,
                new ShotTrace.Pierce(2), ArmyNetwork.KIND_BULLET);
        Hitscan.Result c = Hitscan.fire(level, shooter, Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 11))), dir, 20, 5f,
                new ShotTrace.Pierce(2), ArmyNetwork.KIND_BULLET);
        h.assertTrue(a.blocked() && a.entity() == null && behindStone.getHealth() == hp, "за обсидианом урона нет: " + a);
        h.assertTrue(b.entity() == behindPlanks && b.pierced().size() == 2, "сквозной прошёл 2 доски и попал: " + b);
        h.assertTrue(c.blocked() && c.entity() == null, "стена ОРИОНа (blast_proof) остановила сквозной: " + c);
        h.assertTrue(h.getBlockState(new BlockPos(5, 2, 7)).is(Blocks.OAK_PLANKS), "доски целы");
        shooter.discard();
        behindStone.discard();
        behindPlanks.discard();
        behindOrion.discard();
        h.succeed();
    }

    /** Лимит снарядов-сущностей на участок: сверх лимита новый не выпускается; удалённый освобождает место. */
    @GameTest(template = AREA, batch = "combat_limit_projectiles")
    public static void projectileLimitPerSector(GameTestHelper h) {
        int old = VivaConfig.armyMaxProjectilesPerSector();
        var level = h.getLevel();
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(6, 4, 7)));
        int base = ArmyLimits.projectilesIn(level, at);
        VivaConfig.ARMY_MAX_PROJECTILES.set(base + 3);
        OrionMachine owner = machine(h, MachineType.TANK, new BlockPos(1, 1, 1));
        List<OrionProjectile> shots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            h.assertTrue(ArmyLimits.canSpawnProjectile(level, at), "снаряд " + (i + 1) + " в пределах лимита");
            OrionProjectile p = OrionProjectile.create(level, owner, OrionProjectile.Kind.SHELL, 1f, 0f);
            p.setPos(at.add(0, i * 0.5, 0));
            level.addFreshEntity(p);
            shots.add(p);
        }
        int counted = ArmyLimits.projectilesIn(level, at) - base;
        boolean fourth = ArmyLimits.canSpawnProjectile(level, at);
        shots.get(0).discard();
        boolean afterRemove = ArmyLimits.canSpawnProjectile(level, at);
        for (OrionProjectile p : shots) p.discard();
        owner.discard();
        VivaConfig.ARMY_MAX_PROJECTILES.set(old);
        h.assertTrue(counted == 3, "учтено 3, а " + counted);
        h.assertTrue(!fourth, "четвёртый — сверх лимита");
        h.assertTrue(afterRemove, "после удаления одного место освободилось");
        h.succeed();
    }

    /** Лимит взрывов за тик: второй взрыв в том же тике — без ломания блоков, но с уроном рядом. */
    @GameTest(template = AREA, batch = "combat_limit_explosions")
    public static void explosionLimitPerTick(GameTestHelper h) {
        int old = VivaConfig.armyMaxExplosionsPerTick();
        var rule = h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING);
        boolean was = rule.get();
        rule.set(true, h.getLevel().getServer());
        VivaConfig.ARMY_MAX_EXPLOSIONS.set(1);
        OrionMachine owner = machine(h, MachineType.TANK, new BlockPos(1, 1, 12));
        h.setBlock(new BlockPos(3, 1, 3), Blocks.DIRT);
        h.setBlock(new BlockPos(9, 1, 9), Blocks.DIRT);
        Pillager near = target(h, new BlockPos(9, 2, 10));
        float hp = near.getHealth();
        OrionProjectile first = OrionProjectile.create(h.getLevel(), owner, OrionProjectile.Kind.SHELL, 1f, 2.5f);
        OrionProjectile second = OrionProjectile.create(h.getLevel(), owner, OrionProjectile.Kind.SHELL, 1f, 2.5f);
        first.explodeAt(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        second.explodeAt(Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 2, 9))));
        boolean firstBroke = h.getBlockState(new BlockPos(3, 1, 3)).isAir();
        boolean secondKept = h.getBlockState(new BlockPos(9, 1, 9)).is(Blocks.DIRT);
        boolean hurt = near.getHealth() < hp || near.isDeadOrDying();
        VivaConfig.ARMY_MAX_EXPLOSIONS.set(old);
        rule.set(was, h.getLevel().getServer());
        owner.discard();
        near.discard();
        h.assertTrue(firstBroke, "первый взрыв — настоящий (сломал землю)");
        h.assertTrue(secondKept, "второй в том же тике — без ломания блоков");
        h.assertTrue(hurt, "второй всё равно ранил разбойника рядом");
        h.succeed();
    }
}
