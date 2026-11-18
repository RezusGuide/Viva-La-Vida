package dev.rezus.vivacore.orion.block;

import dev.rezus.vivacore.VivaRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Блоки обломков корабля и лаборатории ОРИОНа (заметки «17 Три стороны конфликта», «16 Враги-армия»).
 * Модели и текстуры — от агента моделей (исходники .bbmodel в art/blocks/).
 * Прочность: обшивка, стены и металлические блоки — как железо; ящик — как дерево.
 */
public final class OrionBlocks {
    private OrionBlocks() {}

    /** Как блок железа: 5 / 6, звук металла, нужен инструмент (кирка, от каменной — тег needs_stone_tool). */
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    /** Металл с формой не во весь блок: свет проходит, мобы не спавнятся сверху. */
    private static BlockBehaviour.Properties metalShaped() {
        return metal().noOcclusion();
    }

    // --- обломки корабля ---
    public static final DeferredBlock<Block> HULL_PANEL = cube("hull_panel");
    public static final DeferredBlock<Block> HULL_PANEL_BURNT = cube("hull_panel_burnt");
    public static final DeferredBlock<Block> HULL_PANEL_DENTED = cube("hull_panel_dented");
    public static final DeferredBlock<Block> HULL_VENT = cube("hull_vent");
    /** Ящик снабжения — как дерево: 2.5, звук дерева, топор, без требования инструмента. */
    public static final DeferredBlock<Block> SUPPLY_CRATE = VivaRegistries.BLOCKS.register("supply_crate",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)));
    public static final DeferredBlock<EmergencyLightBlock> EMERGENCY_LIGHT = VivaRegistries.BLOCKS.register("emergency_light",
            () -> new EmergencyLightBlock(metalShaped().strength(1.5f, 6f).lightLevel(s -> 12)));
    public static final DeferredBlock<EmergencyLightBlock> EMERGENCY_LIGHT_RED = VivaRegistries.BLOCKS.register("emergency_light_red",
            () -> new EmergencyLightBlock(metalShaped().strength(1.5f, 6f).lightLevel(s -> 12)));
    public static final DeferredBlock<FacingShapedBlock> CABLE_BUNDLE = VivaRegistries.BLOCKS.register("cable_bundle",
            () -> new FacingShapedBlock(metalShaped().strength(1.5f, 3f), OrionShapes.CABLE_BUNDLE, true));
    public static final DeferredBlock<FacingShapedBlock> DEAD_CONSOLE = VivaRegistries.BLOCKS.register("dead_console",
            () -> new FacingShapedBlock(metalShaped(), OrionShapes.DEAD_CONSOLE, false));
    public static final DeferredBlock<CryoCapsuleBlock> CRYO_CAPSULE = VivaRegistries.BLOCKS.register("cryo_capsule",
            () -> new CryoCapsuleBlock(metalShaped().pushReaction(PushReaction.BLOCK)));

    // --- лаборатория ОРИОНа ---
    public static final DeferredBlock<Block> ORION_WALL = cube("orion_wall");
    public static final DeferredBlock<Block> ORION_WALL_HAZARD = cube("orion_wall_hazard");
    public static final DeferredBlock<Block> ORION_FLOOR = cube("orion_floor");
    public static final DeferredBlock<OrionDoorBlock> ORION_DOOR = VivaRegistries.BLOCKS.register("orion_door",
            () -> new OrionDoorBlock(metalShaped().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<CodeTerminalBlock> CODE_TERMINAL = VivaRegistries.BLOCKS.register("code_terminal",
            () -> new CodeTerminalBlock(metalShaped()));

    // --- предметы блоков ---
    public static final DeferredItem<BlockItem> HULL_PANEL_ITEM = item(HULL_PANEL);
    public static final DeferredItem<BlockItem> HULL_PANEL_BURNT_ITEM = item(HULL_PANEL_BURNT);
    public static final DeferredItem<BlockItem> HULL_PANEL_DENTED_ITEM = item(HULL_PANEL_DENTED);
    public static final DeferredItem<BlockItem> HULL_VENT_ITEM = item(HULL_VENT);
    public static final DeferredItem<BlockItem> SUPPLY_CRATE_ITEM = item(SUPPLY_CRATE);
    public static final DeferredItem<BlockItem> EMERGENCY_LIGHT_ITEM = item(EMERGENCY_LIGHT);
    public static final DeferredItem<BlockItem> EMERGENCY_LIGHT_RED_ITEM = item(EMERGENCY_LIGHT_RED);
    public static final DeferredItem<BlockItem> CABLE_BUNDLE_ITEM = item(CABLE_BUNDLE);
    public static final DeferredItem<BlockItem> DEAD_CONSOLE_ITEM = item(DEAD_CONSOLE);
    public static final DeferredItem<BlockItem> CRYO_CAPSULE_ITEM = item(CRYO_CAPSULE);
    public static final DeferredItem<BlockItem> ORION_WALL_ITEM = item(ORION_WALL);
    public static final DeferredItem<BlockItem> ORION_WALL_HAZARD_ITEM = item(ORION_WALL_HAZARD);
    public static final DeferredItem<BlockItem> ORION_FLOOR_ITEM = item(ORION_FLOOR);
    public static final DeferredItem<DoubleHighBlockItem> ORION_DOOR_ITEM = VivaRegistries.ITEMS.register("orion_door",
            () -> new DoubleHighBlockItem(ORION_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CODE_TERMINAL_ITEM = item(CODE_TERMINAL);

    /** Все блоки ОРИОНа — для вкладки и тестов. */
    public static List<Supplier<? extends Block>> all() {
        return List.of(HULL_PANEL, HULL_PANEL_BURNT, HULL_PANEL_DENTED, HULL_VENT, SUPPLY_CRATE, EMERGENCY_LIGHT,
                EMERGENCY_LIGHT_RED, CABLE_BUNDLE, DEAD_CONSOLE, CRYO_CAPSULE, ORION_WALL, ORION_WALL_HAZARD, ORION_FLOOR,
                ORION_DOOR, CODE_TERMINAL);
    }

    public static List<Supplier<? extends Item>> items() {
        return List.of(HULL_PANEL_ITEM, HULL_PANEL_BURNT_ITEM, HULL_PANEL_DENTED_ITEM, HULL_VENT_ITEM, SUPPLY_CRATE_ITEM,
                EMERGENCY_LIGHT_ITEM, EMERGENCY_LIGHT_RED_ITEM, CABLE_BUNDLE_ITEM, DEAD_CONSOLE_ITEM, CRYO_CAPSULE_ITEM,
                ORION_WALL_ITEM, ORION_WALL_HAZARD_ITEM, ORION_FLOOR_ITEM, ORION_DOOR_ITEM, CODE_TERMINAL_ITEM);
    }

    public static VoxelShape implantDeviceShape() {
        return OrionShapes.IMPLANT_DEVICE;
    }

    private static DeferredBlock<Block> cube(String name) {
        return VivaRegistries.BLOCKS.register(name, () -> new Block(metal()));
    }

    private static DeferredItem<BlockItem> item(DeferredBlock<? extends Block> block) {
        return VivaRegistries.ITEMS.registerSimpleBlockItem(block);
    }

    /** Принудительно загрузить класс до регистрации. */
    public static void init() {
    }
}
