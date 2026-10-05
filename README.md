# MineBOT

MineBOT runs Java bots inside a Paper server. Bots can use Discord, Minecraft server actions, HTTP requests, persistent JSON storage, scheduled tasks, slash commands, and structured console log events through one runtime.

This repository contains the packaged `2.1.1-java` build.

## Requirements

- A Paper-compatible Minecraft server.
- Bukkit/Paper API target: `1.20`.
- Java 17 bytecode. Some Paper versions require a newer Java runtime.

## Install

1. Copy `release/MineBOT-2.1.1-java.jar` to the server's `plugins` folder.
2. Start or restart the server.
3. MineBOT creates its data folder and `Bots` directory.
4. Run `/minebot create <name>` to create a bot folder, or add an existing bot folder manually.

The default plugin configuration is in [`resources/config.yml`](resources/config.yml).

## Commands

MineBOT administration commands require `minebot.admin`. The permission defaults to server operators.

```text
/minebot list
/minebot create <name>
/minebot start <name>
/minebot stop <name>
/minebot reload <name>
/minebot validate <name>
/minebot status <name>
```

## Local Java bots

A local bot lives in its own directory under `plugins/MineBOT/Bots/`:

```text
plugins/MineBOT/Bots/MyBot/
├── bot.yml
├── data.json
└── bot.jar
```

Example `bot.yml`:

```yaml
name: "MyBot"
enabled: true
entrypoint: "example.MyBot"
token: "PASTE_TOKEN_HERE"
intents:
  - GUILDS
auto-register-slash: true
activity:
  type: playing
  text: "Minecraft"
```

The class named by `entrypoint` must be public, concrete, extend `com.minebot.api.JavaBot`, and have a public no-argument constructor.

Do not bundle MineBOT API classes into the bot JAR. Compile against MineBOT as a compile-only dependency. The local bot classloader uses MineBOT as its parent, so the API and server-provided dependencies are available at runtime.

If a registered provider and a local bot use the same bot name, the registered provider is used.

### Minimal bot

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

## Provider plugins

A companion Paper plugin can register a bot directly:

```java
MineBotJavaApi.registerProvider("MyBot", MyBot::new);
```

Its `plugin.yml` should declare MineBOT as a dependency:

```yaml
depend: [MineBOT]
```

Local bots and registered providers receive the same `BotContext` API.

## Bot API

`BotContext` gives each bot access to:

- `discord()` for Discord REST requests, interactions, slash commands, and presence.
- `minecraft()` for server snapshots, console commands, and broadcasts.
- `http()` for asynchronous HTTP requests.
- `storage()` for persistent per-bot data.
- `console()` for structured server log subscriptions.
- `waitFor(...)` for delayed asynchronous work.
- `every(...)` for repeating tasks.
- `executeSerial(...)` for returning work to the bot's serial dispatcher.

The public Java signatures included in this build are listed in [`docs/API.md`](docs/API.md).

## Console events

Bots can subscribe to server console events:

```java
AutoCloseable subscription = bot().console().subscribe(event -> {
    String source = event.logger();
    String level = event.level();
    String message = event.message();
});
```

A console event contains its timestamp, level, logger name, message, and immutable throwable data. MineBOT reads from the server's Log4j pipeline when it is available and falls back to JUL otherwise.

Close a subscription when the bot no longer needs it. MineBOT also removes active subscriptions when the runtime stops.

## Runtime behavior

Bot callbacks and console subscribers run through a bounded serial dispatcher. Network and HTTP work stays asynchronous. If an asynchronous continuation needs to run again on the bot dispatcher, use `BotContext.executeSerial(...)`.

MineBOT can load, start, stop, reload, validate, and inspect local bots and registered providers. Reloading closes the current runtime and, for a local bot, its classloader before MineBOT loads the bot again.

## Storage

Each bot has its own `data.json`. The storage API can read, set, delete, check, and snapshot values in that file.

## Repository files

```text
MineBOT/
├── README.md
├── CHANGELOG.md
├── SECURITY.md
├── REPOSITORY_DESCRIPTION.txt
├── SHA256SUMS.txt
├── .gitignore
├── .gitattributes
├── release/
│   └── MineBOT-2.1.1-java.jar
├── resources/
│   ├── config.yml
│   └── plugin.yml
├── examples/
│   └── local-bot/
│       ├── bot.yml
│       └── data.json
└── docs/
    └── API.md
```

## Source code

This repository was assembled from the compiled MineBOT JAR supplied for publication. The original Java source files are not stored inside a compiled JAR, so they are not included in this package.

## License

No license file is included. Add a license before publishing the project as open source if you want other people to have explicit permission to use, modify, or redistribute the code.
