package dev.rezus.vivacore.orion.net;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.client.CodeTerminalClient;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.orion.block.CodeTerminalBlock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Пакеты терминала кода. Экран только присылает введённую строку; сервер проверяет блок, расстояние,
 * паузу и сам код и отвечает статусом.
 */
public final class OrionNetwork {
    private OrionNetwork() {}

    /** Сколько символов кода принимаем от клиента (защита от мусора). */
    public static final int MAX_CODE_LENGTH = 64;

    private static final StreamCodec<ByteBuf, Orion.Status> STATUS_CODEC =
            ByteBufCodecs.idMapper(i -> Orion.Status.values()[i], Orion.Status::ordinal);

    /** Сервер → клиент: открыть экран терминала. */
    public record OpenTerminal(BlockPos pos, Orion.Status status) implements CustomPacketPayload {
        public static final Type<OpenTerminal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "code_terminal_open"));
        public static final StreamCodec<ByteBuf, OpenTerminal> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenTerminal::pos, STATUS_CODEC, OpenTerminal::status, OpenTerminal::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент → сервер: введённый код. */
    public record Submit(BlockPos pos, String code) implements CustomPacketPayload {
        public static final Type<Submit> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "code_terminal_submit"));
        public static final StreamCodec<ByteBuf, Submit> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Submit::pos, ByteBufCodecs.stringUtf8(MAX_CODE_LENGTH), Submit::code, Submit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Сервер → клиент: результат ввода. */
    public record StatusUpdate(BlockPos pos, Orion.Status status) implements CustomPacketPayload {
        public static final Type<StatusUpdate> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "code_terminal_status"));
        public static final StreamCodec<ByteBuf, StatusUpdate> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, StatusUpdate::pos, STATUS_CODEC, StatusUpdate::status, StatusUpdate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenTerminal.TYPE, OpenTerminal.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> CodeTerminalClient.open(p.pos(), p.status())));
        registrar.playToClient(StatusUpdate.TYPE, StatusUpdate.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> CodeTerminalClient.status(p.pos(), p.status())));
        registrar.playToServer(Submit.TYPE, Submit.CODEC, OrionNetwork::handleSubmit);
    }

    public static void openTerminal(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        Orion.Status status = Orion.isProgramCancelled(player.server) || state.getOptionalValue(CodeTerminalBlock.UNLOCKED).orElse(false)
                ? Orion.Status.ALREADY_CANCELLED : Orion.Status.READY;
        send(player, new OpenTerminal(pos, status));
    }

    private static void handleSubmit(Submit payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Orion.Status status = Orion.submit(player, payload.pos(), payload.code());
            send(player, new StatusUpdate(payload.pos(), status));
        });
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        // у клиента без Viva Core (и у тестового игрока) нашего канала нет
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
