# Viva Core — правила для сессий Claude Code

Мод Viva Core для сборки Minecraft «Viva La Vida». Ставится каждому игроку и на сервер.
Репозиторий: https://github.com/RezusGuide/Viva-La-Vida (локально `D:\viva-core`).

## Окружение
- Minecraft 1.21.1, NeoForge 21.1.264, Java 21, ModDevGradle (шаблон NeoForge MDK 1.21.1).
- mod id `vivacore`, пакет `dev.rezus.vivacore`.
- В сборке: Create 6.0.10, Create Aeronautics 1.3.2, Sable 2.0.6, GeckoLib 4.9.3, Jade 15.10.6, Curios 9.5.1.
- Create в dev: `com.simibubi.create:create-1.21.1:6.0.10-280:slim` (сборка 280 = git ac0c444, тот же коммит, что jar в инстансе),
  Ponder 1.0.82+mc1.21.1, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67. Версии — в `gradle.properties`.
- Create: Chromeware 1.3.0 — **необязательная** зависимость, подключена только рефлексией (`compat/ChromewareBridge`):
  build не требует jar Chromeware. Jar для изучения и тестов совместимости можно держать в корне (в git не кладём:
  All Rights Reserved; `/*.jar` в .gitignore) и временно класть в `run/mods`.
- GeckoLib 4.9.3: `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3` с
  `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` (sha1 совпадает с jar инстанса).
- Инстанс Prism с jar всех модов сборки:
  `C:\Users\give_\AppData\Roaming\PrismLauncher\instances\Viva La Vida\minecraft\mods`.
  Если зависимости нет на maven — бери jar оттуда (`compileOnly` / `localRuntime`), не качай с непонятных сайтов.
- Заметки о сборке (Obsidian): `D:\Obsidian\Viva La Vida` — **только читать, ничего не менять**.
  Главные: «06 Viva Core», «03 Этап 1 — Земля», «07 Книга знаний», «16 Враги-армия», «17 Три стороны конфликта», «09 Тесты».

## Команды Gradle
- `./gradlew build` — сборка.
- `./gradlew runServer` — dev-сервер (Create + Viva Core), мир в `run/world`. EULA в dev не спрашивается.
  Консоль сервера читает stdin: можно вводить команды (`viva home list`, `stop`) прямо в терминале,
  или подать их через pipe — так проверялись команды в задаче 1.
- `./gradlew runGameTestServer` — все GameTest с namespace `vivacore`, сервер выходит с ошибкой, если тест упал.
  GameTestServer быстрый (~40 с), но у него нет usercache — тесты не должны на него полагаться.
- Проверка совместимости с модами сборки: временно положить их jar в `run/mods` (папка в .gitignore),
  прогнать runGameTestServer / runServer, потом убрать. Так в задаче 2 проверено, что миксины колёс уживаются
  с Create Deep Seas (create_submarine 3.3.0), Sable 2.0.6 и Aeronautics 1.3.2. Внимание: моды с измерениями
  оставляют их в `run/world` — потом будет безвредная ERROR про неизвестное измерение.
- `./gradlew runClient` — dev-клиент.
- `./gradlew runClient -PvivaQuickPlay=<мир>` — клиент сам заходит в мир из `run/saves/<мир>` и включает
  `client/DevScreenshots`: 5 скриншотов в `run/screenshots/vivacore-dev-N.png`, потом клиент закрывается.
  Так агент смотрит модели в игре без ручного управления (картинки читаются инструментом Read). После 60 тиков хук
  держит поворот камеры (окно клиента ловит мышь пользователя). `-PvivaDevScreen=code_terminal` — последний кадр
  с открытым экраном терминала. `-PvivaDevAction=wave` — волна у игрока на тике 130; `-PvivaDevAction=profile` —
  средний MSPT встроенного сервера в лог (профиль встроенного сервера при этом не пишется — для разбивки нагрузки
  используй dedicated-сервер и `/perf start` из консоли: отчёт в `run/debug/profiling/*.zip`). Сцену ставит датапак
  мира (`run/saves/<мир>/datapacks/...`, функция в теге `minecraft:tick`). Свежий мир: временно `level-name` в
  `run/server.properties` → runServer → stop → перенести папку мира в `run/saves`.

