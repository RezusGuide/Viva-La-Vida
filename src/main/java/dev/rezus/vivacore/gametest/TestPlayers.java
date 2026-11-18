package dev.rezus.vivacore.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

/**
 * Тестовый игрок для GameTest: ServerPlayer вне мира с «глухим» соединением.
 * makeMockServerPlayerInLevel не подходит: игрок попадает в мир, Create шлёт ему свои пакеты,
 * фальшивое соединение их не принимает — сервер падает. ServerPlayer.moveTo без соединения падает — используй setPos.
 */
public final class TestPlayers {
    private TestPlayers() {}

    private static int counter;

    /** Игрок у относительной позиции rel (центр блока). */
    public static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "viva_test_" + (++counter));
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, p, cookie) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            }
        };
        p.setPos(Vec3.atCenterOf(h.absolutePos(rel)));
        return p;
    }

    public static ServerPlayer player(GameTestHelper h) {
        return player(h, new BlockPos(0, 1, 0));
    }
}
