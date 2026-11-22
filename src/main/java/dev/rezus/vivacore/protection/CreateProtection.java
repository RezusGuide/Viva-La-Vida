package dev.rezus.vivacore.protection;

import com.simibubi.create.api.contraption.BlockMovementChecks;

/**
 * Защита построек от контрапций Create (тест 34).
 * <ul>
 *   <li>Сдвиг: у Create есть API — BlockMovementChecks.registerMovementAllowedCheck. Блок в коробке постройки →
 *       FAIL: поршень, подшипник, портал, лебёдка не соберут контрапцию с ним (сборка падает с «блок не двигается»).</li>
 *   <li>Разрушение дрелью / пилой / катком: события у Create нет (BlockHelper.destroyBlock не шлёт BreakEvent) —
 *       закрыто миксинами {@code CreateBlockHelperMixin}, {@code CreateBreakingMovementMixin},
 *       {@code CreateBreakingBlockEntityMixin}.</li>
 * </ul>
 */
public final class CreateProtection {
    private CreateProtection() {}

    public static void register() {
        BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                SiteProtection.isProtected(level, pos) ? BlockMovementChecks.CheckResult.FAIL : BlockMovementChecks.CheckResult.PASS);
    }
}
