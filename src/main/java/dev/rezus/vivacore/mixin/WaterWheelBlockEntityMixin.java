package dev.rezus.vivacore.mixin;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.rezus.vivacore.wheels.VivaWaterWheel;
import dev.rezus.vivacore.wheels.WheelLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Лимит водяных колёс (тест 17). Цель — WaterWheelBlockEntity Create 6.0.10; большое колесо
 * (LargeWaterWheelBlockEntity) наследует эти методы, поэтому тоже попадает под лимит.
 * <ul>
 *   <li>getGeneratedSpeed — HEAD, cancellable: у лишнего колеса 0. Через getGeneratedSpeed Create считает
 *       и скорость источника, и мощность (KineticNetwork#getActualCapacityOf = capacity × |generatedSpeed|),
 *       и isSource(). Колесо с 0 Create уже умеет обрабатывать — так ведёт себя колесо без воды на валу.</li>
 *   <li>setFlowScoreAndUpdate — перед вызовом updateGeneratedRotation: решаем «лишнее или нет» до того,
 *       как Create применит новую скорость, чтобы новое колесо не крутнулось на тик.</li>
 *   <li>write / read — TAIL / RETURN: сохраняем флаг и порядок, в том числе в пакете клиенту (для очков).</li>
 * </ul>
 */
@Mixin(WaterWheelBlockEntity.class)
public abstract class WaterWheelBlockEntityMixin extends GeneratingKineticBlockEntity implements VivaWaterWheel {
    @Unique
    private static final String VIVACORE$LIMITED = "vivacore:limited";
    @Unique
    private static final String VIVACORE$ORDER = "vivacore:order";

    @Unique
    private boolean vivacore$limited;
    @Unique
    private long vivacore$order = NO_ORDER;
    @Unique
    private boolean vivacore$checkNow = true;

    private WaterWheelBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void vivacore$limitSpeed(CallbackInfoReturnable<Float> cir) {
        if (vivacore$limited) {
            cir.setReturnValue(0f);
        }
    }

    @Inject(method = "setFlowScoreAndUpdate",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity;updateGeneratedRotation()V"))
    private void vivacore$beforeFlowUpdate(int score, CallbackInfo ci) {
        WheelLimit.beforeFlowUpdate((WaterWheelBlockEntity) (Object) this);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void vivacore$write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (vivacore$limited) {
            tag.putBoolean(VIVACORE$LIMITED, true);
        }
        if (vivacore$order != NO_ORDER && !clientPacket) {
            tag.putLong(VIVACORE$ORDER, vivacore$order);
        }
    }

    // RETURN, а не TAIL: в read есть ранний return (если нет "Material")
    @Inject(method = "read", at = @At("RETURN"))
    private void vivacore$read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        vivacore$limited = tag.getBoolean(VIVACORE$LIMITED);
        if (!clientPacket) {
            vivacore$order = tag.contains(VIVACORE$ORDER) ? tag.getLong(VIVACORE$ORDER) : NO_ORDER;
            vivacore$checkNow = true;
        }
    }

    @Override
    public boolean vivacore$isLimited() {
        return vivacore$limited;
    }

    @Override
    public void vivacore$setLimited(boolean limited) {
        this.vivacore$limited = limited;
    }

    @Override
    public long vivacore$order() {
        return vivacore$order;
    }

    @Override
    public void vivacore$setOrder(long order) {
        this.vivacore$order = order;
    }

    @Override
    public boolean vivacore$consumeCheckNow() {
        boolean now = vivacore$checkNow;
        vivacore$checkNow = false;
        return now;
    }
}
