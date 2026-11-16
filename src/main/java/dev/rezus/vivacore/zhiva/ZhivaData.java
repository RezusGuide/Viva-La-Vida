package dev.rezus.vivacore.zhiva;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Данные Живы на игроке (attachment vivacore:zhiva). Неизменяемая запись: каждое изменение — player.setData(...),
 * чтобы NeoForge отправил синхронизацию игроку.
 *
 * @param state          состояние импланта
 * @param foreignImplant игрок уже ложился на кушетку и получил сообщение «обнаружен посторонний имплант»
 */
public record ZhivaData(ZhivaState state, boolean foreignImplant) {
    public static final ZhivaData DEFAULT = new ZhivaData(ZhivaState.IMPLANTED, false);

    public static final Codec<ZhivaData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ZhivaState.CODEC.optionalFieldOf("state", ZhivaState.IMPLANTED).forGetter(ZhivaData::state),
            Codec.BOOL.optionalFieldOf("foreign_implant", false).forGetter(ZhivaData::foreignImplant)
    ).apply(i, ZhivaData::new));

    public static final StreamCodec<ByteBuf, ZhivaData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> ZhivaState.values()[i], ZhivaState::ordinal), ZhivaData::state,
            ByteBufCodecs.BOOL, ZhivaData::foreignImplant,
            ZhivaData::new);

    public ZhivaData withState(ZhivaState newState) {
        return new ZhivaData(newState, foreignImplant);
    }

    public ZhivaData withForeignImplant(boolean found) {
        return new ZhivaData(state, found);
    }
}