## Структура
- `VivaCore` — точка входа; `VivaConfig` — серверный конфиг; `command/VivaCommands` — корень `/viva`.
- Модули (пакеты, в каждом `package-info.java` со ссылкой на заметку): `knowledge`, `home`, `army`, `zones`,
  `protection`, `wheels`, `zhiva`, `planets`.
- Lang: `src/main/resources/assets/vivacore/lang/{en_us,ru_ru}.json`.
- GameTest: в main-исходниках, `@GameTestHolder("vivacore")`, шаблоны `vivacore:empty`
  (`data/vivacore/structure/empty.nbt`, 1×1×1 воздух) и `vivacore:wheels` (12×8×14 воздух).
  Вода для колёс в тестах — пузырьковые столбы над песком душ (Create считает их потоком).
- Тесты 31 (`structures/SiteGameTests`), 32 (`zones/ZoneGameTests`), 34 (`protection/ProtectionGameTests`):
  каждый тест — в своей пачке, общее состояние построек/стадии/флагов сбрасывают в конце (`Structures.resetForTests`).
  Постройки в тестах ставятся далеко (x 30 000+), чтобы не задеть площадки других тестов.
- Тесты по «09 Тесты»: 35 — дома (`home/HomeGameTests`), 17 — лимит водяных колёс (`wheels/WheelGameTests`),
  36 — твист с имплантом Живы и 33 (любая из 7 оптик Kiroshi) — `zhiva/ZhivaGameTests`, 24 (первый шаг армии:
  дрон-разведчик) — `army/ArmyGameTests`. Тест 7 (баланс Chromeware) — разведка в `docs/chromeware-balance.md`.
- Армия (`army/`, тесты 24 и 27): 13 машин в `MachineType` (данные моделей и значения по умолчанию; баланс — конфиг
  `army.<id>.*`), основа `OrionMachine` (наземные), `FlyingMachine` (летающие), `ScoutDrone` (поднимает тревогу).
  Регистрация — `ArmyEntities`, рендереры — `client/MachineRenderer` (+ `Swarm`: одна сущность, N моделек — клиентский
  конфиг `army.swarmUnits`). Тревога участков — `Alarm`/`AlarmData`, волны и лимиты — `Waves`, выключатели и события —
  `Army` (армия активна = `army.enabled` и программа ОРИОНа не отменена). Дроп — `data/vivacore/loot_table/entities/`.
  Модели — `assets/vivacore/{geo,animations,textures}/entity/<id>*`, исходники — `art/models/<id>.bbmodel`.
  Цели машин добавляются в конструкторе `OrionMachine`, а не в `registerGoals` (тот вызывается до присвоения полей).
- Оружие (задача 7): `MachineCombat` (очереди, лазер снайпера, ракеты, аура роя), снаряд `OrionProjectile`
  (BULLET / PIERCING / SHELL / ROCKET, спрайты агента моделей), частицы `ArmyParticles` (вспышка, искры, обломки).
  Позиции стволов — данные в `MachineType.muzzles()` (pivot костей muzzle* из .geo.json; на сервере моделей нет).
  Числа — `army.<id>.*` (+ ракеты, щит, лазер, аура). Свои своих не бьют (`Factions.sameSide`).
- Размер машин (задача 11): масштаб модели и стволов `army.<id>.scale` (танк 2, вертолёт 2,6, мех 1,45), хитбокс
  `army.<id>.hitboxWidth / hitboxHeight` — через `OrionMachine.getDefaultDimensions` (размер типа сущности задан до
  загрузки конфига). Танк и мех (`drivesDirect`): если путь к цели не строится — едут напрямую
  (`army.combat.directDriveTicks`) и при включённом vivaArmyGriefing ломают перед собой листву, траву, цветы, саженцы,
  снег (`breakSoftBlocksAhead`, через LivingDestroyBlockEvent); высота шага 1. В волнах тяжёлым нужен блок свободного
  места вокруг хитбокса.
