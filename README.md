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
| `/viva дом снять <ник>` | `/viva home remove <player>` | снять игрока с дома |
| `/viva дом список` | `/viva home list` | кто с домом, кто без дома |

Правила для разработки — в [CLAUDE.md](CLAUDE.md).
