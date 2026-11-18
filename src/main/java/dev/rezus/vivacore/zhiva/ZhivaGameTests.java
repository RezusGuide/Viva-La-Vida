package dev.rezus.vivacore.zhiva;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.compat.ChromewareCompat;
import dev.rezus.vivacore.zhiva.device.ZhivaChipItem;
import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import io.netty.channel.embedded.EmbeddedChannel;
import javax.annotation.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentInternals;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 36 из «09 Тесты»: твист с имплантом Живы. Плюс заготовка теста 33 (оптика Kiroshi).
 * Тесты проходят и без Chromeware, и с ним в run/mods (zhivaCouchSeatMount проверяет посадку только с ним).
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZhivaGameTests {
    private static final String EMPTY = "empty";

    private ZhivaGameTests() {}

    /** Тестовый игрок вне мира (см. TestPlayers). */
    private static ServerPlayer player(GameTestHelper h) {
        return dev.rezus.vivacore.gametest.TestPlayers.player(h);
    }

    private static int countChips(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (ZhivaChipItem.isChip(s)) n += s.getCount();
        return n;
    }

    /** IMPLANTED → REMOVED (чип) → REVEALED (чип забран); неверные переходы отклоняются; данные сохраняются. */
    @GameTest(template = EMPTY)
    public static void zhivaStatesAndSave(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(Zhiva.state(p) == ZhivaState.IMPLANTED, "по умолчанию IMPLANTED");
        h.assertTrue(Zhiva.insertChip(p) == Zhiva.Result.WRONG_STATE, "вставить в IMPLANTED нельзя");

        h.assertTrue(Zhiva.removeImplant(p) == Zhiva.Result.OK, "удалить");
        h.assertTrue(Zhiva.state(p) == ZhivaState.REMOVED, "REMOVED");
        h.assertTrue(countChips(p) == 1 && Zhiva.chipStatus(p) == Zhiva.ChipStatus.OWN, "у игрока свой чип");
        h.assertTrue(Zhiva.removeImplant(p) == Zhiva.Result.WRONG_STATE, "второй раз удалить нельзя");

        // сохранение сущности и загрузка в другого игрока — состояние и флаг на месте
        p.setData(VivaRegistries.ZHIVA, Zhiva.data(p).withForeignImplant(true));
        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        ServerPlayer loaded = player(h);
        loaded.load(saved);
        h.assertTrue(Zhiva.state(loaded) == ZhivaState.REMOVED && Zhiva.data(loaded).foreignImplant(),
                "после сохранения/загрузки: " + Zhiva.data(loaded));

        h.assertTrue(Zhiva.insertChip(p) == Zhiva.Result.OK, "вставить свой чип");
        h.assertTrue(Zhiva.state(p) == ZhivaState.REVEALED && countChips(p) == 0, "REVEALED, чип забран");

        // кодек записи
        Tag tag = ZhivaData.CODEC.encodeStart(NbtOps.INSTANCE, new ZhivaData(ZhivaState.REVEALED, true)).getOrThrow();
        h.assertTrue(ZhivaData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(new ZhivaData(ZhivaState.REVEALED, true)),
                "кодек ZhivaData");
        h.succeed();
    }

    /** Состояние копируется при смерти (copyOnDeath) — так же, как NeoForge делает в PlayerEvent.Clone. */
    @GameTest(template = EMPTY)
    public static void zhivaCopiedOnDeath(GameTestHelper h) {
        ServerPlayer old = player(h);
        old.setData(VivaRegistries.ZHIVA, new ZhivaData(ZhivaState.REVEALED, true));
        ServerPlayer respawned = player(h);
        AttachmentInternals.copyEntityAttachments(old, respawned, true);
        h.assertTrue(Zhiva.data(respawned).equals(new ZhivaData(ZhivaState.REVEALED, true)),
                "после смерти: " + Zhiva.data(respawned));
        h.succeed();
    }

    /** Чужой чип устройство не принимает; состояние и чип не меняются. */
    @GameTest(template = EMPTY)
    public static void zhivaForeignChipRejected(GameTestHelper h) {
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        Zhiva.setState(a, ZhivaState.REMOVED);
        a.getInventory().add(ZhivaChipItem.create(b));
        h.assertTrue(Zhiva.chipStatus(a) == Zhiva.ChipStatus.FOREIGN_ONLY, "у A только чужой чип");
        h.assertTrue(Zhiva.insertChip(a) == Zhiva.Result.FOREIGN_CHIP, "чужой чип — отказ");
        h.assertTrue(Zhiva.state(a) == ZhivaState.REMOVED && countChips(a) == 1, "ничего не изменилось");
        a.getInventory().add(ZhivaChipItem.create(a));
        h.assertTrue(Zhiva.insertChip(a) == Zhiva.Result.OK && countChips(a) == 1, "свой чип принят, чужой остался");
        h.succeed();
    }

    /** «Кушетка» срабатывает один раз: повтор не повторяет сообщение; ставит достижение-отметку. */
    @GameTest(template = EMPTY)
    public static void zhivaCouchOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(Zhiva.onCouch(p), "первый раз");
        h.assertTrue(!Zhiva.onCouch(p), "второй раз — без сообщения");
        h.assertTrue(Zhiva.data(p).foreignImplant(), "отметка в данных");
        var adv = p.server.getAdvancements().get(Zhiva.FOREIGN_IMPLANT_ADVANCEMENT);
        h.assertTrue(adv != null && p.getAdvancements().getOrStartProgress(adv).isDone(), "достижение vivacore:zhiva/foreign_implant получено");
        h.succeed();
    }

    /** Фраза о здоровье: только в REVEALED, не чаще антиспама. */
    @GameTest(template = EMPTY)
    public static void zhivaLowHealthPhrase(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(p.getMaxHealth() * (VivaConfig.zhivaLowHealthPercent() - 10) / 100f);
        long now = 1000;
        long cooldown = VivaConfig.zhivaPhraseCooldownSeconds() * 20L;

        h.assertTrue(!ZhivaHelp.checkHealth(p, now), "IMPLANTED — молчит");
        Zhiva.setState(p, ZhivaState.REMOVED);
        h.assertTrue(!ZhivaHelp.checkHealth(p, now), "REMOVED — молчит совсем");
        h.assertTrue(!ZhivaVoice.say(p, now, ZhivaHelp.RAID_INCOMING), "REMOVED — любая фраза молчит");
        Zhiva.setState(p, ZhivaState.REVEALED);
        h.assertTrue(ZhivaHelp.checkHealth(p, now), "REVEALED — фраза звучит");
        h.assertTrue(!ZhivaHelp.checkHealth(p, now + 1), "сразу повторно — антиспам");
        h.assertTrue(!ZhivaHelp.checkHealth(p, now + cooldown - 1), "до конца паузы — антиспам");
        h.assertTrue(ZhivaHelp.checkHealth(p, now + cooldown), "после паузы — снова звучит");
        p.setHealth(p.getMaxHealth());
        h.assertTrue(!ZhivaHelp.checkHealth(p, now + 10 * cooldown), "здоровье в норме — молчит");
        h.succeed();
    }

    /** Устройство: действие только рядом с блоком; удаление через perform даёт чип. */
    @GameTest(template = EMPTY)
    public static void zhivaDeviceChecks(GameTestHelper h) {
        BlockPos rel = new BlockPos(0, 1, 0);
        h.setBlock(rel, VivaRegistries.IMPLANT_DEVICE.get());
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer p = player(h);

        p.setPos(Vec3.atCenterOf(abs).add(40, 0, 0));
        h.assertTrue(ZhivaNetwork.perform(p, abs, ZhivaNetwork.Action.REMOVE) == Zhiva.Result.WRONG_STATE, "далеко — отказ");
        h.assertTrue(Zhiva.state(p) == ZhivaState.IMPLANTED, "далеко — состояние не меняется");

        p.setPos(Vec3.atCenterOf(abs).add(1.5, 0, 0));
        h.assertTrue(ZhivaNetwork.perform(p, abs.above(), ZhivaNetwork.Action.REMOVE) == Zhiva.Result.WRONG_STATE, "не устройство — отказ");
        h.assertTrue(ZhivaNetwork.perform(p, abs, ZhivaNetwork.Action.REMOVE) == Zhiva.Result.OK, "рядом — удалено");
        h.assertTrue(countChips(p) == 1, "чип выдан");
        h.assertTrue(ZhivaNetwork.perform(p, abs, ZhivaNetwork.Action.INSERT) == Zhiva.Result.OK, "вставлено");
        h.assertTrue(Zhiva.state(p) == ZhivaState.REVEALED, "REVEALED");
        h.succeed();
    }

    /** Без Chromeware ничего не падает: проверки отвечают «нет», посадка в лодку — не кушетка. */
    @GameTest(template = EMPTY)
    public static void zhivaWithoutChromewareSafe(GameTestHelper h) {
        ServerPlayer p = player(h);
        boolean kiroshi = ChromewareCompat.hasCyberOptics(p);
        if (!ChromewareCompat.isLoaded()) {
            h.assertTrue(!kiroshi, "без Chromeware Kiroshi = нет");
        }
        h.assertTrue(!ChromewareCompat.isBroken(), "обёртка Chromeware не сломалась: " + ChromewareCompat.isLoaded());
        Boat boat = h.spawn(EntityType.BOAT, new BlockPos(1, 2, 1));
        h.assertTrue(!ChromewareCompat.isSurgerySeat(boat), "лодка — не кушетка");
        p.setPos(boat.position());
        p.startRiding(boat, true);
        h.assertTrue(!Zhiva.data(p).foreignImplant(), "посадка в лодку не открывает квест");

        // команды есть по-русски и по-английски
        CommandDispatcher<CommandSourceStack> d = p.server.getCommands().getDispatcher();
        CommandSourceStack src = p.server.createCommandSourceStack();
        List<String> ru = d.getCompletionSuggestions(d.parse("viva жива ", src)).join().getList().stream().map(Suggestion::getText).toList();
        h.assertTrue(ru.containsAll(List.of("статус", "состояние", "кушетка")), "подкоманды жива: " + ru);
        List<String> en = d.getCompletionSuggestions(d.parse("viva zhiva ", src)).join().getList().stream().map(Suggestion::getText).toList();
        h.assertTrue(en.containsAll(List.of("status", "state", "couch")), "подкоманды zhiva: " + en);
        h.succeed();
    }

    /**
     * С Chromeware в run/mods: игрок садится на сущность-сиденье кушетки chromeware:surgery_seat → квест открыт.
     * Без Chromeware тест просто проходит (проверять нечего).
     */
    @GameTest(template = EMPTY)
    public static void zhivaCouchSeatMount(GameTestHelper h) {
        if (!ChromewareCompat.isLoaded()) {
            h.succeed();
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ChromewareCompat.SURGERY_SEAT);
        h.assertTrue(!BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(BuiltInRegistries.ENTITY_TYPE.getDefaultKey()),
                "сущность chromeware:surgery_seat зарегистрирована");
        Entity seat = type.create(h.getLevel());
        h.assertTrue(seat != null, "сиденье создано");
        seat.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        h.getLevel().addFreshEntity(seat);
        ServerPlayer p = player(h);
        p.setPos(seat.position());
        h.assertTrue(p.startRiding(seat, true), "игрок сел на сиденье");
        h.assertTrue(Zhiva.data(p).foreignImplant(), "посадка на кушетку открыла квест");
        // Kiroshi у нового игрока нет — и обёртка не сломалась на настоящем Chromeware
        h.assertTrue(!ChromewareCompat.hasCyberOptics(p) && !ChromewareCompat.isBroken(), "Kiroshi = нет, обёртка цела");
        seat.discard();
        h.succeed();
    }

    /**
     * Тест 33: оптика для показа Живы — любая из 7 оптик Kiroshi. С Chromeware в run/mods ставим игроку в слот
     * лица базовую оптику, потом «Часового» (sentry) — оба дают true; пустой слот — false.
     * Слот ставится рефлексией в attachment chromeware:face (CyberwareSlots / InstalledImplant).
     * Без Chromeware — проверка отвечает «нет».
     */
    @GameTest(template = EMPTY)
    public static void zhivaCyberOptics(GameTestHelper h) throws Exception {
        ServerPlayer p = player(h);
        if (!ChromewareCompat.isLoaded()) {
            h.assertTrue(!ChromewareCompat.hasCyberOptics(p) && ChromewareCompat.opticsVariants() == 0, "без Chromeware — нет");
            h.succeed();
            return;
        }
        h.assertTrue(ChromewareCompat.opticsVariants() == 7, "вариантов оптики: " + ChromewareCompat.opticsVariants());
        h.assertTrue(!ChromewareCompat.hasCyberOptics(p), "без оптики — нет");
        for (String optic : List.of("kiroshi_optics", "sentry")) {
            setFaceImplant(p, optic);
            h.assertTrue(ChromewareCompat.hasCyberOptics(p), "оптика " + optic + " → да");
        }
        setFaceImplant(p, null);
        h.assertTrue(!ChromewareCompat.hasCyberOptics(p) && !ChromewareCompat.isBroken(), "слот пуст → нет, обёртка цела");
        h.succeed();
    }

    /** Поставить имплант Chromeware в слот лица 0 (или очистить), рефлексией — без компиляции против Chromeware. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setFaceImplant(ServerPlayer p, @Nullable String implantId) throws Exception {
        ClassLoader loader = ZhivaGameTests.class.getClassLoader();
        Class<?> slotsClass = Class.forName("com.chromeware.cyberware.CyberwareSlots", true, loader);
        Class<?> installedClass = Class.forName("com.chromeware.cyberware.InstalledImplant", true, loader);
        java.util.Map<Integer, Object> sockets = new java.util.HashMap<>();
        if (implantId != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chromeware", implantId)));
            if (stack.isEmpty()) throw new IllegalStateException("нет предмета chromeware:" + implantId);
            sockets.put(0, installedClass.getConstructor(ItemStack.class).newInstance(stack));
        }
        Object slots = slotsClass.getConstructor(java.util.Map.class).newInstance(sockets);
        var type = net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chromeware", "face"));
        if (type == null) throw new IllegalStateException("нет attachment chromeware:face");
        p.setData((net.neoforged.neoforge.attachment.AttachmentType) type, slots);
    }
}
