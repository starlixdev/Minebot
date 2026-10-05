# Changelog

## 2.1.1-java

- Loads Java bots from per-bot `bot.jar` files and from providers registered by companion Paper plugins.
- Supports bot creation, start, stop, reload, validation and runtime status through `/minebot` and `MineBotJavaApi`.
- Connects each running bot to the Discord gateway and REST API.
- Handles Discord events, slash commands, autocomplete, interaction replies, deferrals, follow-ups and presence updates.
- Subscribes bots to selected Bukkit events and exposes server snapshots, console commands and broadcasts.
- Provides asynchronous HTTP requests with configurable connection, request and response-size limits.
- Persists bot data in per-bot `data.json` files.
- Loads named secrets from `secrets.yml` or environment variables and redacts configured secret values in MineBOT-managed output paths.
- Exposes structured console log subscriptions with throwable snapshots.
- Uses a bounded serial dispatcher for bot callbacks and provides delayed and repeating scheduling helpers.
