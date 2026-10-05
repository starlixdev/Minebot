# MineBOT Java API

MineBOT can run a `JavaBot` from a local `bot.jar` or from a provider registered by another Paper plugin. Both use the same `BotContext` API and lifecycle.

A local bot folder has this form:

```text
plugins/MineBOT/Bots/MyBot/
├── bot.yml
├── data.json
└── bot.jar
```

`entrypoint` in `bot.yml` names the `JavaBot` implementation. The class must be public, concrete and have a public no-argument constructor. A registered provider takes precedence when the same bot name also has a local entrypoint.

`JavaBot` lifecycle hooks include `onLoad`, `onReady`, Discord events, slash commands, autocomplete, Minecraft events and `onStop`. `BotContext` provides Discord, Minecraft, HTTP, storage, console log, scheduling, secret and runtime-state access.

Local bot JARs should compile against MineBOT without bundling MineBOT API classes.
