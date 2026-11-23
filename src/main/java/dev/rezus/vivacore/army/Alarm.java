package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Тревога участков (заметка «16 Враги-армия», раздел 3; тест 24). Мир делится на квадраты army.alarm.sectorSize.
 * Тревога 0–4: растёт, когда дрон видит игрока и когда игрок убивает машину ОРИОНа; падает со временем,
 * пока в участке нет игроков. Не растёт, если армия выключена или программа ОРИОНа отменена.
 * Игрокам по сети ничего не уходит — тревогу видит только оператор (/viva army status).
 */
public final class Alarm {
    private Alarm() {}

    public static String sector(Level level, BlockPos pos) {
        int size = VivaConfig.armySectorSize();
        return level.dimension().location() + "|" + Math.floorDiv(pos.getX(), size) + "|" + Math.floorDiv(pos.getZ(), size);
    }

    public static float get(ServerLevel level, BlockPos pos) {
        return AlarmData.get(level.getServer()).get(sector(level, pos));
    }

    /** Целый уровень тревоги 0–4. */
    public static int level(ServerLevel level, BlockPos pos) {
        return (int) Math.floor(get(level, pos));
    }

    /** Поднять тревогу участка pos. @return false, если армия неактивна (выключена или программа отменена) */
    public static boolean raise(ServerLevel level, BlockPos pos, double amount) {
        if (!Army.isActive(level.getServer()) || amount <= 0) return false;
        AlarmData data = AlarmData.get(level.getServer());
        String key = sector(level, pos);
        data.set(key, (float) (data.get(key) + amount));
        return true;
    }

    /** Оператор: задать уровень тревоги участка. */
    public static void set(ServerLevel level, BlockPos pos, float value) {
        AlarmData.get(level.getServer()).set(sector(level, pos), value);
    }

    /** Участки, в которых сейчас есть игроки (не наблюдатели). */
    public static Set<String> occupied(Collection<ServerPlayer> players) {
        Set<String> out = new HashSet<>();
        for (ServerPlayer p : players) {
            if (!p.isSpectator()) out.add(sector(p.level(), p.blockPosition()));
        }
        return out;
    }

    /** Спад тревоги за seconds секунд во всех участках без игроков. Вызывается раз в секунду. */
    public static void decay(MinecraftServer server, Set<String> occupied, double seconds) {
        AlarmData data = AlarmData.get(server);
        double step = VivaConfig.armyAlarmDecay() / 60.0 * seconds;
        for (var e : data.all().entrySet()) {
            if (!occupied.contains(e.getKey())) {
                data.set(e.getKey(), (float) (e.getValue() - step));
            }
        }
    }
}