- Отмена программы ОРИОНа (задача 11): незаражённые машины не «спят», а отключаются в течение
  `army.combat.shutdownSeconds` (анимация death, без дропа и опыта); заражённые остаются.
- Вспышка выстрела — полуразмер `army.muzzleFlashSize` (клиент, 0.15); вспышки, трассеры и снаряды ближе
  `army.hideNearCamera` (1.5) к камере не рисуются — задача 11: «огромная жёлтая фигура перед камерой» у Батыра —
  скорее всего вспышка (квадрат 0,7 блока) или снаряд у самого лица; в dev точно не воспроизведена.
- Дверь ОРИОНа: створка сдвигается целиком (translate), не сжимается; открытая — в двух блоках над проёмом.
- Экономия в бою (задача 9, тест 38): пули (очереди, пулемёт вертолёта, снайпер) — мгновенный выстрел `Hitscan`
  без сущности: поиск сущности на всём луче, потом блоки только до неё; клиенту — один пакет `ArmyNetwork.Tracer`
  («из A в B», только игрокам ближе `army.combat.tracerSendRange`), клиент (`client/Tracers`) рисует черту, вспышку
  и искры (скорость/длина/толщина — клиентский конфиг `army.tracer*`). Сущностями остаются ракеты, снаряд танка и
  сквозной снаряд тяжёлых; лимиты `ArmyLimits`: снарядов-сущностей на участок `army.combat.maxProjectilesPerSector`
  (сверх — выстрел пропускается), взрывов за тик `army.combat.maxExplosionsPerTick` (сверх — только урон рядом, без
  блоков). `army.combat.hitscanBullets = false` — пули снова сущности (для сравнения нагрузки).
  Блоки — общая трассировка `ShotTrace` (Sable, pierceable / blast_proof). Замер: `/viva devbench combat <прогрев>
  <замер> [групп]` (только dev, `army/CombatBench`): группа = 4 неуязвимых разбойника + 20 машин, как в задаче 7;
  `Hitscan.STATS` — время мгновенных выстрелов и тиков снарядов. Главная цена боя — взрывы (~3 мс на взрыв).
- Правило игры `vivaArmyGriefing` (`ArmyRules`): вкл — взрывы ванильные (`ExplosionInteraction.TNT`, проходят через
  `ExplosionEvent.Detonate` — приват OPAC их фильтрует); выкл — танк и ракеты вертолёта стреляют сквозными (тег
  `vivacore:pierceable`, стоп на `vivacore:blast_proof`), и такие машины выбирают цели за лёгкой стеной.
- Трассировка снарядов — через `level.clip`, а не `BlockGetter.traverseBlocks`: `clip` подменяет Sable, и снаряд
  видит блоки кораблей. Точка попадания в блок корабля приходит в координатах участка корабля (plot) — `ShotTrace`
  сразу переводит её в мир через `compat/SableCompat` (рефлексия:
  `dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(Level, Vec3)` — так делает сам Sable в
  своём миксине взрывов); `getBlockPos()` остаётся позицией в участке (по ней читается блок). Взрывы Sable переносит в
  мир сам (миксин `ServerLevel.explode`). До задачи 9 искры падали в участок, а поиск сущностей шёл по лучу до точки
  участка (тысячи блоков).
- Три стороны (`Factions`): списки целей — `army.targets.orion` / `army.targets.infected`. Мобы Spore — по пространству
  имён `spore`. Захват заражением: машину убил моб Spore или заражённая → с шансом `army.infection.convertChance`
  встаёт заражённая версия (`MachineType.infectedVariant`).
- Волны — на участок (`Waves.tickAll`/`tickSector`), место — по тегу `vivacore:wave_spawn_ground`; под землёй
  (карта высот выше игрока) — только `fitsCave()` машины на его высоте ±8.
