package dev.rezus.vivacore.zhiva.device;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.rezus.vivacore.VivaRegistries;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Чип-имплант «Жива»: появляется, когда игрок удаляет имплант в устройстве. Привязан к UUID своего игрока
 * (компонент vivacore:chip_owner) — чужой чип устройство не принимает.
 * <p>
 * Не горит (fireResistant в свойствах), не получает урона ни от чего — огонь, лава, взрыв, кактус
 * (canBeHurtBy = false), не исчезает на земле (бесконечная жизнь предмета). Скрыт: нет в творческих вкладках,
 * в JEI скрыт тегом c:hidden_from_recipe_viewers.
 */
public class ZhivaChipItem extends Item {

    /** Владелец чипа: UUID и ник на момент удаления (ник — только для подсказки). */
    public record Owner(UUID id, String name) {
        public static final Codec<Owner> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Owner::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Owner::name)
        ).apply(i, Owner::new));

        public static final StreamCodec<ByteBuf, Owner> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Owner::id,
                ByteBufCodecs.STRING_UTF8, Owner::name,
                Owner::new);
    }

    public ZhivaChipItem(Properties properties) {
        super(properties);
    }

    /** Новый чип игрока. */
    public static ItemStack create(Player owner) {
        ItemStack stack = new ItemStack(VivaRegistries.ZHIVA_CHIP.get());
        stack.set(VivaRegistries.CHIP_OWNER.get(), new Owner(owner.getUUID(), owner.getGameProfile().getName()));
        return stack;
    }

    @Nullable
    public static Owner owner(ItemStack stack) {
        return stack.get(VivaRegistries.CHIP_OWNER.get());
    }

    public static boolean isChip(ItemStack stack) {
        return stack.is(VivaRegistries.ZHIVA_CHIP.get());
    }

    public static boolean belongsTo(ItemStack stack, Player player) {
        Owner owner = owner(stack);
        return isChip(stack) && owner != null && owner.id().equals(player.getUUID());
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return false;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        // не исчезает со временем, пока лежит на земле
        if (entity.level() instanceof ServerLevel && entity.getAge() >= 0) {
            entity.setUnlimitedLifetime();
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Owner owner = owner(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("vivacore.zhiva.chip.owner", owner.name()).withStyle(ChatFormatting.GRAY));
        }
    }
}
