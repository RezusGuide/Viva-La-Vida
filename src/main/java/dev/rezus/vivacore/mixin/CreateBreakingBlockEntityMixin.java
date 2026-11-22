package dev.rezus.vivacore.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Защита построек (тест 34): стоячие дрель и пила (BlockBreakingKineticBlockEntity) не начинают ломать блок постройки.
 * canBreak(BlockState, float) — RETURN → false, позиция — getBreakingPos().
 */
@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class CreateBreakingBlockEntityMixin {
    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Inject(method = "canBreak", at = @At("RETURN"), cancellable = true)
    private void vivacore$protectSites(BlockState state, float blockHardness, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && SiteProtection.isProtected(((BlockEntity) (Object) this).getLevel(), getBreakingPos())) {
            cir.setReturnValue(false);
        }
    }
}
