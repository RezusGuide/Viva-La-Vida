# Баланс имплантов Create: Chromeware 1.3.0 — разведка

Тест 7 в «09 Тесты» («как правим баланс»). Задача 3, часть Б. **Код и баланс не менялись.** Все значения ниже — то, что
стоит в Chromeware 1.3.0 по умолчанию. Источник — сам jar (`chromeware-1.3.0.jar`, лицензия All Rights Reserved; в git его нет):
enum `com.chromeware.cyberware.Implant`, `config/chromeware-*-defaults.json`, `assets/chromeware/lang`, `data/chromeware/recipe`.
Плюс конфиги, которые Chromeware сам создал при запуске dev-сервера и dev-клиента (NeoForge 21.1.264).

## 1. Конфиги

| Файл | Где лежит | Тип NeoForge | Что внутри |
| --- | --- | --- | --- |
| `chromeware-server.toml` | `config/` сервера (в dev: `run/config/`) | SERVER | Почти весь баланс: `[systems]` (вместимость), `[cyberpsychosis.*]` (человечность, психоз, иммуносупрессанты), `[implants.<id>]` — числа каждого импланта (87 разделов: 71 имплант + особые предметы), `[quickhacks.<id>]` (18), `[slots.<категория>]` (гнёзда, 9), `[bosses]`, `[launcher]` (чёрный список патронов PLS) |
| `chromeware-client.toml` | `config/` клиента | CLIENT | Только 4 переключателя визуала: `cyberpsychosis_vfx`, `time_slow_vfx`, `berserk_vfx`, `quickhack_vfx`. На баланс не влияет |
| `chromeware-implant-defaults.json`, `chromeware-quickhack-defaults.json`, `chromeware-slot-defaults.json` | **внутри jar** (`/config/…`) | — | Схема: значение по умолчанию, min, max и единица для каждого числа. Из неё строятся разделы `[implants]`, `[quickhacks]`, `[slots]` в server.toml. Править их не надо (и нельзя без перепаковки jar) — правим server.toml |

**Синхронизация с клиентом.** SERVER-конфиг NeoForge сам отправляет клиентам при входе. Кроме того, у Chromeware свой пакет
`ConfigSync`: каждый тик сервера он сравнивает снимок значений и рассылает изменения игрокам. Подсказки предметов показывают
изменённые числа (`ConfiguredStats.appendIfCustomized`).

**Нужен ли перезапуск.** По коду — нет. NeoForge следит за файлами конфигов и перечитывает их при изменении, а `ConfigSync`
разошлёт новые значения игрокам. Но это вывод по коду, **в игре не проверял**. Отдельно: число гнёзд `[slots]` и
`starting_capacity` могут по-разному вести себя для уже установленных имплантов и уже зашедших игроков. Это тоже не проверено.
Безопасный порядок — правка при остановленном сервере.

## 2. Что зашито в код

Большая часть чисел имплантов читается из конфига: урон, перезарядки, длительности, шансы, радиусы, бонусы. Ниже — то, для
чего ключа в конфиге **нет**. Найдено по `static final`-константам в байткоде и по подсказкам без ключей. Список не полный:
байткод не декомпилировался целиком.

| Что | Где в коде | Значение | Как поменять |
| --- | --- | --- | --- |
| Sandevistan: бонус скорости и высоты шага во время замедления | `Sandevistan.SPEED_BONUS`, `STEP_BONUS` | 0.5, 2.0 | только миксин |
| Sandevistan: замедление, длительность, перезарядка, радиус, крит, лечение | — | в конфиге `[implants.sandevistan]` | конфиг |
| Zetatech Berserk: неуязвимость, «только ближний бой», «нельзя использовать предметы» | `BerserkState` и логика | зашито | только миксин |
| Second Heart: «+100% здоровья при 0» | логика | зашито (в конфиге только перезарядка) | только миксин |
| Kiroshi: дальность сканера, ловушек, устройств, Stalker/Clairvoyant | `KiroshiOptics.*_RANGE`, `STALKER_DEPTH` | 20 / 30 / 40 / 20 / 50 блоков | только миксин |
| Mantis Blades: дальность удара, конус, скорость прыжка, тайминги | `MantisImplant.REACH`, `CONE`, `LEAP_SPEED`, `*_TICKS` | 3.5, 0.35, 1.25 … | миксин (урон и перезарядка — в конфиге) |
| Gorilla Arms: отталкивание ударной волны, досягаемость удара | `GorillaCombat.SHOCKWAVE_PUSH`, `SMASH_REACH` | 1.2, 3.0 | миксин (урон, шанс кровотечения, радиус — в конфиге) |
| Двойной прыжок | `MovementImplants.DOUBLE_JUMP` | 0.6 | миксин |
| Quantum Tuner: запас, восстановление, «в бою» | `QuantumTunerState.RESERVE`, `RECOVERY`, `COMBAT_GRACE` | 1000, 1200, 100 тиков | миксин |
| «В бою» после удара (для имплантов боя) | `InCombat.DURATION` | 200 тиков | миксин |
| +вместимость за каждое достижение Create | `CapacityProgress.PER_ADVANCEMENT` | 4 | миксин |
| Список имплантов и категорий | enum `Implant`, `ImplantCategory` | 71 и 9 | нельзя добавить даже миксином без большой переделки |
| Тексты подсказок | lang | — | ресурспак (просто) |

**Датапаком меняется:** рецепты (раздел 3), теги Chromeware (`data/chromeware/tags/...`: цели сканера, взрывчатка,
патроны PLS по тирам, тупое оружие Berserk), лут-таблицы и достижения Chromeware. Это простой и надёжный путь.
**Только миксином:** всё из таблицы выше. Хрупко: это внутренние классы, их имена и константы могут поменяться в любом
обновлении. Миксины не писались.

## 3. Рецепты

