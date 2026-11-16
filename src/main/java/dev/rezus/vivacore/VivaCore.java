package dev.rezus.vivacore;

import com.mojang.logging.LogUtils;
import dev.rezus.vivacore.command.VivaCommands;
import dev.rezus.vivacore.home.HomeEvents;
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
        // Все числа функций живут в серверном конфиге (world/serverconfig/vivacore-server.toml)
        container.registerConfig(ModConfig.Type.SERVER, VivaConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(VivaCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(HomeEvents::onPlayerLoggedIn);
    }
}
