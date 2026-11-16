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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTest на логику домов и на кириллицу в командах.
 * В заметке «09 Тесты» отдельного номера для домов пока нет — тесты автоматические, проверяют только логику.
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
        h.assertTrue(sub.containsAll(List.of("одобрить", "снять", "список")), "подсказки подкоманд: " + sub);
        h.assertTrue(suggest(dispatcher, src, "viva дом с").containsAll(List.of("снять", "список")),
                "подсказки по началу кириллического слова");
        h.assertTrue(suggest(dispatcher, src, "viva дом од").contains("одобрить"), "подсказка «одобрить»");

        CommandNode<CommandSourceStack> home = dispatcher.getRoot().getChild("viva").getChild("дом");
        String usage = String.join(" | ", dispatcher.getAllUsage(home, src, true));
        h.assertTrue(usage.contains("одобрить <ник1> <ник2>") && usage.contains("снять <ник>"), "имена аргументов: " + usage);

        h.assertTrue(dispatcher.parse("viva дом снятьX", src).getReader().canRead(), "лишние буквы не принимаются");
        h.succeed();
    }

    private static List<String> suggest(CommandDispatcher<CommandSourceStack> d, CommandSourceStack src, String input) {
        return d.getCompletionSuggestions(d.parse(input, src)).join().getList().stream()
                .map(Suggestion::getText).toList();
    }
}
