package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Пакеты армии (задача 9, тест 38): мгновенный выстрел — один пакет «выстрел из A в B» вместо сущности-пули
 * (создание, движение каждый тик, удаление, две частицы). Клиент рисует трассер, вспышку у ствола и искры в точке
 * попадания. Шлётся только игрокам ближе army.combat.tracerSendRange к началу или концу выстрела.
 */
public final class ArmyNetwork {
    private ArmyNetwork() {}

    /** Вид трассера: пуля пехоты/меха/вертолёта, выстрел снайпера. */
    public static final byte KIND_BULLET = 0;
    public static final byte KIND_SNIPER = 1;
    /** Куда пришёлся выстрел: никуда, в блок, в сущность. */
    public static final byte IMPACT_NONE = 0;
    public static final byte IMPACT_BLOCK = 1;
    public static final byte IMPACT_ENTITY = 2;

    public record Tracer(double fx, double fy, double fz, double tx, double ty, double tz, byte kind, byte impact)
            implements CustomPacketPayload {
        public static final Type<Tracer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "army_tracer"));
        public static final StreamCodec<ByteBuf, Tracer> CODEC = StreamCodec.of((buf, t) -> {
            buf.writeDouble(t.fx);
            buf.writeDouble(t.fy);
            buf.writeDouble(t.fz);
            buf.writeDouble(t.tx);
            buf.writeDouble(t.ty);
            buf.writeDouble(t.tz);
            buf.writeByte(t.kind);
            buf.writeByte(t.impact);
        }, buf -> new Tracer(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readByte(), buf.readByte()));

        public Vec3 from() {
            return new Vec3(fx, fy, fz);
        }

        public Vec3 to() {
            return new Vec3(tx, ty, tz);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Tracer.TYPE, Tracer.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rezus.vivacore.client.Tracers.add(p)));
    }

    /** Для тестов и замера: сколько пакетов трассеров отправлено. */
    static int sent;

    static void sendTracer(ServerLevel level, Vec3 from, Vec3 to, byte kind, byte impact) {
        double r = VivaConfig.armyTracerSendRange();
        double r2 = r * r;
        Tracer payload = null;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(from) > r2 && p.distanceToSqr(to) > r2) continue;
            // у клиента без Viva Core (и у тестовых игроков) нашего канала нет
            if (p.connection == null || !p.connection.hasChannel(Tracer.TYPE)) continue;
            if (payload == null) payload = new Tracer(from.x, from.y, from.z, to.x, to.y, to.z, kind, impact);
            PacketDistributor.sendToPlayer(p, payload);
            sent++;
        }
    }
}
