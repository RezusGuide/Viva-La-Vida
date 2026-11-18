package dev.rezus.vivacore.orion;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rezus.vivacore.VivaConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Команды оператора: /viva орион статус | сбросить (EN: /viva orion status | reset). */
public final class OrionCommands {
    private OrionCommands() {}

    public record Words(String orion, String status, String reset) {}

    public static final Words RU = new Words("орион", "статус", "сбросить");
    public static final Words EN = new Words("orion", "status", "reset");

    public static LiteralArgumentBuilder<CommandSourceStack> build(Words w) {
        return Commands.literal(w.orion())
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.status()).executes(ctx -> {
                    OrionData data = OrionData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> data.programCancelled()
                            ? Component.translatable("vivacore.orion.command.status_cancelled", data.cancelledBy())
                            : Component.translatable("vivacore.orion.command.status_active"), false);
                    return data.programCancelled() ? 1 : 0;
                }))
                .then(Commands.literal(w.reset()).executes(ctx -> {
                    Orion.reset(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.orion.command.reset"), true);
                    return 1;
                }));
    }
}
