# Viva Core

Мод для сборки Minecraft «Viva La Vida»: Minecraft 1.21.1, NeoForge 21.1.264, Java 21, Create 6.0.10.

```
./gradlew build               # jar в build/libs
./gradlew runServer           # dev-сервер с Create
./gradlew runGameTestServer   # автотесты
./gradlew runClient           # dev-клиент
```

Команды — только английские (задача 11), операторы, уровень 2:

| Команда | Что делает |
| --- | --- |
| `/viva home approve <player1> [player2]` | одобрить дом на 1–2 игроков |
| `/viva home add <player> <to_player>` | добавить игрока в дом другого (если есть место) |
| `/viva home setpos <player> [pos]` | место дома; без координат — позиция того, кто выполняет |
| `/viva home remove <player>` | снять игрока с дома |
| `/viva home list` | кто с домом, кто без дома |

Жива (оператор): `/viva zhiva status <player>`, `/viva zhiva state <player> <implanted|removed|revealed>`,
`/viva zhiva couch <player>`. Блок «продвинутое устройство имплантирования» —
`vivacore:implant_device`.

Create: Chromeware — необязательный: Viva Core читает его рефлексией, для сборки jar Chromeware не нужен.

ОРИОН (оператор): `/viva orion status | reset`. Код терминала — конфиг
`orion.accessCode`; дверь ОРИОНа открывается только латунным ключом.

Армия ОРИОНа: 13 машин (`vivacore:scout_drone`, `infantry_light`, `infantry_heavy`, `sniper`, `mech_walker`, `tank`,
`helicopter`, `swarm_unit`, `core_guardian` и заражённые версии), яйца во вкладке Viva Core; естественный спавн — только
в зоне влияния лаборатории (см. ниже).
Тревога участков и волны подкрепления; выключено, пока `army.enabled = false`.
Оператор: `/viva army on | off | alarm <0–4> | status`.
Машины вооружены (кроме дронов и стража ядра); разрушение блоков армией — игровое правило `/gamerule vivaArmyGriefing`
(по умолчанию true; при false — сквозные снаряды вместо взрывных).
Три постройки (черновики `data/vivacore/structure/{crash_habitation,orion_lab,nexus_site}.nbt`, можно заменить своими —
код смотрит только на имя файла и метки `structure_block` в режиме DATA: `vivacore:ground`, `vivacore:core`,
`vivacore:player_spawn`, `vivacore:guardian`, `vivacore:guard`). Жилой отсек ставится у спавна при первом запуске мира,
лаборатория и Нексус — в 10 000 блоков по углу из сида (в разные стороны, на суше), когда их область впервые загрузилась.
Оператор: `/viva sites status | place <lab|nexus|crash> [here] | tp <lab|nexus|crash>`,
`/viva nexus destroy | restore`, `/viva stage [1–5]`. Стадия — временно, до AStages.
Зоны влияния (`zones.*`): вокруг ядер круги 9 500 блоков, доля мобов стороны 100% − 10% за 1000 блоков; до 4-й стадии —
только охрана 30% в 1000 блоков от ядер. Защита построек (`protection.*`): в коробке постройки нельзя ломать, ставить,
лить (и снаружи раздатчиком или затеканием), жечь, взрывать, двигать, ломать и разбирать в неё контрапции Create;
в лабораторию — без эндер-жемчуга и хоруса. Место под постройку — суша по всей площади и не островок; под постройкой —
столбы до земли, края сводятся к местности склоном.
Бой (`army.combat.*`): пули — мгновенный выстрел с трассером, снаряды-сущности и взрывы ОРИОНа ограничены.
Проверка моделей в игре без ручного управления: `./gradlew runClient -PvivaQuickPlay=<мир из run/saves>`.

Баланс имплантов Chromeware: [docs/chromeware-balance.md](docs/chromeware-balance.md).

Лимит водяных колёс Create: в одной кинетической сети работают не больше 3 колёс (конфиг `wheels.maxPerNetwork`),
лишние ведут себя как вал. Конфиг сервера — `config/vivacore-server.toml`.

Правила для разработки — в [CLAUDE.md](CLAUDE.md).
