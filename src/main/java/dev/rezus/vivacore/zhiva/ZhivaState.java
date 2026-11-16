package dev.rezus.vivacore.zhiva;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Состояние импланта Живы (заметка «02 Сюжет и Жива», раздел «Твист с имплантом на Chromeware»).
 * IMPLANTED → (устройство: удалить) → REMOVED → (устройство: вставить чип) → REVEALED.
 */
public enum ZhivaState implements StringRepresentable {
    /** С начала игры: имплант в мозге, Жива скрывает, что она ИИ. */
    IMPLANTED,
    /** Имплант вынут, у игрока чип. Жива молчит совсем. */
    REMOVED,
    /** Чип вставлен обратно, игрок узнал правду. Жива помогает прямо. */
    REVEALED;

    public static final Codec<ZhivaState> CODEC = StringRepresentable.fromEnum(ZhivaState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