- Цели Mob оценивает только в тики с чётным `(tickCount + id)` — периодические проверки в `canUse` делать с учётом id.
- GameTest с машинами — каждый в своей пачке (batch): машины воюют между собой, а тесты одной пачки стоят рядом.
  Тест Sable (`SableHitGameTests`, необязательный) работает, только если Sable лежит в `run/mods`. Платформа
  `/sable spawn platform 5` — это 11×11; корабль падает, ложится на пол наклонно и «утопает» в нём до блока, а в
  плоском мире GameTest иногда проваливается сквозь пол (3 из 8 прогонов) — прицел строится от доски палубы (её позиция
  в участке → мир), выстрелы — сверху вниз, если корабль не лежит на полу, проверки палубы пропускаются (WARN в лог).
  `SableCompanion.getVelocity` ждёт точку в участке корабля, не в мире.
  Таран (`protection/SableRamGameTests`, необязательный): по коду Sable 2.0.6 ломает только блоки со свойством
  `sable:fragile` (тег `#sable:fragile`: листва, бамбук, арбуз, тыква, кактус, лёд, лилия) —
  `FragileBlockCallback.onHit` → `ServerLevel.destroyBlock`, без событий; блоки-взрывчатка взрываются через обычный
  взрыв (его фильтрует защита). В шаблонах построек хрупких блоков нет; закрыть хрупкую местность в отступе можно только
  миксином во внутренний класс Sable — не сделано.
  Модели плагина смотрят в −Z (так же, как сущности в игре — проверено скриншотами), правая сторона модели +X;
  имена костей left/right в моделях могут быть перепутаны — в коде к костям по именам не обращаться.
- Тестовый игрок в GameTest: **не** `makeMockServerPlayerInLevel` — Create шлёт игроку в мире свои пакеты, фальшивое
  соединение их не принимает, сервер падает. Бери `gametest/TestPlayers.player(h, rel)`: ServerPlayer вне мира с «глухим»
  соединением. `ServerPlayer.moveTo` без соединения падает — используй `setPos`.
- ОРИОН (`orion/`): блоки обломков корабля и лаборатории (`orion/block/OrionBlocks`), дверь с латунным замком
  (`OrionDoorBlock`: свой BlockSetType «не открывается рукой», редстоун игнорируется; створка уезжает вверх/вниз —
  рисует `client/OrionDoorRenderer` через блок-сущность нижней половины), терминал кода (`CodeTerminalBlock`,
  экран `client/CodeTerminalScreen`, пакеты `orion/net`), флаг мира «программа отменена» (`OrionData`,
  `Orion.isProgramCancelled(server)`), записка с кодом (attachment `vivacore:orion_note_given`), детали армии
  (`OrionItems`). Тест 34 (часть) — `orion/OrionGameTests`. Исходники моделей блоков — `art/blocks/`.
- Формы блоков (хитбоксы) — по моделям, для facing=north, поворот как у blockstate (`FacingShapedBlock.rotateClockwise`).
  Двухблочные блоки (капсула) — как дверь: половины lower/upper, лут только из нижней.
- Свечение моделей по маскам агента моделей (`*_glowmask.png`, прозрачный фон):
  блоки — копии элементов с текстурой маски, на 0.01 px больше, с `"neoforge_data": {"block_light": 15, "sky_light": 15}`
  и `render_type: cutout` (в 1.21.1 работает: NeoForge парсит neoforge_data у элементов);
  предметы — `"loader": "neoforge:item_layers"`, слой 1 = маска, `neoforge_data.layers.1` = 15/15.
  Генератор, которым это делалось в задаче 5, лежал в `run/tmp-task5/make_resources.py` (не в git).
