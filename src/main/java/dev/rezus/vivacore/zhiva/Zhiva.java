package dev.rezus.vivacore.zhiva;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.zhiva.device.ZhivaChipItem;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Твист с имплантом Живы (заметка «02 Сюжет и Жива», тест 36). Вся логика серверная:
 * экран устройства только просит, решение принимается здесь.
 */
public final class Zhiva {
    private Zhiva() {}

    /** Достижение-отметка «открыт квест на устройство». Без отображения и без тоста; квест FTB потом проверит его. */
    public static final ResourceLocation FOREIGN_IMPLANT_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "zhiva/foreign_implant");
    public static final String FOREIGN_IMPLANT_CRITERION = "found";

    public static ZhivaData data(Player player) {
        return player.getData(VivaRegistries.ZHIVA);
    }

    public static ZhivaState state(Player player) {
        return data(player).state();
    }

    public static void setState(Player player, ZhivaState state) {
        player.setData(VivaRegistries.ZHIVA, data(player).withState(state));
    }

    // ---------------------------------------------------------------- шаг 2: кушетка

    /**
     * Игрок лёг на кушетку операций Chromeware (или оператор вызвал «/viva zhiva couch»).
     * Только в первый раз: сообщение и отметка-достижение.
     *
     * @return true, если это был первый раз
     */
    public static boolean onCouch(ServerPlayer player) {
        ZhivaData data = data(player);
        if (data.foreignImplant()) {
            return false;
        }
        player.setData(VivaRegistries.ZHIVA, data.withForeignImplant(true));
        ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.couch.foreign_implant"));
        AdvancementHolder advancement = player.server.getAdvancements().get(FOREIGN_IMPLANT_ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, FOREIGN_IMPLANT_CRITERION);
        } else {
            VivaCore.LOGGER.warn("Нет достижения {} — квест на устройство не откроется", FOREIGN_IMPLANT_ADVANCEMENT);
        }
        return true;
    }

    // ---------------------------------------------------------------- шаг 3: устройство

    public enum Result { OK, WRONG_STATE, NO_CHIP, FOREIGN_CHIP }

    /** IMPLANTED → REMOVED, игроку чип (если инвентарь полон — выпадает рядом). */
    public static Result removeImplant(ServerPlayer player) {
        if (state(player) != ZhivaState.IMPLANTED) {
            return Result.WRONG_STATE;
        }
        setState(player, ZhivaState.REMOVED);
        ItemHandlerHelper.giveItemToPlayer(player, ZhivaChipItem.create(player));
        return Result.OK;
    }

    /** Есть ли у игрока свой чип / только чужой / никакого. */
    public enum ChipStatus { NONE, OWN, FOREIGN_ONLY }

    public static ChipStatus chipStatus(Player player) {
        boolean foreign = false;
        for (ItemStack stack : player.getInventory().items) {
            if (ZhivaChipItem.belongsTo(stack, player)) return ChipStatus.OWN;
            if (ZhivaChipItem.isChip(stack)) foreign = true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (ZhivaChipItem.belongsTo(stack, player)) return ChipStatus.OWN;
            if (ZhivaChipItem.isChip(stack)) foreign = true;
        }
        return foreign ? ChipStatus.FOREIGN_ONLY : ChipStatus.NONE;
    }

    /** REMOVED + свой чип в руке или в инвентаре → чип забирается, REVEALED. Чужой чип не принимается. */
    public static Result insertChip(ServerPlayer player) {
        if (state(player) != ZhivaState.REMOVED) {
            return Result.WRONG_STATE;
        }
        ItemStack own = findOwnChip(player);
        if (own.isEmpty()) {
            return chipStatus(player) == ChipStatus.FOREIGN_ONLY ? Result.FOREIGN_CHIP : Result.NO_CHIP;
        }
        own.shrink(1);
        setState(player, ZhivaState.REVEALED);
        return Result.OK;
    }

    /** Свой чип: сначала в руках, потом в инвентаре. */
    private static ItemStack findOwnChip(Player player) {
        if (ZhivaChipItem.belongsTo(player.getMainHandItem(), player)) return player.getMainHandItem();
        if (ZhivaChipItem.belongsTo(player.getOffhandItem(), player)) return player.getOffhandItem();
        for (ItemStack stack : player.getInventory().items) {
            if (ZhivaChipItem.belongsTo(stack, player)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
