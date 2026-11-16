package dev.rezus.vivacore.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.wheels.VivaWaterWheel;
import dev.rezus.vivacore.wheels.WheelLimit;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Лимит водяных колёс (тест 17). Цель — GeneratingKineticBlockEntity Create 6.0.10, потому что
 * в самом WaterWheelBlockEntity нет своих tick и addToGoggleTooltip. Для всех остальных генераторов
 * код выходит сразу после instanceof.
 * <ul>
 *   <li>tick — TAIL: периодическая перепроверка лимита колеса (WheelLimit#tick).</li>
 *   <li>addToGoggleTooltip — RETURN: строка «колесо стоит — лимит N на сеть» в очках инженера.</li>
 * </ul>
 */
@Mixin(GeneratingKineticBlockEntity.class)
public abstract class GeneratingKineticBlockEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void vivacore$wheelLimitTick(CallbackInfo ci) {
        if ((Object) this instanceof WaterWheelBlockEntity wheel) {
            WheelLimit.tick(wheel);
        }
    }

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void vivacore$wheelLimitGoggles(List<Component> tooltip, boolean isPlayerSneaking,
                                            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof WaterWheelBlockEntity && ((VivaWaterWheel) this).vivacore$isLimited()) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("vivacore.wheels.limited", VivaConfig.wheelsMaxPerNetwork())
                            .withStyle(ChatFormatting.GOLD)));
            cir.setReturnValue(true);
        }
    }
}
