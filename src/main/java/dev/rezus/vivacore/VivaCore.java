package dev.rezus.vivacore;

import com.mojang.logging.LogUtils;
import dev.rezus.vivacore.command.VivaCommands;
import dev.rezus.vivacore.home.HomeEvents;
import dev.rezus.vivacore.zhiva.ZhivaEvents;
import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import net.neoforged.bus.api.IEventBus;
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

        VivaRegistries.register(modBus);
        modBus.addListener(ZhivaNetwork::register);

        NeoForge.EVENT_BUS.addListener(VivaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(HomeEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onMount);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ZhivaEvents::onLogout);
    }
}
