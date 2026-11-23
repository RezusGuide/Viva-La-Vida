package dev.rezus.vivacore.structures;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.stage.ServerStage;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Команды оператора (тест 31): /viva sites status | place &lt;lab|nexus|crash&gt; [here] | tp &lt;lab|nexus|crash&gt;
 * и /viva nexus destroy | restore. В dev-сборке ещё «build &lt;…&gt;» — генератор шаблонов-черновиков.
 * Задача 11: только английские слова, корень sites (было structures и русские псевдонимы).
 */
public final class SiteCommands {
    private SiteCommands() {}

    public record Words(String root, String status, String place, String here, String tp, String build,
                        String nexus, String destroy, String restore) {}

    public static final Words EN = new Words("sites", "status", "place", "here", "tp", "build",
            "nexus", "destroy", "restore");

    public static LiteralArgumentBuilder<CommandSourceStack> build(Words w) {
        var root = Commands.literal(w.root())
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.status()).executes(SiteCommands::status));
        var place = Commands.literal(w.place());
        var tp = Commands.literal(w.tp());
        var gen = Commands.literal(w.build());
        for (SiteType t : SiteType.values()) {
            place.then(Commands.literal(t.id).executes(ctx -> place(ctx, t, false))
                    .then(Commands.literal(w.here()).executes(ctx -> place(ctx, t, true))));
            tp.then(Commands.literal(t.id).executes(ctx -> tp(ctx, t)));
            gen.then(Commands.literal(t.id).executes(ctx -> generate(ctx, t)));
        }
        root.then(place).then(tp);
        if (!FMLEnvironment.production) root.then(gen);
        return root;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildNexus(Words w) {
        return Commands.literal(w.nexus())
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.destroy()).executes(ctx -> nexus(ctx, true)))
                .then(Commands.literal(w.restore()).executes(ctx -> nexus(ctx, false)));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Structures.ensurePlanned(server);
        SiteData d = SiteData.get(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.structures.command.status_header",
                String.format(Locale.ROOT, "%.1f", Math.toDegrees(d.angle())), ServerStage.get(server), d.nexusDestroyed()), false);
        for (SiteType t : SiteType.values()) {
            BlockPos pos = Structures.position(server, t);
            String where = pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
            boolean placed = d.entry(t).placed();
            ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.structures.command.status_line", t.id, where, placed), false);
        }
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> ctx, SiteType t, boolean here) {
        MinecraftServer server = ctx.getSource().getServer();
        if (Structures.isPlaced(server, t)) {
            ctx.getSource().sendFailure(Component.translatable("vivacore.structures.command.already", t.id));
            return 0;
        }
        BoundingBox box;
        if (here) {
            BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
            // «здесь» — для тестов и dev-скриншотов: без поворота, чтобы ракурсы были предсказуемы
            box = Structures.placeAt(server.overworld(), t, at, net.minecraft.world.level.block.Rotation.NONE);
        } else {
            box = Structures.place(server, t);
        }
        if (box == null) {
            ctx.getSource().sendFailure(Component.translatable("vivacore.structures.command.failed", t.id));
            return 0;
        }
        if (here && !FMLEnvironment.production) {
            // dev: метка-сущность в углу коробки — от неё сцена dev-скриншотов считает ракурсы
            var marker = net.minecraft.world.entity.EntityType.MARKER.create(server.overworld());
            if (marker != null) {
                marker.moveTo(box.minX(), box.minY(), box.minZ());
                marker.addTag("vivacore_site_origin");
                server.overworld().addFreshEntity(marker);
            }
        }
        BlockPos c = box.getCenter();
        ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.structures.command.placed", t.id,
                c.getX() + " " + c.getY() + " " + c.getZ()), true);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx, SiteType t) {
        MinecraftServer server = ctx.getSource().getServer();
        Structures.ensurePlanned(server);
        BlockPos pos = Structures.position(server, t);
        if (pos == null || ctx.getSource().getEntity() == null) {
            ctx.getSource().sendFailure(Component.translatable("vivacore.structures.command.failed", t.id));
            return 0;
        }
        ServerLevel level = server.overworld();
        int y = Structures.isPlaced(server, t) ? pos.getY()
                : level.getChunk(pos.getX() >> 4, pos.getZ() >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() & 15, pos.getZ() & 15) + 1;
        if (Structures.isPlaced(server, t) && t != SiteType.CRASH) {
            // к ядру не телепортируем — внутрь лаборатории только ногами; ставим над коробкой
            BoundingBox box = Structures.box(server, t);
            if (box != null) y = box.maxY() + 2;
        }
        ctx.getSource().getEntity().teleportTo(level, pos.getX() + 0.5, y, pos.getZ() + 0.5, java.util.Set.of(),
                ctx.getSource().getEntity().getYRot(), ctx.getSource().getEntity().getXRot());
        int fy = y;
        ctx.getSource().sendSuccess(() -> Component.translatable("vivacore.structures.command.teleported", t.id,
                pos.getX() + " " + fy + " " + pos.getZ()), true);
        return 1;
    }

    private static int nexus(CommandContext<CommandSourceStack> ctx, boolean destroyed) {
        Structures.setNexusDestroyed(ctx.getSource().getServer(), destroyed);
        ctx.getSource().sendSuccess(() -> Component.translatable(destroyed ? "vivacore.structures.command.nexus_destroyed"
                : "vivacore.structures.command.nexus_restored"), true);
        return 1;
    }

    /** Dev: собрать черновик шаблона в мире у оператора и сохранить .nbt (StructureGenerator). */
    private static int generate(CommandContext<CommandSourceStack> ctx, SiteType t) {
        // команда из консоли, прочитанная до загрузки мира, приходит без мира — берём overworld
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos at = ctx.getSource().getLevel() == null ? level.getSharedSpawnPos() : BlockPos.containing(ctx.getSource().getPosition());
        String path = StructureGenerator.generate(level, t, at);
        ctx.getSource().sendSuccess(() -> Component.literal("Viva Core dev: " + t.file + " → " + path), true);
        return 1;
    }
}
