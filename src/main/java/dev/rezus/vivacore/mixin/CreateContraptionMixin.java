package dev.rezus.vivacore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Защита построек от Create (тест 34, задача 9): контрапцию нельзя разобрать так, чтобы её блоки встали в коробку
 * постройки. Contraption.addBlocksToWorld для каждого блока смотрит, что стоит на месте: если там неразрушимый блок
 * (getDestroySpeed == −1, как бедрок), блок контрапции выпадает предметом (Block.dropResources); иначе стоящий блок
 * ломается (world.destroyBlock) и на его место ставится блок контрапции — без событий. Оборачиваем этот вызов
 * getDestroySpeed (WrapOperation — совместим с чужими обёртками того же вызова): в коробке — −1, и Create сам
 * выбрасывает блок предметом, блок постройки цел.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class CreateContraptionMixin {
    @WrapOperation(method = "addBlocksToWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float vivacore$protectSites(BlockState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
        if (level instanceof LevelAccessor accessor && SiteProtection.isProtected(accessor, pos)) return -1f;
        return original.call(state, level, pos);
    }
}
