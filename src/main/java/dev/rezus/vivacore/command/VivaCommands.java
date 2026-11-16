package dev.rezus.vivacore.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.rezus.vivacore.home.HomeCommands;
import dev.rezus.vivacore.zhiva.ZhivaCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Корневая команда /viva. Подкоманды модулей добавляются сюда.
 * <p>
 * Русские слова — основные (кириллица в Brigadier проверена тестом
 * {@code HomeGameTests#cyrillicCommands}), английские — псевдонимы для консоли и скриптов.
 */
public final class VivaCommands {
    private VivaCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("viva")
                .then(HomeCommands.build(HomeCommands.RU))
                .then(HomeCommands.build(HomeCommands.EN))
                .then(ZhivaCommands.build(ZhivaCommands.RU))
                .then(ZhivaCommands.build(ZhivaCommands.EN)));
    }
}
