package dev.rezus.vivacore.mixin;

import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита построек (тест 34, задача 9): огонь в коробке не держится. BaseFireBlock.onPlace вызывается на любую
 * установку огня (огниво игрока и раздатчика, огненный заряд, молния, распространение огня, лава) — HEAD, cancellable:
 * в коробке огонь сразу убирается (так же ванильный onPlace убирает огонь, который «не может стоять»).
 * Огонь, который ставит сама постройка (огонь душ в шаблоне Нексуса), не трогается — {@link SiteProtection#fireBlocked}.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void vivacore$protectSites(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston,
                                       CallbackInfo ci) {
        if (SiteProtection.fireBlocked(level, pos)) {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }
}
