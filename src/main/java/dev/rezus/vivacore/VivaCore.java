package dev.rezus.vivacore;

import com.mojang.logging.LogUtils;
import dev.rezus.vivacore.command.VivaCommands;
import dev.rezus.vivacore.home.HomeEvents;
import dev.rezus.vivacore.zhiva.ZhivaEvents;
import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Точка входа мода Viva Core. Заметка Obsidian: «06 Viva Core».
 */
@Mod(VivaCore.MOD_ID)
public final class VivaCore {
    public static final String MOD_ID = "vivacore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VivaCore(IEventBus modBus, ModContainer container) {
        // Все числа функций живут в серверном конфиге: config/vivacore-server.toml (так кладёт NeoForge 21.1.264, проверено в dev)
        container.registerConfig(ModConfig.Type.SERVER, VivaConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, VivaClientConfig.SPEC);

        dev.rezus.vivacore.army.ArmyRules.init();
        VivaRegistries.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            dev.rezus.vivacore.client.VivaClient.init(modBus);
        }
        modBus.addListener(ZhivaNetwork::register);
        modBus.addListener(dev.rezus.vivacore.orion.net.OrionNetwork::register);

        NeoForge.EVENT_BUS.addListener(VivaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(HomeEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onMount);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.army.Army::onDeath);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.army.Army::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.army.Army::onLogout);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) dev.rezus.vivacore.orion.Orion.giveNoteIfNeeded(p);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) ->
                dev.rezus.vivacore.orion.Orion.forget(e.getEntity().getUUID()));
        // постройки, стадия, зоны влияния, защита (задача 8; тесты 31, 32, 34)
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                e.enqueueWork(dev.rezus.vivacore.protection.CreateProtection::register));
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.structures.Structures::onServerStarted);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                dev.rezus.vivacore.structures.Structures.onServerStopped());
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.structures.Structures::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.zones.InfluenceZones::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.zones.InfluenceZones::onPositionCheck);
        if (!FMLEnvironment.production) {
            NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.zones.DevBench::onTickPre);
            NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.zones.DevBench::onTickPost);
        }
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onPlace);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onToolModify);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onTrample);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onUseItem);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onExplosion);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onLivingDestroyBlock);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onEnderPearl);
        NeoForge.EVENT_BUS.addListener(dev.rezus.vivacore.protection.SiteProtection::onChorus);
    }
}
