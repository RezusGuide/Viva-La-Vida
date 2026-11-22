package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Регистрация машин ОРИОНа: тип сущности и яйцо призыва на каждую {@link MachineType}.
 * Естественного спавна нет — только яйца, команды и волны подкрепления.
 */
public final class ArmyEntities {
    private ArmyEntities() {}

    private static final Map<MachineType, DeferredHolder<EntityType<?>, ? extends EntityType<? extends OrionMachine>>> TYPES =
            new EnumMap<>(MachineType.class);
    private static final Map<MachineType, DeferredItem<DeferredSpawnEggItem>> EGGS = new EnumMap<>(MachineType.class);

    /** Дрон-разведчик — отдельный класс (поднимает тревогу). */
    public static final DeferredHolder<EntityType<?>, EntityType<ScoutDrone>> SCOUT_DRONE =
            VivaRegistries.ENTITIES.register(MachineType.SCOUT_DRONE.id, () -> builder(
                    EntityType.Builder.<ScoutDrone>of((t, l) -> new ScoutDrone(t, l, MachineType.SCOUT_DRONE), MobCategory.MONSTER),
                    MachineType.SCOUT_DRONE));

    /** Снаряды машин (пуля, сквозная, снаряд танка, ракета). */
    public static final DeferredHolder<EntityType<?>, EntityType<OrionProjectile>> PROJECTILE =
            VivaRegistries.ENTITIES.register("orion_projectile", () -> EntityType.Builder.<OrionProjectile>of(OrionProjectile::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(VivaCore.MOD_ID + ":orion_projectile"));

    static {
        TYPES.put(MachineType.SCOUT_DRONE, SCOUT_DRONE);
        for (MachineType mt : MachineType.values()) {
            if (mt == MachineType.SCOUT_DRONE) continue;
            if (mt.movement == MachineType.Movement.FLYING) {
                TYPES.put(mt, VivaRegistries.ENTITIES.register(mt.id, () -> builder(
                        EntityType.Builder.<FlyingMachine>of((t, l) -> new FlyingMachine(t, l, mt), MobCategory.MONSTER), mt)));
            } else {
                TYPES.put(mt, VivaRegistries.ENTITIES.register(mt.id, () -> builder(
                        EntityType.Builder.<OrionMachine>of((t, l) -> new OrionMachine(t, l, mt), MobCategory.MONSTER), mt)));
            }
        }
        for (MachineType mt : MachineType.values()) {
            EGGS.put(mt, VivaRegistries.ITEMS.register(mt.id + "_spawn_egg",
                    () -> new DeferredSpawnEggItem(TYPES.get(mt), mt.eggPrimary, mt.eggSecondary, new Item.Properties())));
        }
    }

    private static <T extends OrionMachine> EntityType<T> builder(EntityType.Builder<T> b, MachineType mt) {
        return b.sized(mt.width, mt.height)
                .eyeHeight(mt.height * 0.8f)
                .clientTrackingRange(mt.heavy || mt.boss() ? 12 : 10)
                .build(VivaCore.MOD_ID + ":" + mt.id);
    }

    @SuppressWarnings("unchecked")
    public static EntityType<? extends OrionMachine> type(MachineType mt) {
        return (EntityType<? extends OrionMachine>) TYPES.get(mt).get();
    }

    public static DeferredItem<DeferredSpawnEggItem> egg(MachineType mt) {
        return EGGS.get(mt);
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (MachineType mt : MachineType.values()) {
            event.put(type(mt), OrionMachine.createAttributes(mt).build());
        }
    }

    /** Принудительно загрузить класс до регистрации. */
    public static void init() {
    }
}
