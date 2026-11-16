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

Лимит водяных колёс Create: в одной кинетической сети работают не больше 3 колёс (конфиг `wheels.maxPerNetwork`),
лишние ведут себя как вал. Конфиг сервера — `world/serverconfig/vivacore-server.toml`.

Правила для разработки — в [CLAUDE.md](CLAUDE.md).
