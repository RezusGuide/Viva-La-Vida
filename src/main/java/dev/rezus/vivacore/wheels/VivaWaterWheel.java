package dev.rezus.vivacore.wheels;

/**
 * Данные лимита, которые миксин добавляет в водяное колесо Create
 * ({@code WaterWheelBlockEntity}, а значит и в большое колесо).
 * Префикс vivacore$ — чтобы не столкнуться с именами других модов.
 */
public interface VivaWaterWheel {
    /** Порядок не задан (колесо ещё ни разу не тикало на сервере). */
    long NO_ORDER = Long.MIN_VALUE;

    /** Колесо сверх лимита: не даёт ни скорости, ни мощности, работает как вал. */
    boolean vivacore$isLimited();

    void vivacore$setLimited(boolean limited);

    /** Порядок подключения: игровое время первого тика колеса на сервере. Меньше — раньше, работает первым. */
    long vivacore$order();

    void vivacore$setOrder(long order);

    /** Проверить лимит в ближайший тик, не дожидаясь интервала (после загрузки). */
    boolean vivacore$consumeCheckNow();
}
