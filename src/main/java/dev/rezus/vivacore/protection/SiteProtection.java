package dev.rezus.vivacore.protection;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Защита построек (тест 34). Заметка «17 Три стороны конфликта», раздел «Защита построек».
 * <p>
 * Коробка постройки — по размеру шаблона (как поставлена) плюс отступ protection.margin со всех сторон.
 * В коробке: игроки не ломают и не ставят блоки (кроме операторов в творческом режиме), не льют жидкости;
 * взрывы, мобы (иссушитель, дракон, разрушитель, эндермен) и контрапции Create не ломают и не сдвигают блоки
 * (Create — см. {@link CreateProtection}); эндер-жемчуг и плод хоруса не переносят внутрь коробки лаборатории.
 * Использование блоков (дверь с ключом, терминал, кнопки, рычаги) не трогаем — работает как обычно.
 * Блоки из тега vivacore:protection_exempt (ящики припасов) ломать можно.
 * <p>
 * Цена: на событие — до трёх проверок «точка в коробке» по кешу {@link Structures#cache}.
 */
public final class SiteProtection {
    private SiteProtection() {}

    public static final TagKey<Block> EXEMPT =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "protection_exempt"));

    /** Коробка защищённой постройки, в которой pos (с отступом). null — не защищено. */
    @Nullable
    public static SiteType protectedAt(@Nullable LevelAccessor level, BlockPos pos) {
        if (!VivaConfig.protectionEnabled() || !(level instanceof Level l) || l.isClientSide() || l.dimension() != Level.OVERWORLD
                || l.getServer() == null) {
            return null;
        }
        int m = VivaConfig.protectionMargin();
        List<? extends String> types = VivaConfig.protectionStructures();
        for (Structures.Protected p : Structures.cache(l.getServer()).boxes()) {
            if (inside(p.box(), pos, m) && types.contains(p.type().id)) return p.type();
        }
        return null;
    }

    public static boolean isProtected(@Nullable LevelAccessor level, BlockPos pos) {
        return protectedAt(level, pos) != null;
    }

    private static boolean inside(BoundingBox b, BlockPos p, int m) {
        return p.getX() >= b.minX() - m && p.getX() <= b.maxX() + m && p.getY() >= b.minY() - m && p.getY() <= b.maxY() + m
                && p.getZ() >= b.minZ() - m && p.getZ() <= b.maxZ() + m;
    }

    /** Нельзя ли сюда телепортироваться жемчугом / хорусом (коробки из protection.noTeleportInto). */
    public static boolean teleportBlocked(Level level, Vec3 target) {
        if (!VivaConfig.protectionEnabled() || level.isClientSide() || level.dimension() != Level.OVERWORLD || level.getServer() == null) {
            return false;
        }
        BlockPos pos = BlockPos.containing(target);
        int m = VivaConfig.protectionMargin();
        List<? extends String> types = VivaConfig.protectionNoTeleportInto();
        for (Structures.Protected p : Structures.cache(level.getServer()).boxes()) {
            if (types.contains(p.type().id) && inside(p.box(), pos, m)) return true;
        }
        return false;
    }

    /** Раздатчик в pos, повёрнутый в facing: снаружи коробки внутрь — нельзя (DispenserBlockMixin). */
    public static boolean dispenseBlocked(Level level, BlockPos pos, Direction facing) {
        return isProtected(level, pos.relative(facing)) && !isProtected(level, pos);
    }

    /** Огонь в pos убрать (BaseFireBlockMixin): в коробке — да, кроме огня, который ставит сама постройка. */
    public static boolean fireBlocked(Level level, BlockPos pos) {
        return !level.isClientSide() && !Structures.isBuilding() && isProtected(level, pos);
    }

    /**
     * Огниво и огненный заряд в руке игрока по блоку в коробке (или с краю внутрь) — отказ: поджога в постройке нет
     * (огонь и так не устоит — BaseFireBlockMixin, — но так не тратится прочность огнива и не поджигается свеча/костёр/TNT).
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof net.minecraft.world.item.FlintAndSteelItem)
                && !(stack.getItem() instanceof net.minecraft.world.item.FireChargeItem)) {
            return;
        }
        if (bypass(event.getEntity()) || event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        Direction face = event.getFace();
        if (isProtected(event.getLevel(), pos) || (face != null && isProtected(event.getLevel(), pos.relative(face)))) {
            event.setCanceled(true);
        }
    }

    /** Оператор в творческом режиме может строить в постройках. */
    public static boolean bypass(@Nullable Entity e) {
        return e instanceof Player p && p.isCreative() && p.hasPermissions(VivaConfig.protectionOpLevel());
    }

    // ---------- события ----------

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (bypass(event.getPlayer()) || event.getState().is(EXEMPT)) return;
        if (isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (bypass(event.getEntity())) return;
        if (isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    /** Топор, мотыга, лопата (обтёсывание, вспахивание, тропинка) — тоже изменение блока. */
    public static void onToolModify(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || bypass(event.getPlayer())) return;
        if (isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (isProtected(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    /** Ведро: в 1.21 у NeoForge нет события ведра — смотрим, куда игрок целится, как сам BucketItem. */
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BucketItem) && !(stack.getItem() instanceof SolidBucketItem)) return;
        Player player = event.getEntity();
        if (bypass(player) || player.level().isClientSide()) return;
        BlockHitResult hit = bucketTarget(player);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        if (isProtected(player.level(), hit.getBlockPos()) || isProtected(player.level(), hit.getBlockPos().relative(hit.getDirection()))) {
            event.setCanceled(true);
        }
    }

    /** Цель ведра — луч от глаз на дальность руки, жидкость-источник тоже цель (как Item.getPlayerPOVHitResult). */
    public static BlockHitResult bucketTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!VivaConfig.protectionEnabled()) return;
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> isProtected(level, pos));
    }

    /** Иссушитель, дракон, разрушитель и другие мобы, ломающие блоки телом. */
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (isProtected(event.getEntity().level(), event.getPos())) event.setCanceled(true);
    }

    /** Мобы с mobGriefing (эндермен берёт блоки, кролик ест морковь, …): внутри коробки — нельзя. */
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity e = event.getEntity();
        if (e != null && !(e instanceof Player) && isProtected(e.level(), e.blockPosition())) event.setCanGrief(false);
    }

    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (teleportBlocked(event.getEntity().level(), event.getTarget())) event.setCanceled(true);
    }

    public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
        if (teleportBlocked(event.getEntity().level(), event.getTarget())) event.setCanceled(true);
    }
}
