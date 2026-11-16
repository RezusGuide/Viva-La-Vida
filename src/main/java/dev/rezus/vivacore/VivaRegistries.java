package dev.rezus.vivacore;

import dev.rezus.vivacore.zhiva.ZhivaData;
import dev.rezus.vivacore.zhiva.device.ImplantDeviceBlock;
import dev.rezus.vivacore.zhiva.device.ZhivaChipItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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

    // --- Жива (заметка «02 Сюжет и Жива», тест 36) ---

    /** Состояние импланта Живы у игрока. Переживает смерть и перезаход, синхронизируется только самому игроку. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ZhivaData>> ZHIVA = ATTACHMENTS.register("zhiva",
            () -> AttachmentType.builder(() -> ZhivaData.DEFAULT)
                    .serialize(ZhivaData.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, ZhivaData.STREAM_CODEC)
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

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
