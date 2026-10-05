# MineBOT

MineBOT runs Java bots inside a Paper server. Each bot has its own configuration, JSON storage, Discord connection and serial runtime. Bot code can use Discord REST and gateway events, Bukkit events, server commands, broadcasts, HTTP requests, scheduled work and structured console logs through the MineBOT Java API.

MineBOT is designed to keep working on future Minecraft versions without requiring full or version-specific updates. The latest Minecraft version it has been tested on is `26.2`.

Version: `2.1.1-java`

## Requirements

- Paper or another Bukkit-compatible server with the APIs used by the plugin.
- Java 17 or newer.
- The bundled plugin descriptor targets Bukkit API `1.20`.

## Install

Copy `release/MineBOT-2.1.1-java.jar` to the server's `plugins` directory and start the server. MineBOT creates `plugins/MineBOT/`, `plugins/MineBOT/Bots/`, `config.yml` and `secrets.yml` as needed.

Administration commands use the `minebot.admin` permission, which defaults to server operators.

```text
/minebot list
/minebot create <name>
/minebot start <name>
/minebot stop <name>
/minebot reload <name>
/minebot validate <name>
/minebot status <name>
```

Bot identifiers are 1-48 characters and may contain letters, digits, `_` and `-`.

## Local Bots

A local bot is stored under `plugins/MineBOT/Bots/<name>/`:

```text
plugins/MineBOT/Bots/MyBot/
├── bot.yml
├── data.json
└── bot.jar
```

The folder name must match the bot name. When `entrypoint` is present, `bot.jar` must exist and the entrypoint class must be public, concrete, extend `com.minebot.api.JavaBot` and have a public no-argument constructor.

```yaml
name: "MyBot"
enabled: true
entrypoint: "example.MyBot"
token: "${ENV:MYBOT_DISCORD_TOKEN}"
intents:
  - GUILDS
auto-register-slash: true
activity:
  type: playing
  text: "Minecraft"
```

`token` accepts a literal Discord bot token or an environment reference in the form `${ENV:VARIABLE_NAME}`. Discord intents can be listed by name. `intents-value` can be used instead when a numeric intent bitmask is needed.

Supported activity types are `playing`, `streaming`, `listening`, `watching`, `custom` and `competing`.

### Minimal Bot

```java
package example;

import com.minebot.api.JavaBot;
import com.minebot.api.SlashCommand;

import java.util.Collection;
import java.util.List;

public final class MyBot extends JavaBot {
    @Override
    public void onReady() {
        bot().log("MyBot is ready.");
    }

    @Override
    public Collection<SlashCommand> slashCommands() {
        return List.of(
            SlashCommand.simple("ping", "Check whether the bot is online")
        );
    }
}
```

Compile local bots against MineBOT without packaging MineBOT API classes into `bot.jar`. The local bot classloader uses MineBOT as its parent, so the API and server-provided classes remain available at runtime.

## Provider Plugins

A companion Paper plugin can register a `JavaBot` factory:

```java
MineBotJavaApi.registerProvider("MyBot", MyBot::new);
```

The bot still uses its folder and `bot.yml` under `plugins/MineBOT/Bots/`. A registered provider takes precedence over a local `entrypoint` with the same bot name. Companion plugins should declare MineBOT as a dependency in `plugin.yml`:

```yaml
depend: [MineBOT]
```

## Java API

`BotContext` is the main API available to a running bot:

- `discord()` exposes Discord state, REST requests, interaction replies, command registration and presence updates.
- `minecraft()` returns a server snapshot and can dispatch console commands or broadcasts.
- `http()` performs asynchronous HTTP requests.
- `storage()` reads and writes the bot's `data.json` file.
- `console()` subscribes to structured server log events.
- `waitFor(...)` and `every(...)` schedule delayed or repeating work.
- `executeSerial(...)` queues work on the bot's serial dispatcher.
- `secret(...)` reads a named value from `plugins/MineBOT/secrets.yml`.

The complete public signatures are listed in [`docs/API.md`](docs/API.md). The API source is under [`src/main/java/com/minebot/api`](src/main/java/com/minebot/api).

## Minecraft Events

`JavaBot.minecraftEvents()` returns the Bukkit events a bot wants to receive. MineBOT accepts fully qualified Bukkit event class names and these aliases:

```text
player_join
player_quit
player_chat
player_command
player_death
block_break
block_place
server_command
```

Event data is converted to immutable `EventData`. The snapshot includes `event.class` and values exposed through zero-argument Bukkit getters.

## Secrets

MineBOT creates `plugins/MineBOT/secrets.yml`. Values can be stored directly or read from environment variables:

```yaml
secrets:
  EXAMPLE_API_KEY: "${ENV:EXAMPLE_API_KEY}"
```

Bot code reads a value with:

```java
String key = bot().secret("EXAMPLE_API_KEY");
```

Configured secrets are redacted from MineBOT-managed error text and data paths that pass through its redaction layer.

## Plugin Configuration

`src/main/resources/config.yml` contains the runtime defaults:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `http-timeout-seconds` | `20` | Overall HTTP request timeout. |
| `http-connect-timeout-seconds` | `10` | HTTP connection timeout. |
| `http-worker-threads` | `4` | HTTP worker thread count. |
| `http-max-response-bytes` | `2097152` | Maximum HTTP response body size. |
| `runtime-max-pending-events` | `1024` | Requested event queue capacity. The runtime never creates a queue smaller than 16 entries. |
| `runtime-max-event-age-seconds` | `30` | Events older than this value are counted as stale instead of executed. |
| `runtime-coalesce-timers` | `true` | Prevents repeating timer callbacks from stacking while a previous callback is still pending. |

## Build

The project uses Maven and targets Java 17.

```text
mvn clean package
```

The build writes `target/MineBOT-2.1.1-java.jar`.

## Repository Layout

```text
MineBOT/
├── src/main/java/          Java source
├── src/main/resources/     plugin.yml, config.yml and bundled API notes
├── docs/API.md             public Java API reference
├── examples/local-bot/     minimal bot configuration and data file
├── release/                release JAR
├── pom.xml                 Maven build
├── README.md
├── CHANGELOG.md
└── SECURITY.md
```