Все 107 рецептов — обычные JSON в `data/chromeware/recipe/` внутри jar: 80 `minecraft:crafting_shaped`,
16 `create:mechanical_crafting`, 8 `create:sequenced_assembly`, 3 `create:mixing`, 2 `create:filling`, 2 `create:emptying`.
**Переопределяются датапаком сборки** стандартно: файл с тем же путём, `data/chromeware/recipe/<id>.json`, в датапаке
(или в Viva Core / KubeJS). Убрать рецепт — тот же путь с `"neoforge:conditions": [{"type": "neoforge:false"}]`.

Уровень по ингредиентам (как прочитал генератор: латунь = латунь в рецепте, прецизионный механизм, механический крафтер или
сборка по шагам):
- **незерит:** Sandevistan, Zetatech Berserk, Chrome Compressor, квикхак Blackwall Gateway;
- **до латуни** (андезит, железо, медь — доступно на 3-й стадии): Kiroshi Optics, Rangeguard, Scarab, Stalker,
  Visual Cortex Support, квикхаки Overheat и Short Circuit, шприц и иммуносупрессанты;
- **латунь:** всё остальное, в том числе **кушетка операций** (`surgery_chair`). Без кушетки импланты не поставить,
  поэтому кушетка — естественный замок «Понимания».

## 4. Самые сильные («OP»)

Помечены **OP** в таблицах. Это особые предметы со своей механикой: Sandevistan, Mantis Blades, Monowire,
Projectile Launch System, Gorilla Arms. И сильные импланты из enum: Zetatech Berserk (неуязвимость), Optical Camo
(невидимость), Second Heart (второй запас здоровья), Blood Pump, Quantum Tuner, Cyberdeck (квикхаки, в том числе
летальные: Suicide, System Collapse, Cyberpsychosis, Blackwall Gateway). Список «OP по словам автора» взят из
задачи. Отдельного списка в самом моде нет.

## 5. Предложения по балансу — **предложение, решает Батыр**

В конфиги ничего не вписано. Идея: импланты открываются на 4-й стадии (латунь), а сильные требуют ресурсов поздних этапов.
Их не выключают.

1. **Замок стадии.** Кушетку операций и все импланты закрыть стадией «Понимание» через AStages. Кушетка и так на латуни,
   но пять имплантов (Kiroshi, Rangeguard, Scarab, Stalker, Visual Cortex Support) крафтятся раньше. Без замка их можно
   накопить до 4-й стадии.
2. **Сильные — за поздние ресурсы**, через переопределение рецептов датапаком:
   - Sandevistan, Zetatech Berserk — уже на незерите. Можно добавить предмет следующих этапов (нефть/топливо,
     материалы Энда) или шаг `create:sequenced_assembly`;
   - Mantis Blades, Monowire, Gorilla Arms, PLS — сейчас латунь. Предложение: механический крафтер плюс поздний
     материал (стержни ифрита — уже испытание 3-й стадии, незерит или материалы драконов, если берём IceAndFire);
   - Optical Camo, Second Heart, Cyberdeck — тоже поднять до незерита или до ресурса Энда.
3. **Цена в человечности вместо запрета.** Сильным поднять `capacity` в `[implants.<id>]`. Тогда при общей
   вместимости 38 (`starting_capacity`) нельзя будет поставить всё сразу, а превышение лимита ускоряет психоз
   (`overflow_multiplier`).
4. **Перезарядки сильных** — поднять `cooldown_seconds` у Sandevistan (30 с), Berserk (25 с), Optical Camo (50 с), если в
   тестах они окажутся слишком частыми. Это проверка теста 7 с MSPT и боем против армии.
5. **Летальные квикхаки** против боссов уже ограничены разделом `[bosses]`. Для наших боссов (Прото-Разум, Нексус,
   крейсер) стоит проверить, попадают ли они под `boss_max_health`.
6. **Не трогать конфигом** то, что зашито (раздел 2). Если что-то из этого ломает баланс (неуязвимость Berserk против
   армии), решаем отдельно: миксин хрупкий.

## 6. Таблица: 71 имплант (enum `Implant`)

Вместимость и броня — значения по умолчанию из конфига (броня в единицах Chromeware «half armor points»).
«Числа в конфиге» — ключи `[implants.<id>]`, кроме capacity и armor. Описание — подсказка из ru_ru.json мода (обрезано до ~260 знаков).

