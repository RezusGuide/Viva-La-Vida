package dev.rezus.vivacore.home;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import dev.rezus.vivacore.VivaCore;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTest на логику домов и на кириллицу в командах — тест 35 в «09 Тесты» (испытание «Приятный дом»).
 * Автоматически проверяется логика и разбор команд; команды из чата живого клиента — вручную.
 * Запуск: ./gradlew runGameTestServer
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeGameTests {
    private static final String EMPTY = "empty";
    private static final int MAX = 2;

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final GlobalPos POS = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -20));

    private HomeGameTests() {}

    /** Одобрить двоих — ок; третий в тот же дом не влезает. */
    @GameTest(template = EMPTY)
    public static void twoFitThirdDoesNot(GameTestHelper h) {
        HomeData data = new HomeData();
        h.assertTrue(data.approve(List.of(A, B), MAX, POS).result() == HomeData.Result.OK, "двое должны влезть");
        h.assertTrue(data.homeOf(A).isPresent() && data.homeOf(B).isPresent(), "оба с домом");
        HomeData fresh = new HomeData();
        h.assertTrue(fresh.approve(List.of(A, B, C), MAX, POS).result() == HomeData.Result.TOO_MANY,
                "трое в один дом — отказ");
        h.assertTrue(fresh.homes().isEmpty(), "после отказа дом не создан");
        h.assertTrue(data.approve(List.of(C), MAX, POS).result() == HomeData.Result.OK, "третий — в свой дом");
        h.assertTrue(data.homes().size() == 2, "два дома");
        h.succeed();
    }

    /** Игрок не может быть в двух домах; один и тот же игрок дважды — отказ. */
    @GameTest(template = EMPTY)
    public static void playerInOnlyOneHome(GameTestHelper h) {
        HomeData data = new HomeData();
        h.assertTrue(data.approve(List.of(A), MAX, POS).result() == HomeData.Result.OK, "A в доме");
        HomeData.ApproveOutcome second = data.approve(List.of(B, A), MAX, POS);
        h.assertTrue(second.result() == HomeData.Result.ALREADY_HOUSED && A.equals(second.conflicting()),
                "A уже в доме — отказ с указанием A");
        h.assertTrue(data.homeOf(B).isEmpty(), "B не попал в дом при отказе");
        h.assertTrue(data.approve(List.of(B, B), MAX, POS).result() == HomeData.Result.DUPLICATE, "B дважды — отказ");
        h.assertTrue(data.homes().size() == 1, "остался один дом");
        h.succeed();
    }

    /** Снять игрока; пустой дом удаляется; снять бездомного — false. */
    @GameTest(template = EMPTY)
    public static void removePlayer(GameTestHelper h) {
        HomeData data = new HomeData();
        data.approve(List.of(A, B), MAX, POS);
        h.assertTrue(data.remove(A), "A снят");
        h.assertTrue(data.homeOf(A).isEmpty(), "A без дома");
        h.assertTrue(data.homeOf(B).isPresent(), "B остался");
        h.assertTrue(data.remove(B), "B снят");
        h.assertTrue(data.homes().isEmpty(), "пустой дом удалён");
        h.assertTrue(!data.remove(B), "снять бездомного — false");
        h.assertTrue(data.approve(List.of(A, B), MAX, POS).result() == HomeData.Result.OK, "после снятия можно снова");
        h.succeed();
    }

    /** Данные после сохранения и загрузки совпадают. */
    @GameTest(template = EMPTY)
    public static void saveLoadRoundTrip(GameTestHelper h) {
        HomeData data = new HomeData();
        data.approve(List.of(A, B), MAX, POS);
        data.approve(List.of(C), MAX, null);
        data.remove(B);
        data.rememberName(A, "Alpha");
        data.rememberName(C, "Gamma");

        var registries = h.getLevel().registryAccess();
        CompoundTag tag = data.save(new CompoundTag(), registries);
        HomeData loaded = HomeData.load(tag, registries);

        h.assertTrue(loaded.sameContent(data), "содержимое совпадает после загрузки");
        h.assertTrue(POS.equals(loaded.homeOf(A).orElseThrow().pos()), "координаты дома сохранились");
        h.assertTrue(loaded.homeOf(B).isEmpty(), "снятый игрок остался снятым");
        // новый дом после загрузки не должен взять старый id
        loaded.approve(List.of(B), MAX, POS);
        long distinctIds = loaded.homes().stream().mapToInt(HomeData.Home::id).distinct().count();
        h.assertTrue(distinctIds == loaded.homes().size(), "id домов не повторяются после загрузки");
        h.succeed();
    }

    /**
     * Кириллица в Brigadier: разбор и выполнение команды, подсказки по Tab на сервере,
     * имена аргументов в подсказке использования.
     */
    @GameTest(template = EMPTY)
    public static void cyrillicCommands(GameTestHelper h) {
        var server = h.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();

        for (String cmd : List.of("viva дом список", "viva home list")) {
            ParseResults<CommandSourceStack> parse = dispatcher.parse(cmd, src);
            h.assertTrue(parse.getExceptions().isEmpty() && !parse.getReader().canRead(),
                    "команда разобрана целиком: " + cmd);
            try {
                dispatcher.execute(parse);
            } catch (Exception e) {
                h.fail("команда не выполнилась: " + cmd + " — " + e.getMessage());
            }
        }

        h.assertTrue(suggest(dispatcher, src, "viva д").contains("дом"), "подсказка «дом» по «д»");
        List<String> sub = suggest(dispatcher, src, "viva дом ");
        h.assertTrue(sub.containsAll(List.of("одобрить", "снять", "список", "место", "добавить")), "подсказки подкоманд: " + sub);
        h.assertTrue(suggest(dispatcher, src, "viva дом с").containsAll(List.of("снять", "список")),
                "подсказки по началу кириллического слова");
        h.assertTrue(suggest(dispatcher, src, "viva дом од").contains("одобрить"), "подсказка «одобрить»");

        CommandNode<CommandSourceStack> home = dispatcher.getRoot().getChild("viva").getChild("дом");
        String usage = String.join(" | ", dispatcher.getAllUsage(home, src, true));
        h.assertTrue(usage.contains("одобрить <ник1> <ник2>") && usage.contains("снять <ник>")
                && usage.contains("добавить <ник> <к_нику>") && usage.contains("место <ник> <координаты>"),
                "имена аргументов: " + usage);

        h.assertTrue(dispatcher.parse("viva дом снятьX", src).getReader().canRead(), "лишние буквы не принимаются");
        h.succeed();
    }

    /** Позиция из консоли не сохраняется (null), у сущности — сохраняется её позиция. */
    @GameTest(template = EMPTY)
    public static void consolePosIsNull(GameTestHelper h) {
        var server = h.getLevel().getServer();
        h.assertTrue(HomeCommands.sourcePos(server.createCommandSourceStack()) == null, "консоль → позиция null");
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 1));
        GlobalPos pos = HomeCommands.sourcePos(stand.createCommandSourceStack());
        h.assertTrue(pos != null && pos.pos().equals(stand.blockPosition()) && pos.dimension().equals(h.getLevel().dimension()),
                "сущность → её позиция: " + pos);

        HomeData data = new HomeData();
        data.approve(List.of(A), MAX, HomeCommands.sourcePos(server.createCommandSourceStack()));
        h.assertTrue(data.homeOf(A).orElseThrow().pos() == null, "дом из консоли без позиции");
        h.succeed();
    }

    /** «место»: задать место дома; у бездомного — отказ; место переживает сохранение. */
    @GameTest(template = EMPTY)
    public static void setHomePos(GameTestHelper h) {
        HomeData data = new HomeData();
        data.approve(List.of(A, B), MAX, null);
        GlobalPos target = GlobalPos.of(Level.NETHER, new BlockPos(1, 70, 2));
        h.assertTrue(data.setPos(B, target), "место задано через второго жильца");
        h.assertTrue(target.equals(data.homeOf(A).orElseThrow().pos()), "место общее для дома");
        h.assertTrue(!data.setPos(C, target), "у бездомного — отказ");
        var registries = h.getLevel().registryAccess();
        HomeData loaded = HomeData.load(data.save(new CompoundTag(), registries), registries);
        h.assertTrue(target.equals(loaded.homeOf(A).orElseThrow().pos()), "место после загрузки");
        h.succeed();
    }

    /** «добавить»: второй жилец; полный дом — отказ; у добавляемого уже есть дом — отказ. */
    @GameTest(template = EMPTY)
    public static void addSecondMember(GameTestHelper h) {
        HomeData data = new HomeData();
        data.approve(List.of(A), MAX, POS);
        h.assertTrue(data.addMember(B, A, MAX) == HomeData.Result.OK, "B добавлен к A");
        h.assertTrue(data.homeOf(B).orElseThrow() == data.homeOf(A).orElseThrow(), "A и B в одном доме");
        h.assertTrue(data.addMember(C, A, MAX) == HomeData.Result.FULL, "в полный дом — отказ");
        h.assertTrue(data.homeOf(C).isEmpty(), "C не попал в дом");
        data.approve(List.of(C), MAX, POS);
        h.assertTrue(data.addMember(C, A, MAX) == HomeData.Result.ALREADY_HOUSED, "у C уже есть дом — отказ");
        h.assertTrue(data.addMember(D, B, 3) == HomeData.Result.OK, "при maxMembers=3 третий влезает");
        h.assertTrue(data.addMember(A, D, MAX) == HomeData.Result.ALREADY_HOUSED, "A уже в доме");
        h.assertTrue(data.addMember(B, B, MAX) == HomeData.Result.DUPLICATE, "сам к себе — отказ");
        HomeData empty = new HomeData();
        h.assertTrue(empty.addMember(A, B, MAX) == HomeData.Result.TARGET_NOT_HOUSED, "к бездомному — отказ");
        h.succeed();
    }

    /** Ник из usercache.json запоминается в HomeData. */
    @GameTest(template = EMPTY)
    public static void usercacheNameRemembered(GameTestHelper h) {
        // У GameTestServer нет своего usercache — создаём кэш во временном файле
        GameProfileCache cache;
        try {
            Path file = Files.createTempFile("vivacore-usercache", ".json");
            Files.writeString(file, "[]");
            file.toFile().deleteOnExit();
            cache = new GameProfileCache(null, file.toFile());
        } catch (IOException e) {
            h.fail("не создать временный usercache: " + e);
            return;
        }
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
        cache.add(new GameProfile(id, "CacheGuy"));
        HomeData data = new HomeData();
        h.assertTrue("CacheGuy".equals(HomeCommands.nameOf(cache, data, id)), "ник из кэша");
        h.assertTrue("CacheGuy".equals(data.knownNames().get(id)), "ник запомнен в HomeData");
        h.succeed();
    }

    private static List<String> suggest(CommandDispatcher<CommandSourceStack> d, CommandSourceStack src, String input) {
        return d.getCompletionSuggestions(d.parse(input, src)).join().getList().stream()
                .map(Suggestion::getText).toList();
    }
}
