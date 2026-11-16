package dev.rezus.vivacore.home;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rezus.vivacore.VivaConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Команды испытания «Приятный дом» (заметка «03 Этап 1 — Земля»):
 * одобрить дом на 1–2 игроков, снять игрока, список с домом / без дома.
 * Одно дерево строится дважды — с русскими и с английскими словами.
 */
public final class HomeCommands {
    private HomeCommands() {}

    /** Слова одного варианта команды. */
    public record Words(String home, String approve, String remove, String list,
                        String player1, String player2, String player) {}

    public static final Words RU = new Words("дом", "одобрить", "снять", "список", "ник1", "ник2", "ник");
    public static final Words EN = new Words("home", "approve", "remove", "list", "player1", "player2", "player");

    public static LiteralArgumentBuilder<CommandSourceStack> build(Words w) {
        return Commands.literal(w.home())
                .requires(src -> src.hasPermission(VivaConfig.homeCommandPermission()))
                .then(Commands.literal(w.approve())
                        .then(Commands.argument(w.player1(), GameProfileArgument.gameProfile())
                                .executes(ctx -> approve(ctx, w, false))
                                .then(Commands.argument(w.player2(), GameProfileArgument.gameProfile())
                                        .executes(ctx -> approve(ctx, w, true)))))
                .then(Commands.literal(w.remove())
                        .then(Commands.argument(w.player(), GameProfileArgument.gameProfile())
                                .executes(ctx -> remove(ctx, w))))
                .then(Commands.literal(w.list())
                        .executes(HomeCommands::list));
    }

    // ---------------------------------------------------------------- одобрить

    private static int approve(CommandContext<CommandSourceStack> ctx, Words w, boolean two)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        List<GameProfile> profiles = new ArrayList<>();
        GameProfile first = single(ctx, w.player1());
        if (first == null) return 0;
        profiles.add(first);
        if (two) {
            GameProfile second = single(ctx, w.player2());
            if (second == null) return 0;
            profiles.add(second);
        }

        HomeData data = HomeData.get(src.getServer());
        profiles.forEach(p -> data.rememberName(p.getId(), p.getName()));

        // Заготовка для набегов: координаты одобряющего оператора
        GlobalPos pos = GlobalPos.of(src.getLevel().dimension(), BlockPos.containing(src.getPosition()));
        List<UUID> ids = profiles.stream().map(GameProfile::getId).toList();
        HomeData.ApproveOutcome outcome = data.approve(ids, VivaConfig.homeMaxMembers(), pos);

        switch (outcome.result()) {
            case OK -> {
                Component names = Component.literal(joinNames(src.getServer(), data, ids));
                src.sendSuccess(() -> Component.translatable("vivacore.home.approved", names, formatPos(pos)), true);
                return 1;
            }
            case TOO_MANY -> src.sendFailure(Component.translatable("vivacore.home.error.too_many",
                    VivaConfig.homeMaxMembers()));
            case DUPLICATE -> src.sendFailure(Component.translatable("vivacore.home.error.duplicate",
                    nameOf(src.getServer(), data, outcome.conflicting())));
            case ALREADY_HOUSED -> src.sendFailure(Component.translatable("vivacore.home.error.already_housed",
                    nameOf(src.getServer(), data, outcome.conflicting())));
            case EMPTY -> src.sendFailure(Component.translatable("vivacore.home.error.empty"));
        }
        return 0;
    }

    /** Ровно один профиль из аргумента (селектор вроде @a может дать несколько). */
    @Nullable
    private static GameProfile single(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        Collection<GameProfile> found = GameProfileArgument.getGameProfiles(ctx, arg);
        if (found.size() != 1) {
            ctx.getSource().sendFailure(Component.translatable("vivacore.home.error.not_single", found.size()));
            return null;
        }
        return found.iterator().next();
    }

    // ---------------------------------------------------------------- снять

    private static int remove(CommandContext<CommandSourceStack> ctx, Words w) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        GameProfile profile = single(ctx, w.player());
        if (profile == null) return 0;
        HomeData data = HomeData.get(src.getServer());
        data.rememberName(profile.getId(), profile.getName());
        if (!data.remove(profile.getId())) {
            src.sendFailure(Component.translatable("vivacore.home.error.not_housed", profile.getName()));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("vivacore.home.removed", profile.getName()), true);
        return 1;
    }

    // ---------------------------------------------------------------- список

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        HomeData data = HomeData.get(server);

        Set<UUID> housed = new LinkedHashSet<>();
        data.homes().forEach(h -> housed.addAll(h.members()));
        List<UUID> homeless = knownPlayers(server, data).keySet().stream()
                .filter(id -> !housed.contains(id))
                .toList();

        src.sendSuccess(() -> Component.translatable("vivacore.home.list.header",
                housed.size(), homeless.size()), false);
        for (HomeData.Home home : data.homes()) {
            Component names = Component.literal(joinNames(server, data, home.members()));
            src.sendSuccess(() -> Component.translatable("vivacore.home.list.entry",
                    names, formatPos(home.pos())), false);
        }
        if (homeless.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("vivacore.home.list.no_homeless"), false);
        } else {
            Component names = Component.literal(joinNames(server, data, homeless));
            src.sendSuccess(() -> Component.translatable("vivacore.home.list.homeless", names), false);
        }
        return housed.size();
    }

    /**
     * Все, кто хоть раз заходил: ники, запомненные модом, плюс файлы world/playerdata
     * (на случай игроков, заходивших до установки Viva Core).
     */
    static Map<UUID, String> knownPlayers(MinecraftServer server, HomeData data) {
        Map<UUID, String> result = new LinkedHashMap<>(data.knownNames());
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".dat"))
                        .map(n -> n.substring(0, n.length() - 4))
                        .forEach(n -> {
                            try {
                                UUID id = UUID.fromString(n);
                                result.putIfAbsent(id, nameOf(server, data, id));
                            } catch (IllegalArgumentException ignored) {
                                // не UUID — пропускаем
                            }
                        });
            } catch (IOException e) {
                // список всё равно покажем по известным никам
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- вспомогательное

    static String nameOf(MinecraftServer server, HomeData data, @Nullable UUID id) {
        if (id == null) return "?";
        String known = data.knownNames().get(id);
        if (known != null) return known;
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var cached = cache.get(id);
            if (cached.isPresent()) return cached.get().getName();
        }
        return id.toString();
    }

    private static String joinNames(MinecraftServer server, HomeData data, Collection<UUID> ids) {
        return ids.stream().map(id -> nameOf(server, data, id)).collect(Collectors.joining(", "));
    }

    private static Component formatPos(@Nullable GlobalPos pos) {
        if (pos == null) return Component.literal("?");
        BlockPos p = pos.pos();
        return Component.literal(p.getX() + " " + p.getY() + " " + p.getZ() + " (" + pos.dimension().location() + ")");
    }
}
