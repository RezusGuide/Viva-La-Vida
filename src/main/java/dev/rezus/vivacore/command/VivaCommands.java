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
 * Только английские слова (задача 11: русские псевдонимы убраны — в подсказках по Tab были оба набора, а русские
 * неудобно набирать с переключением раскладки). Сообщения — на языке клиента через lang.
 */
public final class VivaCommands {
    private VivaCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            // dev: замер нагрузки зон (тест 32) — в обычной сборке команды нет
            dispatcher.register(Commands.literal("viva").then(dev.rezus.vivacore.zones.DevBench.build()));
            dispatcher.register(Commands.literal("viva").then(dev.rezus.vivacore.zones.DevBench.buildDoor()));
        }
        dispatcher.register(Commands.literal("viva")
                .then(HomeCommands.build(HomeCommands.EN))
                .then(ZhivaCommands.build(ZhivaCommands.EN))
                .then(dev.rezus.vivacore.orion.OrionCommands.build(dev.rezus.vivacore.orion.OrionCommands.EN))
                .then(dev.rezus.vivacore.army.ArmyCommands.build(dev.rezus.vivacore.army.ArmyCommands.EN))
                .then(dev.rezus.vivacore.structures.SiteCommands.build(dev.rezus.vivacore.structures.SiteCommands.EN))
                .then(dev.rezus.vivacore.structures.SiteCommands.buildNexus(dev.rezus.vivacore.structures.SiteCommands.EN))
                .then(dev.rezus.vivacore.stage.StageCommands.build(dev.rezus.vivacore.stage.StageCommands.EN)));
    }
}