- Постройки (`structures/`, тест 31): `SiteType` (crash / lab / nexus → имя .nbt), `SitePlanner` (угол из сида,
  противоположные точки, поиск суши по шуму биомов генератора — без генерации чанков), `Structures` (размещение по
  меткам, кеш для зон и защиты, `position(server, type)` для квестов), `SiteData` (SavedData: план, коробки, ядра,
  «Нексус уничтожен»), команды `SiteCommands`. Шаблоны-черновики собирает `StructureGenerator`
  (`/viva sites build <crash|lab|nexus>` — только в dev, пишет в `src/main/resources/data/vivacore/structure/`;
  из консоли с pipe — команда приходит без мира, генератор берёт overworld). Код размещения знает только имя файла
  и метки (structure_block, режим DATA, поле metadata): `vivacore:ground` (уровень земли), `core`, `player_spawn`,
  `guardian`, `guard`. Метки в мир не ставятся (BlockIgnoreProcessor). После шаблона — столбы фундамента до земли
  (`foundationMaxDepth`; у земли 3 блока — то, что под ней: земля под травой, песчаник под песком; выше — камень
  местности), расчистка нависающего грунта сверху (`clearAboveDepth`) и сведение краёв (`blendEdges`: полоса
  `edgeBlend` снаружи — smoothstep к местности, склон ≤ `edgeSlope` на блок, неровность ±1; уровень у края — верх
  природной земли ближайшего столбца коробки, у стен лаборатории — уровень земли постройки; кайма `edgeInner` внутри —
  только природная земля). У Нексуса и отсека края шаблона — structure_void: там остаётся местность.
  Поиск места (задача 9): суша по сетке `landGridStep` на всей площади (половина большей стороны шаблона +
  `landMargin`) и не островок — связная суша ≥ площадь × `islandFactor` (заливка по сетке 2 × шаг); ~70–120 мс на мир. Вода местности (озеро рядом —
  планировщик обходит только океан/реку/пляж по биомам) убирается из коробки, кольцо в 1 блок вокруг коробки
  засыпается — иначе вода затекает в постройку (найдено на скриншоте Нексуса у океана, тест `waterDoesNotFlood`).
- Когда ставятся: отсек — `ServerStartedEvent` (чанки спавна загружены), спавн мира → метка player_spawn, spawnRadius 0
  (иначе спавн уезжает на крышу: `adjustSpawnLocation` берёт верх карты высот — метка стоит под дырой в крыше).
  Лаборатория и Нексус — раз в секунду `hasChunk` чанка центра; загрузился → ставим (флаг в SavedData). В GameTestServer
  авторазмещения нет. «Поставить здесь» — без поворота (предсказуемые ракурсы dev-скриншотов).
- Стадия сервера (`stage/ServerStage`, временно до AStages): все проверки — только `ServerStage.get(server)`.
- Зоны влияния (`zones/`, тест 32): `InfluenceZones` (доли, решение на `MobSpawnEvent.PositionCheck` — только NATURAL,
  MONSTER, не машины и не Spore; две дистанции до ядер из кеша), `LabZone` (машина вместо пропущенного моба — 1:1,
  состав `zones.labComposition`; не работает, если армия неактивна: `army.enabled = false` по умолчанию или программа
  отменена — тогда вся зона лаборатории выключена, обычные мобы идут как обычно), `InfectionZone` (точка входа для
  Spore, пока заглушка — заражённые машины; после «Нексус уничтожен» зоны нет). Число машин для лимита — кеш на 5 с.
- Защита (`protection/`, тест 34): `SiteProtection` — события NeoForge (ломание, установка, инструменты, вытаптывание,
  ведро — через `RightClickItem` + луч, событий ведра в 21.1 нет; взрыв `Detonate`; `LivingDestroyBlockEvent`;
  `EntityMobGriefingEvent`; жемчуг и хорус `EntityTeleportEvent`). Create: сдвиг — API
  `BlockMovementChecks.registerMovementAllowedCheck` (`CreateProtection`); разрушение — миксины (событие у Create не
  шлётся: `BlockHelper.destroyBlock` зовёт `destroyBlockAs` с player = null, без BreakEvent). Ящики припасов — тег
  `vivacore:protection_exempt`. Оператор (уровень `protection.opPermissionLevel`) в творческом — может всё.
  Задача 9 (ванильные миксины, события нет): раздатчик снаружи внутрь (`DispenserBlockMixin`), огонь в коробке
  (`BaseFireBlockMixin` — любой огонь сразу убирается, кроме огня шаблона во время установки, `Structures.isBuilding`;
  `FireBlockMixin` — снаружи не сжигает блоки внутри), жидкость снаружи внутрь (`FlowingFluidMixin`), огниво и огненный
  заряд игрока — `RightClickBlock`. Create: разборка контрапции в коробку (`CreateContraptionMixin`). Огонь, который
  оператор ставит в коробку `/setblock`, тоже убирается.
