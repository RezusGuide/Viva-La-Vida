package dev.rezus.vivacore.mixin;

import dev.rezus.vivacore.protection.SiteProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита построек (тест 34, задача 9): раздатчик снаружи коробки, повёрнутый внутрь, ничего не выдаёт — ни жидкость
 * из ведра, ни огонь (огниво), ни блоки (шалкер, тыква, порошковый снег), ни снаряды. Событий у раздатчика нет
 * (ведро он выливает через BucketItem.emptyContents с player = null). Ванильный DispenserBlock.dispenseFrom, HEAD,
 * cancellable; выбрасыватель (DropperBlock) переопределяет метод и не затронут — он только выкидывает предметы.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void vivacore$protectSites(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (SiteProtection.dispenseBlocked(level, pos, state.getValue(DispenserBlock.FACING))) {
            level.levelEvent(1001, pos, 0); // щелчок «раздатчик пуст»
            ci.cancel();
        }
    }
}
