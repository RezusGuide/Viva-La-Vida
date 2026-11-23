package dev.rezus.vivacore.mixin;

import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита построек (тест 34, задача 9): жидкость снаружи не затекает в коробку (вода или лава, вылитая игроком или
 * раздатчиком у края, ручей с горы). FlowingFluid.spreadTo — ванильный шаг растекания в соседний блок, HEAD,
 * cancellable: в коробку из-за её края — нельзя; внутри коробки (вода самой постройки) — как обычно.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void vivacore$protectSites(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState,
                                       CallbackInfo ci) {
        if (SiteProtection.isProtected(level, pos) && !SiteProtection.isProtected(level, pos.relative(direction.getOpposite()))) {
            ci.cancel();
        }
    }
}
