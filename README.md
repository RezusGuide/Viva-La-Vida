# Viva Core

Мод для сборки Minecraft «Viva La Vida»: Minecraft 1.21.1, NeoForge 21.1.264, Java 21, Create 6.0.10.

```
./gradlew build               # jar в build/libs
./gradlew runServer           # dev-сервер с Create
./gradlew runGameTestServer   # автотесты
./gradlew runClient           # dev-клиент
```

Команды (операторы, уровень 2):

| Русская | Английская | Что делает |
| --- | --- | --- |
| `/viva дом одобрить <ник1> [ник2]` | `/viva home approve <player1> [player2]` | одобрить дом на 1–2 игроков |
| `/viva дом добавить <ник> <к_нику>` | `/viva home add <player> <to_player>` | добавить игрока в дом другого (если есть место) |
| `/viva дом место <ник> [x y z]` | `/viva home setpos <player> [pos]` | место дома; без координат — позиция того, кто выполняет |
| `/viva дом снять <ник>` | `/viva home remove <player>` | снять игрока с дома |
| `/viva дом список` | `/viva home list` | кто с домом, кто без дома |

Жива (оператор): `/viva жива статус <ник>`, `/viva жива состояние <ник> <implanted|removed|revealed>`,
`/viva жива кушетка <ник>` (EN: `/viva zhiva status|state|couch`). Блок «продвинутое устройство имплантирования» —
`vivacore:implant_device`.

Create: Chromeware — необязательный: Viva Core читает его рефлексией, для сборки jar Chromeware не нужен.

ОРИОН (оператор): `/viva орион статус | сбросить` (EN: `/viva orion status | reset`). Код терминала — конфиг
`orion.accessCode`; дверь ОРИОНа открывается только латунным ключом.

Армия ОРИОНа: 13 машин (`vivacore:scout_drone`, `infantry_light`, `infantry_heavy`, `sniper`, `mech_walker`, `tank`,
`helicopter`, `swarm_unit`, `core_guardian` и заражённые версии), яйца во вкладке Viva Core, естественного спавна нет.
Тревога участков и волны подкрепления; выключено, пока `army.enabled = false`.
Оператор: `/viva армия вкл | выкл | тревога <0–4> | статус` (EN: `/viva army on | off | alarm <0–4> | status`).
Машины вооружены (кроме дронов и стража ядра); разрушение блоков армией — игровое правило `/gamerule vivaArmyGriefing`
(по умолчанию true; при false — сквозные снаряды вместо взрывных).
Проверка моделей в игре без ручного управления: `./gradlew runClient -PvivaQuickPlay=<мир из run/saves>`.

Баланс имплантов Chromeware: [docs/chromeware-balance.md](docs/chromeware-balance.md).

Лимит водяных колёс Create: в одной кинетической сети работают не больше 3 колёс (конфиг `wheels.maxPerNetwork`),
лишние ведут себя как вал. Конфиг сервера — `config/vivacore-server.toml`.

Правила для разработки — в [CLAUDE.md](CLAUDE.md).
