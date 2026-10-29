# LivingVillages — Free

![LivingVillages Free](assets/readme/cover-free.jpg)

<p align="center">
  <strong><a href="https://github.com/TheViniAnd/LivingVillages/releases/latest/download/LivingVillages.jar">⬇ Download .JAR</a></strong>
  &nbsp;·&nbsp;
  <a href="https://github.com/TheViniAnd/LivingVillages/releases">Releases</a>
  &nbsp;·&nbsp;
  <a href="#installation">Installation</a>
  &nbsp;·&nbsp;
  <a href="#commands">Commands</a>
  &nbsp;·&nbsp;
  <a href="README.ru.md">Русский</a>
</p>

**NPCs build their own village.** Trade with residents, help with supplies and watch the settlement grow.

## Construction and village life

Residents gather resources, craft tools and build homes and workplaces. They work, eat, rest and move into new housing. Local guards protect the village.

![Village streets and residents](assets/readme/village-street.jpg)

## Economy and trade

Physical coins, a village treasury and trading based on local demand. Complete delivery tasks, supply materials and earn residents' trust.

![Village farm](assets/readme/farmer.jpg)

## Maps and village information

The village map and information boards show population, food supplies, treasury and construction needs.

![Village map and information boards](assets/readme/village-board.jpg)

## Free version

- One culture.
- Village levels 1–3.
- Up to 12 residents per village.
- Up to 5 villages per world.

## Installation

1. [Download the plugin](https://github.com/TheViniAnd/LivingVillages/releases/latest/download/LivingVillages.jar).
2. Place the JAR in your server's `plugins` folder.
3. Restart the server.

## Commands

| Command | Action |
| --- | --- |
| `/lv help` | Show available commands |
| `/lv menu` | Open discovered villages |
| `/lv status` | View village status |
| `/lv claims` | Collect stored items and returns |
| `/lv admin` | Open administration tools |
| `/lv create polevichi [layout]` | Create a village |
| `/lv coins <amount> [player]` | Give coins |
| `/lv reload` | Reload settings |

Administrative permission: `livingvillages.admin`.

## Build from source

```sh
./gradlew build
```

## Documentation

[Configuration](src/main/resources/config.yml) · [Persistence](docs/PERSISTENCE.md) · [Testing](docs/TESTING.md) · [License](LICENSE)
