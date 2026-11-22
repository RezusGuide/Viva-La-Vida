package dev.rezus.vivacore.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Защита построек (тест 34): дрель / пила / каток на движущейся контрапции считают блок постройки неразрушимым
 * (как бедрок) — контрапция упирается и стоит, а не «грызёт» блок без конца. canBreak — RETURN → false.
 * SawMovementBehaviour и др. переопределяют canBreak и зовут super — им это тоже действует; если наследник не зовёт
 * super, страхует {@link CreateBlockHelperMixin}.
 */
@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class CreateBreakingMovementMixin {
    @Inject(method = "canBreak", at = @At("RETURN"), cancellable = true)
    private void vivacore$protectSites(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && SiteProtection.isProtected(level, pos)) cir.setReturnValue(false);
    }
}
