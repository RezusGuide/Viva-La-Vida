package dev.rezus.vivacore.mixin;

import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита построек (тест 34, задача 9): огонь снаружи коробки не сжигает горючие блоки внутри неё. FireBlock.checkBurnOut
 * (ванильный, NeoForge добавил параметр Direction) убирает соседний горючий блок без событий — HEAD, cancellable:
 * блок в коробке не сгорает.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void vivacore$protectSites(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face,
                                       CallbackInfo ci) {
        if (SiteProtection.isProtected(level, pos)) ci.cancel();
    }
}
