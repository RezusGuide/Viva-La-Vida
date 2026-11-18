package dev.rezus.vivacore.orion;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Предметы ОРИОНа: ключ, записка с кодом, детали армии (рецептов и дропа пока нет).
 * Иконки — от агента моделей; у кого есть маска, светится слой маски (модель neoforge:item_layers).
 */
public final class OrionItems {
    private OrionItems() {}

    /** Латунный ключ двери ОРИОНа. Не тратится. */
    public static final DeferredItem<Item> BRASS_KEY = VivaRegistries.ITEMS.register("brass_key",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** Записка с кодом доступа: код в подсказке берётся из конфига orion.accessCode. */
    public static final DeferredItem<Item> CODE_NOTE = VivaRegistries.ITEMS.register("code_note",
            () -> new CodeNoteItem(new Item.Properties().stacksTo(1)));

    // --- детали ОРИОНа (заметка «16 Враги-армия»: что выпадает из армии) ---
    public static final DeferredItem<Item> OPTICAL_SENSOR = part("optical_sensor", Rarity.COMMON);
    public static final DeferredItem<Item> SERVO = part("servo", Rarity.COMMON);
    public static final DeferredItem<Item> COMBAT_CHIP = part("combat_chip", Rarity.COMMON);
    public static final DeferredItem<Item> NANITE_SUSPENSION = part("nanite_suspension", Rarity.COMMON);
    public static final DeferredItem<Item> TURBINE_MODULE = part("turbine_module", Rarity.COMMON);
    public static final DeferredItem<Item> POWER_ACTUATOR = part("power_actuator", Rarity.COMMON);
    public static final DeferredItem<Item> ARMOR_PLATE = part("armor_plate", Rarity.COMMON);
    public static final DeferredItem<Item> ORION_CORE = part("orion_core", Rarity.RARE);
    public static final DeferredItem<Item> SERVO_INFECTED = part("servo_infected", Rarity.COMMON);
    public static final DeferredItem<Item> ARMOR_PLATE_INFECTED = part("armor_plate_infected", Rarity.COMMON);

    public static List<Supplier<? extends Item>> all() {
        return List.of(BRASS_KEY, CODE_NOTE, OPTICAL_SENSOR, SERVO, COMBAT_CHIP, NANITE_SUSPENSION, TURBINE_MODULE,
                POWER_ACTUATOR, ARMOR_PLATE, ORION_CORE, SERVO_INFECTED, ARMOR_PLATE_INFECTED);
    }

    private static DeferredItem<Item> part(String name, Rarity rarity) {
        return VivaRegistries.ITEMS.register(name, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    public static void init() {
    }

    /** Записка: строка с кодом из конфига (серверный конфиг NeoForge синхронизируется клиентам). */
    public static class CodeNoteItem extends Item {
        public CodeNoteItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("vivacore.orion.note.code", VivaConfig.orionAccessCode())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
