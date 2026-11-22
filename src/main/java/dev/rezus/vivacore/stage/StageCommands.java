package dev.rezus.vivacore.stage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rezus.vivacore.VivaConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /viva стадия [номер] (EN: /viva stage [n]) — временно, до AStages. */
public final class StageCommands {
    private StageCommands() {}

    public static final String RU = "стадия";
    public static final String EN = "stage";

    public static LiteralArgumentBuilder<CommandSourceStack> build(String word) {
        return Commands.literal(word)
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .executes(ctx -> {
                    int s = ServerStage.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.stage.command.current", s), false);
                    return s;
                })
                .then(Commands.argument("n", IntegerArgumentType.integer(ServerStage.MIN, ServerStage.MAX)).executes(ctx -> {
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    ServerStage.set(ctx.getSource().getServer(), n);
                    ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.stage.command.set", n), true);
                    return n;
                }));
    }
}