| Имплант | Категория | Вместимость | Броня | Что делает | Числа в конфиге (по умолчанию) | Рецепт | Уровень |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Модуль Ньютона (`newton_module`) | Лобная кора | 14 | 0.0 | При убийстве враждебного моба или игрока мгновенно сокращает перезарядку всех имплантов на 1,5%. | cooldown_refund_fraction=0.015 | верстак: c:andesite_alloy, c:brass_sheet, c:electron_tube, c:iron_sheet, clock (`newton_module.json`) | латунь |
| Бионические суставы (`bionic_joints`) | Скелет | 8 | 3.0 | Броня. Вот и всё. | — | верстак: c:brass_sheet, c:cogwheel, c:iron_sheet, c:shaft (`bionic_joints.json`) | латунь |
| Эпиморфный скелет (`epimorphic_skeleton`) | Скелет | 40 | 4.0 | +50% к максимальному здоровью | health_bonus=0.5 | механический крафтер: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:precision_mechanism, c:sturdy_sheet (`epimorphic_skeleton.json`) | латунь |
| Кинетический каркас (`kinetic_frame`) | Скелет | 16 | 2.5 | +20% к шансу смягчения урона, если сытость выше 80% | hunger_threshold=16.0, mitigation_chance=0.2 | верстак: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:precision_mechanism (`kinetic_frame.json`) | латунь |
| Para Bellum (`para_bellum`) | Скелет | 25 | 3.0 | +10% брони | armor_bonus=0.1 | сборка по шагам: c:brass_sheet, c:incomplete_precision_mechanism, c:precision_mechanism, c:sturdy_sheet (`para_bellum.json`) | латунь |
| Сращиватель рубцов (`scar_coalescer`) | Скелет | 20 | 1.0 | +20% брони, если здоровье ниже 50% | armor_bonus=0.2, health_threshold_fraction=0.5 | верстак: c:brass_sheet, c:copper_sheet, c:electron_tube, c:iron_sheet (`scar_coalescer.json`) | латунь |
| Пружинные суставы (`spring_joints`) | Скелет | 16 | 1.0 | +20% к силе смягчения урона | mitigation_strength_bonus=0.2 | верстак: c:andesite_alloy, c:brass_sheet, c:iron_sheet, piston (`spring_joints.json`) | латунь |
| Универсальный усилитель (`universal_booster`) | Скелет | 25 | 4.0 | Лечебные предметы теперь также дают: +10% брони на 6 сек. −30% расхода сытости на 5 сек. Эффект суммируется. | armor_bonus_per_stack=0.1, armor_duration_seconds=6, hunger_duration_seconds=5, hunger_multiplier=0.7 | сборка по шагам: c:brass_sheet, c:copper_sheet, c:fluid_pipe, c:incomplete_precision_mechanism, c:precision_mechanism (`universal_booster.json`) | латунь |
| Преобразователь адреналина (`adrenaline_converter`) | Нервная система | 6 | 0.0 | +60% к скорости передвижения на 9 сек. при вступлении в бой. | duration_seconds=9.0, movement_speed_bonus=0.6 | верстак: cw:empty_syringe, c:andesite_alloy, c:brass_sheet, c:copper_sheet, redstone (`adrenaline_converter.json`) | латунь |
| Адреналиновый триггер (`adreno_trigger`) | Нервная система | 20 | 0.0 | +40% к скорости передвижения на 35 сек. при вступлении в бой. | duration_seconds=35.0, movement_speed_bonus=0.4 | верстак: cw:adrenaline_converter, c:brass_sheet, c:electron_tube, observer (`adreno_trigger.json`) | латунь |
| NeoFiber (`neofiber`) | Нервная система | 14 | 0.0 | +10% к шансу смягчения урона +10% к силе смягчения урона | mitigation_chance=0.1, mitigation_strength_bonus=0.1 | верстак: c:brass_sheet, c:electron_tube, redstone, string (`neofiber.json`) | латунь |
| Стаббер (`stabber`) | Нервная система | 12 | 0.0 | +20% к критическому урону мечами, клинками и метательным оружием. | critical_damage_bonus=0.2 | верстак: c:andesite_alloy, c:brass_sheet, c:electron_tube, c:iron_sheet, c:zinc_nugget, iron_ingot (`stabber.json`) | латунь |
| Поддержка зрительной коры (`visual_cortex_support`) | Нервная система | 20 | 0.0 | Критический урон увеличивается с расстоянием до врага (макс. 30% на расстоянии 30 блоков) | critical_bonus_distance=30.0, maximum_critical_bonus=0.3 | верстак: c:copper_sheet, c:electron_tube, c:iron_sheet, spyglass (`visual_cortex_support.json`) | **до латуни** |
| Зрительный интерфейс дальнего поля (`deep_field_visual_interface`) | Нервная система | 40 | 0.0 | Критический урон увеличивается с расстоянием до врага (макс. 100% на расстоянии 100 блоков) | critical_bonus_distance=100.0, maximum_critical_bonus=1.0 | верстак: cw:visual_cortex_support, c:copper_sheet, c:electron_tube, c:precision_mechanism, amethyst_shard (`deep_field_visual_interface.json`) | латунь |
| Настройщик рефлексов (`reflex_tuner`) | Нервная система | 5 | 0.0 | Замедляет время на 60% на 4,5 сек., когда здоровье падает ниже 25%. | cooldown_seconds=35.0, duration_seconds=4.5, field_radius=48, health_threshold_fraction=0.25, slowdown_percent=60 | верстак: c:andesite_alloy, c:brass_sheet, c:iron_sheet, clock, comparator (`reflex_tuner.json`) | латунь |
| Ревульсор (`revulsor`) | Нервная система | 35 | 0.0 | Замедляет время на 60% на 4,5 сек., когда здоровье падает ниже 25%. Ваше передвижение не замедляется. | cooldown_seconds=35.0, duration_seconds=4.5, field_radius=48, health_threshold_fraction=0.25, slowdown_percent=60 | верстак: cw:reflex_tuner, c:copper_sheet, c:electron_tube, c:polished_rose_quartz, c:precision_mechanism (`revulsor.json`) | латунь |
| Усилитель адреналина (`adrenaline_booster`) | Кровеносная система | 14 | 0.0 | Восстанавливает 25% сытости после убийства врага оружием ближнего боя. | hunger_refill_fraction=0.25 | верстак: cw:empty_syringe, c:brass_sheet, c:copper_sheet, c:electron_tube, c:iron_sheet (`adrenaline_booster.json`) | латунь |
| Биомонитор (`biomonitor`) | Кровеносная система | 14 | 0.0 | Автоматически использует установленный кровяной насос, когда здоровье падает ниже 50%. Увеличивает исцеление насоса на 1 сердце. | extra_pump_healing=2.0, pump_health_threshold_fraction=0.5 | верстак: c:andesite_alloy, c:brass_sheet, c:stressometer, comparator (`biomonitor.json`) | латунь |
| Чёрная мамба (`black_mamba`) | Кровеносная система | 16 | 0.0 | Урон по отравленным врагам увеличивается на 20%. Вместо этого урон от яда удваивается. | poison_damage_multiplier=2.0, poisoned_target_damage_multiplier=1.2 | верстак: cw:empty_syringe, c:andesite_alloy, c:brass_sheet, c:electron_tube, c:iron_sheet, spider_eye (`black_mamba.json`) | латунь |
| Лечение при убийстве (`heal_on_kill`) | Кровеносная система | 10 | 0.0 | +7,5% здоровья при убийстве врага. | heal_fraction=0.075 | верстак: c:brass_sheet, c:electron_tube, c:iron_sheet, c:sturdy_sheet, c:zinc_nugget (`heal_on_kill.json`) | латунь |
| Изометрический стабилизатор (`isometric_stabilizer`) | Кровеносная система | 40 | 0.0 | −100% расхода сытости | hunger_multiplier=0.0 | верстак: cw:clutch_padding, c:brass_sheet, c:electron_tube, c:precision_mechanism, c:sturdy_sheet (`isometric_stabilizer.json`) | латунь |
| Микророторы (`microrotors`) | Кровеносная система | 12 | 0.0 | +25% к скорости атаки в ближнем бою. | attack_speed_bonus=0.25 | верстак: c:andesite_alloy, c:brass_sheet, c:cogwheel, c:propeller (`microrotors.json`) | латунь |
| Второе сердце (`second_heart`) **OP** | Кровеносная система | 30 | 0.0 | +100% здоровья, когда оно падает до 0. | cooldown_seconds=220.0 | механический крафтер: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:mechanical_pump, c:precision_mechanism, lightning_rod, totem_of_undying (`second_heart.json`) | латунь |
| ThreatEvac (`threat_evac`) | Кровеносная система | 5 | 0.0 | +50% к скорости передвижения, когда здоровье падает до 25%. Чем меньше здоровья, тем выше скорость (макс. +100%). | extra_low_health_bonus=0.5, health_threshold_fraction=0.25, movement_speed_bonus=0.5 | верстак: cw:empty_syringe, c:andesite_alloy, c:brass_sheet, c:copper_sheet (`threat_evac.json`) | латунь |
| Панцирь (`carapace`) | Покровная система | 16 | 2.5 | +3 к прочности брони при атаке сбоку или сзади | directional_toughness=3.0 | верстак: c:brass_sheet, c:sturdy_sheet (`carapace.json`) | латунь |
| Контроболочка (`countershell`) | Покровная система | 12 | 2.0 | +50% к шансу смягчения урона на 4 сек., если потеряно 35% здоровья за 3 сек. | cooldown_seconds=6.0, duration_seconds=4.0, mitigation_chance=0.5, trauma_threshold_fraction=0.35, trauma_window_seconds=3 | верстак: c:andesite_alloy, c:brass_sheet, comparator (`countershell.json`) | латунь |
| Редактор боли (`pain_editor`) | Покровная система | 35 | 3.0 | −10% получаемого урона. | damage_multiplier=0.9 | сборка по шагам: c:andesite_alloy, c:brass_sheet, c:copper_sheet, c:electron_tube, c:incomplete_precision_mechanism, c:precision_mechanism (`pain_editor.json`) | латунь |
| Индуктор боли (`painducer`) | Покровная система | 30 | 4.0 | Преобразует 30% полученного урона в периодический урон. | deferred_damage_fraction=0.3, duration_seconds=5 | сборка по шагам: c:brass_sheet, c:incomplete_precision_mechanism, c:precision_mechanism, c:sturdy_sheet, clock (`painducer.json`) | латунь |
| Обратная периферия (`peripheral_inverse`) | Покровная система | 30 | 1.5 | Чем ближе существо, тем меньше урона оно вам наносит. −40% получаемого урона на расстоянии 3 блоков. Снижение урона постепенно уменьшается до 0% на расстоянии 6 блоков. | damage_reduction_fraction=0.4, falloff_end_range=6, full_strength_range=3 | верстак: cw:proxishield, c:brass_sheet, c:polished_rose_quartz, c:precision_mechanism (`peripheral_inverse.json`) | латунь |
| ProxiShield (`proxishield`) | Покровная система | 10 | 1.0 | Чем ближе существо, тем меньше урона оно вам наносит. −20% получаемого урона на расстоянии 3 блоков. Снижение урона постепенно уменьшается до 0% на расстоянии 6 блоков. | damage_reduction_fraction=0.2, falloff_end_range=6, full_strength_range=3 | верстак: c:brass_sheet, c:electron_tube, observer (`proxishield.json`) | латунь |
| RangeGuard (`rangeguard`) | Покровная система | 14 | 1.0 | +3 брони, если в радиусе 6 блоков нет враждебных мобов или игроков. | conditional_armor_multiplier=3.0, enemy_radius=6.0 | верстак: c:andesite_alloy, c:iron_sheet, c:zinc_nugget, observer (`rangeguard.json`) | **до латуни** |
| Shock-n-Awe (`shock_n_awe`) | Покровная система | 25 | 2.5 | При получении урона с шансом 20% выпускает мощный электроразряд, который наносит 10 урона ближайшим существам. | discharge_chance=0.2, discharge_damage=10.0, discharge_radius=6.0 | верстак: c:brass_sheet, c:copper_sheet, c:electron_tube, c:iron_sheet, lightning_rod (`shock_n_awe.json`) | латунь |
| Подкожная броня (`subdermal_armor`) | Покровная система | 5 | 1.5 | Броня. Вот и всё. | — | верстак: c:brass_sheet (`subdermal_armor.json`) | латунь |
| Оптический камуфляж (`optical_camo`) **OP** | Покровная система | 20 | 1.0 | Делает вас невидимым на 7 секунд. | cooldown_seconds=50.0, duration_seconds=7.0 | сборка по шагам: c:brass_sheet, c:electron_tube, c:incomplete_precision_mechanism, c:polished_rose_quartz, c:precision_mechanism, amethyst_shard (`optical_camo.json`) | латунь |
| Хитин (`chitin`) | Покровная система | 50 | 5.0 | Удваивает естественное восстановление здоровья. | natural_healing_multiplier=2.0 | механический крафтер: c:brass_sheet, c:electron_tube, c:precision_mechanism, c:sturdy_sheet (`chitin.json`) | латунь |
| Сухожилия Дженкинса (`jenkins_tendons`) | Ноги | 6 | 1.5 | Увеличивает скорость бега: от +100% до +20% после 5 сек. непрерывного бега. Вне бега эффект восстанавливается с той же скоростью. | fatigue_seconds=5, initial_sprint_bonus=1.0, sprint_bonus_decay=0.8 | верстак: c:belt_connector, c:brass_sheet, c:copper_sheet (`jenkins_tendons.json`) | латунь |
| Связочная система Лероя (`leeroy_ligament_system`) | Ноги | 8 | 1.0 | +40% к скорости передвижения | movement_speed_bonus=0.4 | верстак: c:belt_connector, c:brass_sheet, c:cogwheel, c:copper_sheet, c:iron_sheet (`leeroy_ligament_system.json`) | латунь |
| Лапы рыси (`lynx_paws`) | Ноги | 5 | 1.0 | Передвижение на 100% тише; не активирует скалк +50% к скорости передвижения при приседании −20% урона от падения | crouch_speed_bonus=0.5, fall_damage_multiplier=0.8 | верстак: c:brass_sheet, c:copper_sheet, white_wool (`lynx_paws.json`) | латунь |
| Укреплённые лодыжки (`fortified_ankles`) | Ноги | 6 | 2.5 | Позволяют заряжать прыжок для увеличения дальности. Удерживайте кнопку прыжка для зарядки, затем отпустите для прыжка. | base_jump_velocity=0.42, charge_seconds=2, charged_jump_velocity_bonus=0.58 | верстак: c:brass_sheet, c:iron_sheet, piston (`fortified_ankles.json`) | латунь |
| Усиленные сухожилия (`reinforced_tendons`) | Ноги | 8 | 0.5 | Нажмите прыжок в воздухе, чтобы выполнить двойной прыжок. Восстанавливается при приземлении. | double_jump_strength=0.6 | верстак: c:belt_connector, c:brass_sheet, c:copper_sheet, piston (`reinforced_tendons.json`) | латунь |
| Обмотка рукояти (`handle_wrap`) | Кисти | 8 | 0.0 | +30% урона оружия дальнего боя | ranged_damage_multiplier=1.3 | верстак: c:brass_sheet, leather, string (`handle_wrap.json`) | латунь |
| Амортизатор (`shock_absorber`) | Кисти | 12 | 0.0 | −25% отдачи огнестрельного оружия. | recoil_multiplier=0.75 | верстак: c:brass_sheet, c:copper_sheet, c:zinc_nugget, piston (`shock_absorber.json`) | латунь |
| Неподвижная сила (`immovable_force`) | Кисти | 35 | 0.0 | −35% отдачи и −25% разброса пуль. | recoil_multiplier=0.65, spread_multiplier=0.75 | сборка по шагам: c:brass_sheet, c:incomplete_precision_mechanism, c:iron_sheet, c:precision_mechanism, c:sturdy_sheet, piston (`immovable_force.json`) | латунь |
| Микрогенератор (`microgenerator`) | Кисти | 9 | 0.0 | После перезарядки пустого оружия следующий выстрел вызывает чёртов удар молнии. Число ударов растёт с количеством заряженных пуль (макс. 5). | maximum_charges=5 | верстак: c:brass_sheet, c:cogwheel, c:copper_sheet, c:precision_mechanism, lightning_rod (`microgenerator.json`) | латунь |
| Амортизационная подкладка (`clutch_padding`) | Кровеносная система | 12 | 0.0 | −50% расхода сытости | hunger_multiplier=0.5 | верстак: c:brass_sheet, c:iron_sheet, leather, string (`clutch_padding.json`) | латунь |
| Контур обратной связи (`feedback_circuit`) | Кровеносная система | 14 | 0.0 | +3,5% здоровья и сытости при попадании по врагу полностью заряженным оружием дальнего боя. | restore_fraction=0.035 | верстак: c:andesite_alloy, c:brass_sheet, c:electron_tube, c:iron_sheet, comparator, redstone (`feedback_circuit.json`) | латунь |
| Электромагнитный переработчик (`electromag_recycler`) | Кровеносная система | 40 | 0.0 | +5% здоровья и сытости при попадании по врагу полностью заряженным оружием дальнего боя. | restore_fraction=0.05 | верстак: cw:feedback_circuit, c:brass_sheet, c:electron_tube, c:precision_mechanism, lightning_rod (`electromag_recycler.json`) | латунь |
| Оптика Kiroshi «Сталкер» (`stalker`) | Лицо | 2 | 0.0 | Позволяет сканировать окружение. Подсвечивает врагов за укрытиями в радиусе 20 блоков и до 50 блоков по вертикали. Ночное зрение включается в колесе способностей. Эффект сохраняется 5 секунд после сканирования. | effect_seconds=5, horizontal_range=20.0, vertical_range=50.0 | верстак: cw:clairvoyant, c:electron_tube, amethyst_shard (`stalker.json`) | **до латуни** |
| Оптика Kiroshi «Часовой» (`sentry`) | Лицо | 2 | 0.0 | Позволяет сканировать окружение. Подсвечивает красную пыль и устройства в радиусе 40 блоков. Ночное зрение включается в колесе способностей. Эффект сохраняется 5 секунд после сканирования. | device_range=40.0, effect_seconds=5 | верстак: cw:kiroshi_optics, c:brass_sheet, c:redstone_link, comparator (`sentry.json`) | латунь |
| Оптика Kiroshi «Оракул» (`the_oracle`) | Лицо | 10 | 0.0 | Позволяет сканировать окружение. Подсвечивает взрывоопасные блоки и ловушки возле прицела в радиусе 30 блоков. Подсвечивает красную пыль и устройства в радиусе 40 блоков. Подсвечивает врагов за укрытиями в радиусе 20 блоков и до 50 блоков по вертикали. Ночное  | effect_seconds=5 | механический крафтер: cw:doomsayer, cw:sentry, cw:stalker, c:precision_mechanism, c:sturdy_sheet, amethyst_shard (`the_oracle.json`) | латунь |
| Оптика Kiroshi «Ясновидец» (`clairvoyant`) | Лицо | 8 | 0.0 | Позволяет сканировать окружение. Подсвечивает врагов в радиусе 20 блоков. Ночное зрение включается в колесе способностей. Эффект сохраняется 5 секунд после сканирования. | effect_seconds=5, enemy_range=20.0 | верстак: cw:kiroshi_optics, c:brass_sheet, c:electron_tube, amethyst_shard (`clairvoyant.json`) | латунь |
| Оптика Kiroshi «Кокатрис» (`cockatrice`) | Лицо | 30 | 0.0 | Позволяет сканировать окружение. Увеличивает критический урон на 35%. Ночное зрение включается в колесе способностей. Эффект сохраняется 5 секунд после сканирования. | critical_damage_bonus=0.35, effect_seconds=5 | сборка по шагам: cw:kiroshi_optics, c:brass_sheet, c:incomplete_precision_mechanism, c:polished_rose_quartz, c:precision_mechanism, amethyst_shard (`cockatrice.json`) | латунь |
| Оптика Kiroshi «Вестник смерти» (`doomsayer`) | Лицо | 2 | 0.0 | Позволяет сканировать окружение. Подсвечивает взрывоопасные блоки и ловушки возле прицела в радиусе 30 блоков. Ночное зрение включается в колесе способностей. Эффект сохраняется 5 секунд после сканирования. | effect_seconds=5, trap_range=30.0 | верстак: cw:kiroshi_optics, c:brass_sheet, amethyst_shard, observer (`doomsayer.json`) | латунь |
| Компрессор хрома (`chrome_compressor`) | Операционная система | 0 | 0.0 | +70 к вместимости имплантов | capacity_bonus=70 | механический крафтер: c:brass_sheet, c:iron_sheet, c:precision_mechanism, c:zinc_ingot, netherite_ingot, piston (`chrome_compressor.json`) | незерит |
| Берсерк (`zetatech_berserk`) **OP** | Операционная система | 35 | 0.0 | Пока активен: Неуязвимость Нельзя использовать предметы Только ближний бой +30% к скорости атаки −100% расхода сытости +50% к скорости передвижения Урон увеличивается при низком здоровье (макс. +50% урона при здоровье ниже 20%) Когда эффект заканчивается: +25% | attack_speed_bonus=0.3, cooldown_seconds=25.0, duration_seconds=11.0, heal_per_kill_fraction=0.25, maximum_bonus_missing_health_fraction=0.8, maximum_melee_damage_bonus_fraction=0.5, movement_speed_bonus=0.5, slam_damage=6.0, slam_knockback=0.8, slam_radius=4.0 | механический крафтер: cw:empty_syringe, c:andesite_alloy, c:brass_sheet, c:copper_sheet, c:electron_tube, c:precision_mechanism, c:sturdy_sheet, c:zinc_nugget, netherite_ingot (`zetatech_berserk.json`) | незерит |
| Инжектор тирозина (`tyrosine_injector`) | Нервная система | 8 | 0.0 | Убийства дают: +10% к скорости передвижения на 15 сек. Суммируется до 10 раз. | duration_seconds=15.0, maximum_stacks=10, movement_speed_bonus=0.1 | верстак: cw:empty_syringe, c:andesite_alloy, c:brass_sheet, c:iron_sheet (`tyrosine_injector.json`) | латунь |
| Атомные датчики (`atomic_sensors`) | Нервная система | 5 | 0.0 | +60% к скорости передвижения, пока враждебный моб нацелен на вас или поблизости находится игрок. Эффект заканчивается при вступлении в бой. | enemy_radius=24, movement_speed_bonus=0.6 | верстак: c:brass_sheet, c:copper_sheet, observer, redstone (`atomic_sensors.json`) | латунь |
| Титановые кости (`titanium_bones`) | Скелет | 6 | 1.0 | Позволяют хранить предметы в сетке крафта 2×2 в инвентаре | — | верстак: c:andesite_alloy, c:brass_sheet, c:iron_sheet (`titanium_bones.json`) | латунь |
| Биопроводник (`bioconductor`) | Лобная кора | 16 | 0.0 | Позволяет скриптам наносить критический урон. +35% к шансу критического урона скриптов −4 к максимуму ОЗУ | quickhack_critical_chance=0.35, ram_penalty=4 | верстак: c:brass_sheet, c:electron_tube, c:iron_sheet, c:polished_rose_quartz, c:zinc_nugget (`bioconductor.json`) | латунь |
| Ex-Disk (`ex_disk`) | Лобная кора | 10 | 0.0 | +6 к максимуму ОЗУ +35% к скорости загрузки скриптов. | ram_bonus=6, upload_speed_multiplier=1.35 | верстак: c:andesite_alloy, c:brass_sheet, c:electron_tube, c:polished_rose_quartz (`ex_disk.json`) | латунь |
| Менеджер ОЗУ Camillo (`camillo_ram_manager`) | Лобная кора | 8 | 0.0 | Мгновенно восстанавливает 20% максимального ОЗУ, когда доступное ОЗУ падает до 20%. +2 к максимуму ОЗУ | cooldown_seconds=80.0, low_ram_threshold_fraction=0.2, ram_bonus=2, ram_refill_fraction=0.2 | верстак: c:brass_sheet, c:electron_tube, c:polished_rose_quartz, comparator (`camillo_ram_manager.json`) | латунь |
| Киберсоматический оптимизатор COX-2 (`cox_2_cybersomatic_optimizer`) | Лобная кора | 50 | 0.0 | Позволяет скриптам наносить критический урон. +100% к шансу критического урона скриптов −4 к максимуму ОЗУ | quickhack_critical_chance=1, ram_penalty=4 | верстак: cw:bioconductor, c:electron_tube, c:golden_sheet, c:precision_mechanism, diamond (`cox_2_cybersomatic_optimizer.json`) | латунь |
| Мехатронное ядро (`mechatronic_core`) | Лобная кора | 5 | 0.0 | +15% урона игрокам с киберконечностями. +2 к максимуму ОЗУ | cyberlimb_target_damage_bonus=0.15, ram_bonus=2 | верстак: c:brass_sheet, c:cogwheel, c:electron_tube, c:iron_sheet, redstone (`mechatronic_core.json`) | латунь |
| Усиление памяти (`memory_boost`) | Лобная кора | 18 | 0.0 | +1 ОЗУ при убийстве враждебного моба или игрока. +1 к максимуму ОЗУ | ram_bonus=1, ram_per_kill=1.0 | верстак: c:brass_sheet, c:copper_sheet, c:electron_tube, c:polished_rose_quartz (`memory_boost.json`) | латунь |
| Перераспределитель ОЗУ (`ram_reallocator`) | Лобная кора | 40 | 0.0 | Мгновенно восстанавливает 40% максимального ОЗУ, когда доступное ОЗУ падает до 20%. +2 к максимуму ОЗУ. | cooldown_seconds=85.0, low_ram_threshold_fraction=0.2, ram_bonus=2, ram_refill_fraction=0.4 | верстак: cw:camillo_ram_manager, c:electron_tube, c:golden_sheet, c:polished_rose_quartz, c:precision_mechanism (`ram_reallocator.json`) | латунь |
| Расширение ОЗУ (`ram_upgrade`) | Лобная кора | 8 | 0.0 | Увеличивает скорость восстановления ОЗУ на 0,2 ед./с. +2 к максимуму ОЗУ | ram_bonus=2, ram_recovery_per_second=0.2 | верстак: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:polished_rose_quartz, c:zinc_nugget (`ram_upgrade.json`) | латунь |
| Self-ICE (`self_ice`) | Лобная кора | 5 | 0.0 | Вражеским нетраннерам придётся взломать его, прежде чем скрипт сработает. Неудачный взлом бьёт их коротким замыканием. +2 к максимуму ОЗУ | cooldown_seconds=20.0, ram_bonus=2 | верстак: c:brass_sheet, c:copper_sheet, c:electron_tube, c:polished_rose_quartz (`self_ice.json`) | латунь |
| Feen-X (`feen_x`) | Скелет | 16 | 1.0 | +250% к скорости восстановления ОЗУ, когда доступно менее 7 ОЗУ | ram_threshold=7.0, recovery_multiplier=3.5 | верстак: c:brass_sheet, c:copper_sheet, c:electron_tube, c:iron_sheet, c:polished_rose_quartz (`feen_x.json`) | латунь |
| Возврат ОЗУ (`ram_recoup`) | Скелет | 14 | 1.0 | Получение урона восстанавливает ОЗУ в размере 20% полученного урона. +2 к максимуму ОЗУ | damage_to_ram_fraction=0.2, ram_bonus=2 | верстак: c:andesite_alloy, c:brass_sheet, c:electron_tube, c:iron_sheet, c:polished_rose_quartz, c:zinc_nugget (`ram_recoup.json`) | латунь |
| Решётка Cogito (`cogito_lattice`) | Покровная система | 12 | 2.5 | +240% брони от этого импланта, когда доступно менее 10 ОЗУ | conditional_armor_multiplier=2.4, ram_threshold=10.0 | верстак: c:brass_sheet, c:polished_rose_quartz, c:zinc_ingot (`cogito_lattice.json`) | латунь |
| Базовая оптика Kiroshi (`kiroshi_optics`) | Лицо | 2 | 0.0 | Позволяет сканировать окружение. Ночное зрение включается в колесе способностей. | effect_seconds=5 | верстак: c:andesite_alloy, c:electron_tube, c:goggles, c:iron_sheet (`kiroshi_optics.json`) | **до латуни** |

