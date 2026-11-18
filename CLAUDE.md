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
- Тексты для игроков пишет Батыр: ставь заглушки `[ТЕКСТ: что здесь будет]` (ru_ru) и `[TEXT: …]` (en_us).
- Каждая функция связана с тестом из заметки «09 Тесты» — номер теста в комментарии или в описании коммита.
  Если подходящего теста в заметке нет — так и написать.
- Команды: русские слова основные, английские — псевдонимы (кириллица в Brigadier работает, проверено тестом
  `HomeGameTests#cyrillicCommands`).
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
