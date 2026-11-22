package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaCore;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Частицы армии со спрайтами агента моделей: вспышка выстрела (3 кадра), искры, обломки машины. */
public final class ArmyParticles {
    private ArmyParticles() {}

    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, VivaCore.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUZZLE_FLASH =
            TYPES.register("muzzle_flash", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK =
            TYPES.register("spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DEBRIS =
            TYPES.register("debris", () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
