package dev.rezus.vivacore.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Одобренные дома игроков (испытание «Приятный дом», заметка «03 Этап 1 — Земля»).
 * Хранится в overworld, игроки — по UUID, переживает перезапуск сервера.
 * <p>
 * Правила: в доме от 1 до maxMembers игроков (конфиг home.maxMembers); игрок — только в одном доме.
 */
public final class HomeData extends SavedData {
    public static final String ID = "vivacore_homes";

    private static final SavedData.Factory<HomeData> FACTORY =
            new SavedData.Factory<>(HomeData::new, HomeData::load, null);

    /** Дома по id, в порядке одобрения. */
    private final Map<Integer, Home> homes = new LinkedHashMap<>();
    /** Последние известные ники игроков: для списка и для оффлайн-игроков. */
    private final Map<UUID, String> knownNames = new LinkedHashMap<>();
    private int nextId = 1;

    public static HomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    // ---------------------------------------------------------------- логика

    public enum Result { OK, EMPTY, TOO_MANY, DUPLICATE, ALREADY_HOUSED, TARGET_NOT_HOUSED, FULL }

    /**
     * @param result      итог
     * @param conflicting игрок, из-за которого отказ (для ALREADY_HOUSED / DUPLICATE)
     * @param home        созданный дом (для OK)
     */
    public record ApproveOutcome(Result result, @Nullable UUID conflicting, @Nullable Home home) {}

    /**
     * Одобрить новый дом для игроков.
     *
     * @param pos позиция дома (позиция одобряющего игрока-оператора или null, если одобрили из консоли) —
     *            заготовка: по этим координатам позже пойдут набеги жителей
     */
    public ApproveOutcome approve(List<UUID> members, int maxMembers, @Nullable GlobalPos pos) {
        if (members.isEmpty()) {
            return new ApproveOutcome(Result.EMPTY, null, null);
        }
        if (members.size() > maxMembers) {
            return new ApproveOutcome(Result.TOO_MANY, null, null);
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID member : members) {
            if (!seen.add(member)) {
                return new ApproveOutcome(Result.DUPLICATE, member, null);
            }
            if (homeOf(member).isPresent()) {
                return new ApproveOutcome(Result.ALREADY_HOUSED, member, null);
            }
        }
        Home home = new Home(nextId++, new ArrayList<>(members), pos);
        homes.put(home.id(), home);
        setDirty();
        return new ApproveOutcome(Result.OK, null, home);
    }

    /**
     * Добавить игрока в дом другого игрока (второй жилец).
     *
     * @param player   кого добавить — у него не должно быть дома
     * @param toPlayer в чей дом
     */
    public Result addMember(UUID player, UUID toPlayer, int maxMembers) {
        if (player.equals(toPlayer)) {
            return Result.DUPLICATE;
        }
        if (homeOf(player).isPresent()) {
            return Result.ALREADY_HOUSED;
        }
        Optional<Home> target = homeOf(toPlayer);
        if (target.isEmpty()) {
            return Result.TARGET_NOT_HOUSED;
        }
        Home home = target.get();
        if (home.members.size() >= maxMembers) {
            return Result.FULL;
        }
        home.members.add(player);
        setDirty();
        return Result.OK;
    }

    /** Задать место дома игрока. @return false, если у игрока нет дома */
    public boolean setPos(UUID player, @Nullable GlobalPos pos) {
        Optional<Home> found = homeOf(player);
        if (found.isEmpty()) {
            return false;
        }
        found.get().pos = pos;
        setDirty();
        return true;
    }

    /** Снять игрока с дома. Пустой дом удаляется. @return false, если игрок был без дома */
    public boolean remove(UUID player) {
        Optional<Home> found = homeOf(player);
        if (found.isEmpty()) {
            return false;
        }
        Home home = found.get();
        home.members.remove(player);
        if (home.members.isEmpty()) {
            homes.remove(home.id());
        }
        setDirty();
        return true;
    }

    public Optional<Home> homeOf(UUID player) {
        for (Home home : homes.values()) {
            if (home.members.contains(player)) {
                return Optional.of(home);
            }
        }
        return Optional.empty();
    }

    public Collection<Home> homes() {
        return Collections.unmodifiableCollection(homes.values());
    }

    public void rememberName(UUID player, String name) {
        if (!name.equals(knownNames.get(player))) {
            knownNames.put(player, name);
            setDirty();
        }
    }

    public Map<UUID, String> knownNames() {
        return Collections.unmodifiableMap(knownNames);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("nextId", nextId);
        ListTag homeList = new ListTag();
        for (Home home : homes.values()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.putInt("id", home.id());
            ListTag members = new ListTag();
            for (UUID member : home.members) {
                members.add(NbtUtils.createUUID(member));
            }
            homeTag.put("members", members);
            if (home.pos() != null) {
                GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, home.pos())
                        .ifSuccess(posTag -> homeTag.put("pos", posTag));
            }
            homeList.add(homeTag);
        }
        tag.put("homes", homeList);

        ListTag names = new ListTag();
        knownNames.forEach((uuid, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.putString("name", name);
            names.add(entry);
        });
        tag.put("knownNames", names);
        return tag;
    }

    public static HomeData load(CompoundTag tag, HolderLookup.Provider registries) {
        HomeData data = new HomeData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        for (Tag t : tag.getList("homes", Tag.TAG_COMPOUND)) {
            CompoundTag homeTag = (CompoundTag) t;
            List<UUID> members = new ArrayList<>();
            for (Tag m : homeTag.getList("members", Tag.TAG_INT_ARRAY)) {
                members.add(NbtUtils.loadUUID(m));
            }
            GlobalPos pos = null;
            if (homeTag.contains("pos")) {
                pos = GlobalPos.CODEC.parse(NbtOps.INSTANCE, homeTag.get("pos")).result().orElse(null);
            }
            Home home = new Home(homeTag.getInt("id"), members, pos);
            data.homes.put(home.id(), home);
            data.nextId = Math.max(data.nextId, home.id() + 1);
        }
        for (Tag t : tag.getList("knownNames", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            data.knownNames.put(entry.getUUID("uuid"), entry.getString("name"));
        }
        return data;
    }

    /** Для тестов: полное совпадение содержимого. */
    public boolean sameContent(HomeData other) {
        return nextId == other.nextId
                && List.copyOf(homes.values()).equals(List.copyOf(other.homes.values()))
                && knownNames.equals(other.knownNames);
    }

    // ---------------------------------------------------------------- дом

    public static final class Home {
        private final int id;
        private final List<UUID> members;
        @Nullable
        private GlobalPos pos;

        Home(int id, List<UUID> members, @Nullable GlobalPos pos) {
            this.id = id;
            this.members = members;
            this.pos = pos;
        }

        public int id() {
            return id;
        }

        public List<UUID> members() {
            return Collections.unmodifiableList(members);
        }

        /** Место дома (позиция одобрившего игрока или из «/viva home setpos»); null — не задано. Для набегов. */
        @Nullable
        public GlobalPos pos() {
            return pos;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Home h && id == h.id && members.equals(h.members) && Objects.equals(pos, h.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, members, pos);
        }
    }
}
