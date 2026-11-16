package dev.rezus.vivacore.zhiva.net;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.client.ZhivaClientHooks;
import dev.rezus.vivacore.zhiva.Zhiva;
import dev.rezus.vivacore.zhiva.ZhivaMessages;
import dev.rezus.vivacore.zhiva.ZhivaState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Пакеты терминала устройства. Экран только просит (TerminalAction), сервер проверяет блок, расстояние,
 * состояние и чип, делает действие и присылает свежий экран (OpenTerminal).
 */
public final class ZhivaNetwork {
    private ZhivaNetwork() {}

    private static <T> StreamCodec<ByteBuf, T> enumCodec(T[] values, java.util.function.ToIntFunction<T> ordinal) {
        return ByteBufCodecs.idMapper(i -> values[i], ordinal);
    }

    /** Сервер → клиент: открыть/обновить терминал. story — показать сюжет (сразу после вставки чипа). */
    public record OpenTerminal(BlockPos pos, ZhivaState state, Zhiva.ChipStatus chip, boolean story)
            implements CustomPacketPayload {
        public static final Type<OpenTerminal> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "zhiva_terminal_open"));
        public static final StreamCodec<ByteBuf, OpenTerminal> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenTerminal::pos,
                enumCodec(ZhivaState.values(), ZhivaState::ordinal), OpenTerminal::state,
                enumCodec(Zhiva.ChipStatus.values(), Zhiva.ChipStatus::ordinal), OpenTerminal::chip,
                ByteBufCodecs.BOOL, OpenTerminal::story,
                OpenTerminal::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum Action { REMOVE, INSERT }

    /** Клиент → сервер: просьба сделать действие в терминале. */
    public record TerminalAction(BlockPos pos, Action action) implements CustomPacketPayload {
        public static final Type<TerminalAction> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "zhiva_terminal_action"));
        public static final StreamCodec<ByteBuf, TerminalAction> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, TerminalAction::pos,
                enumCodec(Action.values(), Action::ordinal), TerminalAction::action,
                TerminalAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // обработчик клиента вызывает класс из client/ только на клиенте
        registrar.playToClient(OpenTerminal.TYPE, OpenTerminal.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ZhivaClientHooks.openTerminal(payload)));
        registrar.playToServer(TerminalAction.TYPE, TerminalAction.CODEC, ZhivaNetwork::handleAction);
    }

    public static void openTerminal(ServerPlayer player, BlockPos pos, boolean story) {
        // у клиента без Viva Core (и у тестового игрока) нашего канала нет — ничего не шлём
        if (player.connection == null || !player.connection.hasChannel(OpenTerminal.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new OpenTerminal(pos, Zhiva.state(player), Zhiva.chipStatus(player), story));
    }

    /** Может ли игрок пользоваться устройством в pos (блок на месте, рядом). */
    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos)
                && player.level().getBlockState(pos).is(VivaRegistries.IMPLANT_DEVICE.get())
                && player.canInteractWithBlock(pos, VivaConfig.zhivaDeviceReach());
    }

    private static void handleAction(TerminalAction payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            perform(player, payload.pos(), payload.action());
        });
    }

    /** Действие терминала с проверками. Отдельный метод — для GameTest. */
    public static Zhiva.Result perform(ServerPlayer player, BlockPos pos, Action action) {
        if (!canUse(player, pos)) {
            ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.device.too_far"));
            return Zhiva.Result.WRONG_STATE;
        }
        Zhiva.Result result = switch (action) {
            case REMOVE -> Zhiva.removeImplant(player);
            case INSERT -> Zhiva.insertChip(player);
        };
        switch (result) {
            case OK -> { }
            case FOREIGN_CHIP -> ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.device.foreign_chip"));
            case NO_CHIP -> ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.device.no_chip"));
            case WRONG_STATE -> ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.device.wrong_state"));
        }
        openTerminal(player, pos, result == Zhiva.Result.OK && action == Action.INSERT);
        return result;
    }
}