## 7. Особые предметы (не в enum `Implant`, своя механика)

| Предмет | Вместимость | Что делает | Числа в конфиге (по умолчанию) | Рецепт | Уровень |
| --- | --- | --- | --- | --- | --- |
| Сандевистан (`sandevistan`) **OP** | 44 | Пока активен: Замедляет время на 90% (вас замедление не затрагивает) +20% к критическому урону Нейтрализация враждебного моба или игрока во время действия даёт: +10% к длительности +10% здоровья Длительность: 10 сек. | cooldown_seconds=30.0, critical_damage_bonus=0.2, duration_seconds=10.0, field_radius=48, heal_per_kill_fraction=0.1, kill_extension_seconds=1, slowdown_percent=90 | механический крафтер: c:brass_sheet, c:electron_tube, c:iron_sheet, c:precision_mechanism, c:rotation_speed_controller, c:zinc_nugget, clock, netherite_ingot (`sandevistan.json`) | незерит |
| Клинки богомола (`mantis_blades`) **OP** | 4 | Клинки богомола позволяют кромсать врагов быстрыми и смертоносными ударами. +10 к урону атаки С обоими клинками удерживайте атаку для зарядки и отпустите, чтобы совершить рывок (к выбранному врагу, если он есть) и ударить обоими клинками. Один рывок за прыжок; | cooldown_seconds=0.65, cross_cooldown_seconds=1, cross_damage=20.0, damage=10.0, leap_range=12.0, lunge_distance=6.25 | механический крафтер: c:brass_sheet, c:cogwheel, c:iron_sheet, c:precision_mechanism, c:shaft, c:sturdy_sheet (`mantis_blades.json`) | латунь |
| Моноструна (`monowire`) **OP** | 4 | Моноструна — имплант для замены руки, позволяющий хлестать нескольких врагов на расстоянии. +7 к пробивающему урону атаки | cooldown_seconds=0.6, cross_cooldown_seconds=1, cross_damage=14.0, damage=7.0 | механический крафтер: c:brass_sheet, c:copper_sheet, c:electron_tube, c:precision_mechanism, string (`monowire.json`) | латунь |
| Система запуска снарядов (`projectile_launch_system`) **OP** | 4 | Для стрельбы держите подходящие боеприпасы в инвентаре или возьмите конкретный боеприпас в руку с системой запуска. Перезарядка зависит от уровня снаряда. Перезарядка залпа суммируется. ЛКМ/ПКМ для выстрела; УДЕРЖИВАЙТЕ для зарядки залпа из 5 снарядов. | brass_nugget_damage=12.0, cooldown_seconds=0.5, copper_nugget_damage=8.0, experience_nugget_damage=3.0, gold_nugget_damage=7.0, iron_nugget_damage=10.0, tier_2_cooldown_seconds=1, tier_3_cooldown_seconds=3, tier_4_cooldown_seconds=8, tier_5_cooldown_seconds=20, volley_shots=5, zinc_nugget_damage=6.0 | механический крафтер: c:brass_sheet, c:cogwheel, c:precision_mechanism, c:sturdy_sheet, dispenser (`projectile_launch_system.json`) | латунь |
| Руки гориллы (`gorilla_arms`) **OP** | 4 | Руки гориллы — лёгкие, эффективные и мощные кибернетические замены рук. Удары в ближнем бою могут вызвать кровотечение. Враг с кровотечением теряет 5% максимального здоровья за 3 секунды и не может бегать или прыгать. +7 к урону ударов кулаком. Удары голыми ру | attack_damage_bonus=7.0, attack_speed=1.6, bleed_chance=0.2, bleed_duration_seconds=3, bleed_health_fraction=0.05, charged_cooldown_seconds=1, charged_damage_multiplier=1.5, cooldown_seconds=0.25, shockwave_radius=3.0 | механический крафтер: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:precision_mechanism, piston (`gorilla_arms.json`) | латунь |
| Выдвижной захват (`extendo_grip`) | 4 | — | knockback=2.0, reach=3.0 | нет рецепта в jar | — |
| Кибердека (`cyberdeck`) **OP** | 16 | Позволяет применять скрипты к врагам и устройствам во время сканирования. | base_ram=28, cooldown_seconds=30.0, duration_seconds=14.0, overclock_extension_per_kill_seconds=3, ram_recovery_per_second=0.5 | механический крафтер: c:brass_sheet, c:copper_sheet, c:electron_tube, c:iron_sheet, c:polished_rose_quartz, c:precision_mechanism, c:redstone_link, c:sturdy_sheet (`cyberdeck.json`) | латунь |
| Кровяной насос (`blood_pump`) **OP** | 15 | Мгновенно восстанавливает 3 сердца, затем по 0,5 сердца/с в течение 6 секунд. 2 заряда; каждый восстанавливается за 30 секунд | charges=2, cooldown_seconds=30.0, duration_seconds=6.0, healing_per_second=1.0, instant_healing=6.0 | верстак: c:andesite_alloy, c:brass_sheet, c:copper_sheet, c:fluid_pipe, c:mechanical_pump (`blood_pump.json`) | латунь |
| Аксолотль (`axolotl`) | 48 | При убийстве враждебного моба или игрока мгновенно сокращает перезарядку всех имплантов на 7,5%. | kill_refund_fraction=0.075 | верстак: cw:newton_module, c:brass_sheet, c:polished_rose_quartz, c:precision_mechanism, clock (`axolotl.json`) | латунь |
| Квантовый настройщик (`quantum_tuner`) **OP** | 45 | Когда другой имплант полностью израсходован, квантовый настройщик мгновенно восстанавливает его заряд (не более 50 сек.). −15% времени перезарядки всех остальных имплантов. | cooldown_reduction_fraction=0.15, cooldown_seconds=60.0, reserve_seconds=50 | механический крафтер: c:brass_sheet, c:electron_tube, c:polished_rose_quartz, c:precision_mechanism, c:rotation_speed_controller, clock (`quantum_tuner.json`) | латунь |
| Плотный костный мозг (`dense_marrow`) | 16 | +30% урона в ближнем бою, −20% расхода сытости. | hunger_multiplier=0.8, melee_damage_multiplier=1.3 | сборка по шагам: c:brass_sheet, c:incomplete_precision_mechanism, c:powdered_obsidian, c:precision_mechanism, c:sturdy_sheet (`dense_marrow.json`) | латунь |
| Rara Avis (`rara_avis`) | 45 | +40% брони | armor_bonus_fraction=0.4 | верстак: cw:para_bellum, c:brass_sheet, c:precision_mechanism, c:sturdy_sheet (`rara_avis.json`) | латунь |
| Скарабей (`scarab`) | 14 | При приседании: +%s брони −30% к скорости передвижения. | crouched_armor_multiplier=3.0, movement_speed_penalty_fraction=-0.3 | верстак: c:andesite_alloy, c:iron_sheet, c:sturdy_sheet (`scarab.json`) | **до латуни** |
| Киберрука (`cyber_arm`) | 0 | +2 к урону атаки | attack_damage_bonus=2.0, capacity_bonus=10, humanity_loss_percent=5.0 | механический крафтер: c:andesite_alloy, c:brass_sheet, c:cogwheel, c:iron_sheet, c:precision_mechanism, c:shaft (`cyber_arm.json`) | латунь |
| Кибернога (`cyber_leg`) | 0 | −25% урона от падения и +2 к безопасной высоте падения (обе ноги: −50%, +4) | capacity_bonus=10, fall_damage_reduction_fraction=0.25, humanity_loss_percent=5.0, safe_fall_distance=2.0 | механический крафтер: c:andesite_alloy, c:brass_sheet, c:iron_sheet, c:large_cogwheel, c:precision_mechanism, c:shaft (`cyber_leg.json`) | латунь |
| Кибертуловище (`cyber_torso`) | 0 | — | capacity_bonus=10, humanity_loss_percent=5.0 | механический крафтер: c:brass_sheet, c:electron_tube, c:iron_sheet, c:precision_mechanism, c:sturdy_sheet (`cyber_torso.json`) | латунь |
