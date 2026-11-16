package dev.rezus.vivacore.zhiva;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.compat.ChromewareCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды оператора для Живы (тест 36): статус, смена состояния, ручной вызов «кушетки»
 * (заменяет кушетку Chromeware, если его нет в сборке). Игрок должен быть онлайн: состояние — attachment на игроке.
 */
public final class ZhivaCommands {
    private ZhivaCommands() {}

    public record Words(String zhiva, String status, String state, String couch, String player) {}

    public static final Words RU = new Words("жива", "статус", "состояние", "кушетка", "ник");
    public static final Words EN = new Words("zhiva", "status", "state", "couch", "player");

    public static LiteralArgumentBuilder<CommandSourceStack> build(Words w) {
        var stateNode = Commands.argument(w.player(), EntityArgument.player());
        for (ZhivaState s : ZhivaState.values()) {
            stateNode.then(Commands.literal(s.getSerializedName()).executes(ctx -> setState(ctx, w, s)));
        }
        return Commands.literal(w.zhiva())
                // те же права, что у команд домов (конфиг home.commandPermissionLevel)
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.status())
                        .then(Commands.argument(w.player(), EntityArgument.player()).executes(ctx -> status(ctx, w))))
                .then(Commands.literal(w.state()).then(stateNode))
                .then(Commands.literal(w.couch())
                        .then(Commands.argument(w.player(), EntityArgument.player()).executes(ctx -> couch(ctx, w))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx, Words w) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, w.player());
        ZhivaData data = Zhiva.data(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.zhiva.command.status",
                player.getGameProfile().getName(),
                data.state().getSerializedName(),
                yesNo(data.foreignImplant()),
                yesNo(ChromewareCompat.isLoaded()),
                yesNo(ChromewareCompat.hasCyberOptics(player))), false);
        return data.state().ordinal();
    }

    private static int setState(CommandContext<CommandSourceStack> ctx, Words w, ZhivaState state)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, w.player());
        Zhiva.setState(player, state);
        ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.zhiva.command.state_set",
                player.getGameProfile().getName(), state.getSerializedName()), true);
        return 1;
    }

    private static int couch(CommandContext<CommandSourceStack> ctx, Words w) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, w.player());
        boolean first = Zhiva.onCouch(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(first
                ? "vivacore.zhiva.command.couch_done" : "vivacore.zhiva.command.couch_already",
                player.getGameProfile().getName()), true);
        return first ? 1 : 0;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "vivacore.common.yes" : "vivacore.common.no");
    }
}