- `runGameTestServer` и `runServer` работают с одной папкой `run/world` (тот же сид; площадки тестов ставятся в
  случайном далёком месте этого мира). Тесты задачи 8 в конце сбрасывают постройки (`Structures.resetForTests`), стадию
  и флаг ОРИОНа — и это сохраняется в `run/world`: после runGameTestServer следующий runServer считает, что построек
  нет, и ставит отсек заново. Для dev-мира неважно, но не держи в `run/world` ничего ценного. Блоки, которые тест
  ставит в мир, тоже остаются: тест, который строит рельеф, берёт новое случайное место каждый прогон.
- GameTest-площадки обнесены барьером: цели и машины за краем шаблона друг друга не видят и туда не пройдут.
- Сцены dev-скриншотов в старых мирах `run/saves`: если функция одного датапака не грузится (например, старая сцена
  вызывает переименованную команду), падает весь тег `minecraft:tick` — и новые сцены тоже не работают. Функции
  датапака выполняются с правами 2: dev-команды для сцен — с `requires(2)` (`/viva devdoor <pos> <true|false>`).
- Dev-замер нагрузки зон: `/viva devbench <прогрев> <замер>` (только dev) — 5 ботов (`TestPlayers.detached`,
  добавлены в мир: вокруг них грузятся чанки и идёт естественный спавн), стадия 4, армия вкл, ночь; итоги в лог
  «Viva Core bench: …», сервер останавливается сам. `-PvivaDevShots=100,180,…` — свои тики кадров dev-скриншотов;
  при телепорте сценой (прыжок > 4 блоков) хук перехватывает поворот камеры заново.
  Сцены задачи 9 (скрипты в `run/tmp-task9/`, не в git): `shoot_seed.py <сид>` — свежий мир с сидом на
  dedicated-сервере, постройки по плану, перенос в `run/saves`, 5 кадров клиентом. Подводные камни: `positioned over
  world_surface` в незагруженном чанке молча не срабатывает (сначала простой tp, потом через ~100 тиков — над
  поверхностью); при генерации чанков сервер тикает медленнее клиента — кадр ставить посередине между ракурсами (~250
  тиков до и после); для съёмки трассеров — каменная площадка в воздухе и `tracerSpeed = 1` в `run/config/vivacore-client.toml`
  (вернуть 6).
  В сценах скриншотов давай ночное видение без частиц: `effect give @s minecraft:night_vision infinite 0 true`.
- Свет блоков в 1.21.1 кешируется в BlockState при регистрации (до загрузки конфига): уровень из конфига — через
  позиционный `getLightEmission(state, level, pos)` (NeoForge), статический `lightLevel` — значение по умолчанию.
- Жива (`zhiva/`): состояние — attachment `vivacore:zhiva` (IMPLANTED → REMOVED → REVEALED, copyOnDeath, синхронизация
  только владельцу); логика — `Zhiva`; голос — `ZhivaVoice` (антиспам); помощь — `ZhivaHelp` (здоровье + пустые точки
  входа для рейдов и советов); устройство — `zhiva/device`, пакеты терминала — `zhiva/net`, экран — `client/`.
- Chromeware: всё только через `compat/ChromewareCompat` (без ссылок на классы Chromeware) → `ChromewareBridge`
  (рефлексия: классы и метод ищутся один раз, кеш в static-полях; не нашлось → «нет» + одна строка в логе). Кушетка —
  событие NeoForge `EntityMountEvent` на сущность `chromeware:surgery_seat`, без миксинов. Хрупкие места при обновлении
  Chromeware: id `chromeware:surgery_seat`, `KiroshiOptics.ALL` (запасной путь — имена 7 оптик в enum `Implant`),
  `Implant#installed(Player)`, attachment `chromeware:face` (только в тесте).
