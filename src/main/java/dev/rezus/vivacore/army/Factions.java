package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Три стороны конфликта (заметка «17 Три стороны конфликта»): ОРИОН, заражение, игроки и прочие.
 * Цели сторон — списки в конфиге army.targets.*: «@player», «@orion», «@infected», «#тег», «мод:*», «мод:id».
 * Мобы Spore определяются по пространству имён «spore» — мод может отсутствовать.
 */
public final class Factions {
    private Factions() {}

    public static final String SPORE_NAMESPACE = "spore";

    public static boolean isOrion(@Nullable Entity e) {
        return e instanceof OrionMachine m && !m.machine().infected;
    }

    public static boolean isInfected(@Nullable Entity e) {
        return e instanceof OrionMachine m && m.machine().infected;
    }

    public static boolean isSporeMob(@Nullable Entity e) {
        return e != null && SPORE_NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }

    /** Одна сторона: ОРИОН с ОРИОНом, заражённые с заражёнными. Свои своих не бьют. */
    public static boolean sameSide(Entity a, Entity b) {
        return (isOrion(a) && isOrion(b)) || (isInfected(a) && isInfected(b));
    }

    /** Цель ли entity для машины shooter (по спискам конфига её стороны). */
    public static boolean isTarget(OrionMachine shooter, Entity e) {
        if (e == shooter || !(e instanceof LivingEntity living) || !living.isAlive() || sameSide(shooter, e)) {
            return false;
        }
        List<? extends String> rules = VivaConfig.armyTargets(shooter.machine().infected);
        for (String rule : rules) {
            if (matches(rule.trim(), e)) return true;
        }
        return false;
    }

    static boolean matches(String rule, Entity e) {
        if (rule.isEmpty()) return false;
        switch (rule) {
            case "@player":
                return e instanceof Player p && !p.isCreative() && !p.isSpectator();
            case "@orion":
                return isOrion(e);
            case "@infected":
                return isInfected(e);
            default:
                break;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (rule.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(rule.substring(1));
            return tag != null && e.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag));
        }
        if (rule.endsWith(":*")) {
            return key.getNamespace().equals(rule.substring(0, rule.length() - 2));
        }
        ResourceLocation id = ResourceLocation.tryParse(rule);
        return id != null && id.equals(key);
    }

    /** Для тестов: тип сущности — цель для стороны infected? */
    public static boolean typeIsTargetRule(boolean infected, EntityType<?> type) {
        for (String rule : VivaConfig.armyTargets(infected)) {
            String r = rule.trim();
            if (r.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(r.substring(1));
                if (tag != null && type.is(TagKey.create(Registries.ENTITY_TYPE, tag))) return true;
            }
        }
        return false;
    }
}
