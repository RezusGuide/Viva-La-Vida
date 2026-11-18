package dev.rezus.vivacore.army;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.orion.Orion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Команды оператора для армии (тесты 24 и 27): /viva армия вкл | выкл | тревога &lt;0–4&gt; | статус
 * (EN: /viva army on | off | alarm &lt;0–4&gt; | status). «вкл/выкл» меняют конфиг army.enabled (сохраняется в файл).
 * Тревога и статус — для участка, где стоит выполняющий команду (у консоли — точка спавна мира).
 */
public final class ArmyCommands {
    private ArmyCommands() {}

    public record Words(String army, String on, String off, String alarm, String status, String level) {}

    public static final Words RU = new Words("армия", "вкл", "выкл", "тревога", "статус", "уровень");
    public static final Words EN = new Words("army", "on", "off", "alarm", "status", "level");

    public static LiteralArgumentBuilder<CommandSourceStack> build(Words w) {
        return Commands.literal(w.army())
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.on()).executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(Commands.literal(w.off()).executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(Commands.literal(w.alarm())
                        .then(Commands.argument(w.level(), IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                            int level = IntegerArgumentType.getInteger(ctx, w.level());
                            CommandSourceStack src = ctx.getSource();
                            Alarm.set(src.getLevel(), BlockPos.containing(src.getPosition()), level);
                            src.sendSuccess(() -> Component.translatable("vivacore.army.command.alarm_set",
                                    Alarm.sector(src.getLevel(), BlockPos.containing(src.getPosition())), level), true);
                            return level;
                        })))
                .then(Commands.literal(w.status()).executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel level = src.getLevel();
                    BlockPos pos = BlockPos.containing(src.getPosition());
                    float alarm = Alarm.get(level, pos);
                    int machines = Waves.countMachines(src.getServer());
                    long heavy = level.getEntitiesOfClass(OrionMachine.class,
                            new net.minecraft.world.phys.AABB(pos).inflate(VivaConfig.armyHeavyGroupRadius()),
                            m -> m.isAlive() && m.machine().heavy).size();
                    double mspt = src.getServer().getAverageTickTimeNanos() / 1_000_000.0;
                    src.sendSuccess(() -> Component.translatable("vivacore.army.command.status",
                            yesNo(VivaConfig.armyEnabled()), yesNo(Orion.isProgramCancelled(src.getServer())),
                            Alarm.sector(level, pos), String.format("%.2f", alarm),
                            machines, VivaConfig.armyMaxMachines(), heavy, String.format("%.1f", mspt)), false);
                    return machines;
                }));
    }

    private static int setEnabled(CommandSourceStack src, boolean enabled) {
        VivaConfig.ARMY_ENABLED.set(enabled);
        VivaConfig.ARMY_ENABLED.save();
        src.sendSuccess(() -> Component.translatable(enabled ? "vivacore.army.command.on" : "vivacore.army.command.off"), true);
        return 1;
    }

    private static Component yesNo(boolean v) {
        return Component.translatable(v ? "vivacore.common.yes" : "vivacore.common.no");
    }
}
