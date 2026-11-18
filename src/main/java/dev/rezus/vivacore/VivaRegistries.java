package dev.rezus.vivacore;

import dev.rezus.vivacore.zhiva.ZhivaData;
import dev.rezus.vivacore.zhiva.device.ImplantDeviceBlock;
import dev.rezus.vivacore.zhiva.device.ZhivaChipItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Все регистрации Viva Core: блоки, предметы, компоненты данных, attachment игрока. */
public final class VivaRegistries {
    private VivaRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VivaCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VivaCore.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, VivaCore.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VivaCore.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VivaCore.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VivaCore.MOD_ID);

    /** Блок-сущность двери ОРИОНа (анимация сдвига створки). */
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<dev.rezus.vivacore.orion.block.OrionDoorBlockEntity>> ORION_DOOR_BE =
            BLOCK_ENTITIES.register("orion_door", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(dev.rezus.vivacore.orion.block.OrionDoorBlockEntity::new,
                            dev.rezus.vivacore.orion.block.OrionBlocks.ORION_DOOR.get())
                    .build(null));

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VivaCore.MOD_ID);

    // --- Жива (заметка «02 Сюжет и Жива», тест 36) ---

    /** Состояние импланта Живы у игрока. Переживает смерть и перезаход, синхронизируется только самому игроку. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ZhivaData>> ZHIVA = ATTACHMENTS.register("zhiva",
            () -> AttachmentType.builder(() -> ZhivaData.DEFAULT)
                    .serialize(ZhivaData.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, ZhivaData.STREAM_CODEC)
                    .build());

    /** Записка с кодом ОРИОНа уже выдана игроку (один раз за всю игру, переживает смерть). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ORION_NOTE_GIVEN =
            ATTACHMENTS.register("orion_note_given", () -> AttachmentType.builder(() -> false)
                    .serialize(com.mojang.serialization.Codec.BOOL)
                    .copyOnDeath()
                    .build());

    /** Владелец чипа «Жива». */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ZhivaChipItem.Owner>> CHIP_OWNER =
            DATA_COMPONENTS.register("chip_owner", () -> DataComponentType.<ZhivaChipItem.Owner>builder()
                    .persistent(ZhivaChipItem.Owner.CODEC)
                    .networkSynchronized(ZhivaChipItem.Owner.STREAM_CODEC)
                    .build());

    public static final DeferredBlock<ImplantDeviceBlock> IMPLANT_DEVICE = BLOCKS.register("implant_device",
            () -> new ImplantDeviceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 6f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> IMPLANT_DEVICE_ITEM = ITEMS.registerSimpleBlockItem(IMPLANT_DEVICE);

    /** Чип не горит (fireResistant), стак 1. Остальная защита — в ZhivaChipItem. В творческие вкладки не добавляется. */
    public static final DeferredItem<ZhivaChipItem> ZHIVA_CHIP = ITEMS.register("zhiva_chip",
            () -> new ZhivaChipItem(new Item.Properties().stacksTo(1).fireResistant()));

    /** Вкладка Viva Core: тестовые предметы. Чип Живы сюда не добавляется (скрыт). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.vivacore"))
                    .icon(() -> new ItemStack(dev.rezus.vivacore.army.ArmyEntities.egg(dev.rezus.vivacore.army.MachineType.SCOUT_DRONE).get()))
                    .displayItems((params, out) -> {
                        for (var mt : dev.rezus.vivacore.army.MachineType.values()) out.accept(dev.rezus.vivacore.army.ArmyEntities.egg(mt).get());
                        out.accept(IMPLANT_DEVICE_ITEM.get());
                        dev.rezus.vivacore.orion.block.OrionBlocks.items().forEach(i -> out.accept(i.get()));
                        dev.rezus.vivacore.orion.OrionItems.all().forEach(i -> out.accept(i.get()));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        dev.rezus.vivacore.orion.block.OrionBlocks.init();
        dev.rezus.vivacore.orion.OrionItems.init();
        dev.rezus.vivacore.army.ArmyEntities.init();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(dev.rezus.vivacore.army.ArmyEntities::onAttributes);
    }
}
