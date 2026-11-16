# Viva Core — правила для сессий Claude Code

Мод Viva Core для сборки Minecraft «Viva La Vida». Ставится каждому игроку и на сервер.
Репозиторий: https://github.com/RezusGuide/Viva-La-Vida (локально `D:\viva-core`).

## Окружение
- Minecraft 1.21.1, NeoForge 21.1.264, Java 21, ModDevGradle (шаблон NeoForge MDK 1.21.1).
- mod id `vivacore`, пакет `dev.rezus.vivacore`.
- В сборке: Create 6.0.10, Create Aeronautics 1.3.2, Sable 2.0.6, GeckoLib 4.9.3, Jade 15.10.6, Curios 9.5.1.
- Create в dev: `com.simibubi.create:create-1.21.1:6.0.10-280:slim` (сборка 280 = git ac0c444, тот же коммит, что jar в инстансе),
  Ponder 1.0.82+mc1.21.1, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67. Версии — в `gradle.properties`.
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
- `./gradlew runClient` — dev-клиент.

## Структура
- `VivaCore` — точка входа; `VivaConfig` — серверный конфиг; `command/VivaCommands` — корень `/viva`.
- Модули (пакеты, в каждом `package-info.java` со ссылкой на заметку): `knowledge`, `home`, `army`, `zones`,
  `protection`, `wheels`, `zhiva`, `planets`.
- Lang: `src/main/resources/assets/vivacore/lang/{en_us,ru_ru}.json`.
- GameTest: в main-исходниках, `@GameTestHolder("vivacore")`, шаблон `vivacore:empty`
  (`data/vivacore/structure/empty.nbt`, 1×1×1 воздух).

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
- Не менять ничего вне репозитория без прямой просьбы. Не пушить без просьбы Батыра.

## Отчёт после каждой задачи
- Что сделано.
- Файлы.
- Как проверял: команды и их результат.
- Что НЕ сделано.
- Все проблемы и ошибки, включая уже исправленные — ничего не скрывать и не сглаживать.
