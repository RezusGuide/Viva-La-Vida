package dev.rezus.vivacore.mixin;

import com.simibubi.create.foundation.utility.BlockHelper;
import dev.rezus.vivacore.protection.SiteProtection;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита построек от Create (тест 34). BlockHelper.destroyBlockAs — общая точка, через которую Create ломает блоки:
 * дрели и пилы на контрапциях и стоячие (BlockHelper.destroyBlock → destroyBlockAs с player = null — без
 * BlockEvent.BreakEvent, поэтому событием не ловится), катки, а также деплоер (у него фейковый игрок — его ловит
 * и событие). HEAD, cancellable: в коробке постройки блок не ломается.
 */
@Mixin(value = BlockHelper.class, remap = false)
public abstract class CreateBlockHelperMixin {
    @Inject(method = "destroyBlockAs", at = @At("HEAD"), cancellable = true)
    private static void vivacore$protectSites(Level level, BlockPos pos, Player player, ItemStack usedTool, float effectChance,
                                              Consumer<ItemStack> droppedItemCallback, CallbackInfo ci) {
        if (!SiteProtection.bypass(player) && SiteProtection.isProtected(level, pos)) ci.cancel();
    }
}