- Конфиги NeoForge 21.1.264 (и наш, и чужие) лежат в `config/` (в dev — `run/config/`), не в `world/serverconfig/`.
- Миксины: `src/main/resources/vivacore.mixins.json`, классы в `dev.rezus.vivacore.mixin`:
  - `CreateBlockHelperMixin` (Create `BlockHelper.destroyBlockAs`, static): HEAD cancellable → в коробке постройки
    блок не ломается (дрели, пилы, катки, деплоер).
  - `CreateBreakingMovementMixin` (`BlockBreakingMovementBehaviour.canBreak`): RETURN → false в коробке — контрапция
    упирается, как в бедрок. `CreateBreakingBlockEntityMixin` (`BlockBreakingKineticBlockEntity.canBreak(BlockState,
    float)`, позиция — `getBreakingPos()`): стоячие дрель/пила не начинают ломать.
  - `CreateContraptionMixin` (Create `Contraption.addBlocksToWorld`): `@WrapOperation` вызова
    `BlockState.getDestroySpeed` → в коробке −1, и Create сам выбрасывает блок контрапции предметом (иначе стоящий блок
    ломается `destroyBlock`, и на его место встаёт блок контрапции).
  - Ванильные (задача 9, защита): `DispenserBlockMixin` (`dispenseFrom` HEAD), `BaseFireBlockMixin` (`onPlace` HEAD),
    `FireBlockMixin` (`checkBurnOut` HEAD, у NeoForge есть параметр Direction), `FlowingFluidMixin` (`spreadTo` HEAD).
  - `WaterWheelBlockEntityMixin` (Create `WaterWheelBlockEntity`): `getGeneratedSpeed` HEAD cancellable → 0 у лишнего
    колеса; `setFlowScoreAndUpdate` INVOKE перед `updateGeneratedRotation` → проверка лимита; `write` TAIL /
    `read` RETURN → NBT `vivacore:limited`, `vivacore:order`.
  - `GeneratingKineticBlockEntityMixin` (Create `GeneratingKineticBlockEntity`): `tick` TAIL → перепроверка
    лимита; `addToGoggleTooltip` RETURN → строка в очках инженера.
  - При обновлении Create: сверить сигнатуры этих методов в исходниках новой сборки
    (`create-1.21.1-<версия>-sources.jar` на maven.createmod.net). Инжекты `defaultRequire: 1` — если метод
    исчез, игра упадёт при загрузке с понятной ошибкой миксина, а не сломается тихо.

## Правила
- Язык: код и идентификаторы — английский; комментарии — русский; сообщения игрокам — только через lang-файлы
  (`Component.translatable`), никаких строк в коде.
- Все числа — в конфиге (`VivaConfig`), в коде не зашивать.
- Тексты для игроков — готовые черновики на русском и английском (задача 11), Батыр их правит. Заглушек
  `[ТЕКСТ: …]` / `[TEXT: …]` в lang быть не должно: задача Gradle `checkLang` (входит в `check` и `build`) падает на
  заглушке и на расхождении ключей ru_ru / en_us. Новые тексты — сразу нормальные, в тоне лора.
- Каждая функция связана с тестом из заметки «09 Тесты» — номер теста в комментарии или в описании коммита.
  Если подходящего теста в заметке нет — так и написать.
- Команды — только английские (задача 11: русские псевдонимы убраны, в подсказках по Tab были оба набора):
  `/viva home | zhiva | orion | army | stage | sites | nexus`. Сообщения — на языке клиента через lang. Тест
  `HomeGameTests#englishCommandsOnly`.
- Перед миксинами в чужие моды (Create, Sable, Aeronautics, Chromeware) — прочитать их код (jar из инстанса)
  и описать в отчёте, куда и почему вмешиваешься.
- Не менять ничего вне репозитория без прямой просьбы. Временные файлы — только внутри репозитория, в папках из
  .gitignore (например `run/tmp-<задача>/`), не в корне диска и не рядом с репозиторием.
- Push: Батыр разрешил (задача 2) пушить в `main` после того, как build, runServer и runGameTestServer зелёные.
  Если что-то красное — не пушить, а описать в отчёте.

## Отчёт после каждой задачи
- Что сделано.
- Файлы.
- Как проверял: команды и их результат.
- Что НЕ сделано.
- Все проблемы и ошибки, включая уже исправленные — ничего не скрывать и не сглаживать.
