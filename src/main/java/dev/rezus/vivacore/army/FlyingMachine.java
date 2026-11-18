package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Летающая машина ОРИОНа (дроны, вертолёт, рой): без гравитации, полётная навигация, без урона от падения.
 * Патрулирует на army.patrolHeight над землёй.
 */
public class FlyingMachine extends OrionMachine {
    public FlyingMachine(EntityType<? extends FlyingMachine> type, Level level, MachineType machine) {
        super(type, level, machine);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected double patrolY(int x, int z) {
        int h = VivaConfig.armyPatrolHeight();
        return super.patrolY(x, z) + h / 2.0 + getRandom().nextInt(h + 1);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isNoGravity() {
        // мёртвая летающая машина падает
        return !isDeadOrDying();
    }
}
