# MineBOT Java API

MineBOT `2.1.1-java` exposes the classes below from `com.minebot.api`.

## `JavaBot`

A bot implementation extends `JavaBot`. MineBOT attaches a `BotContext` before starting the bot.

```java
public abstract class JavaBot {
    protected final BotContext bot();
    public void onLoad() throws Exception;
    public void onReady() throws Exception;
    public void onDiscordEvent(String event, EventData data) throws Exception;
    public void onSlashCommand(String command, Interaction interaction) throws Exception;
    public void onAutocomplete(String command, EventData data) throws Exception;
    public void onMinecraftEvent(String event, EventData data) throws Exception;
    public void onStop() throws Exception;
    public Collection<String> minecraftEvents();
    public Collection<SlashCommand> slashCommands();
}
```

`onLoad()` runs before the Discord gateway starts. `onReady()` runs after Discord sends `READY`. Slash-command registration happens after `onReady()` when `auto-register-slash` is enabled and `slashCommands()` is not empty.

## `BotContext`

```java
public final class BotContext {
    public String name();
    public BotConfiguration configuration();
    public DiscordApi discord();
    public MinecraftApi minecraft();
    public StorageApi storage();
    public HttpApi http();
    public ConsoleApi console();
    public String secret(String key);
    public void log(String message);
    public void warn(String message);
    public CompletableFuture<Void> waitFor(Duration duration);
    public AutoCloseable every(Duration duration, Runnable task);
    public void executeSerial(Runnable task);
    public boolean running();
}
```

`every(...)` and `console().subscribe(...)` return handles that can be closed when the bot no longer needs them. Runtime shutdown also closes active subscriptions and scheduled work.

## `MineBotJavaApi`

```java
public final class MineBotJavaApi {
    public static boolean available();
    public static Path create(String name) throws IOException;
    public static void registerProvider(String name, BotProvider provider) throws Exception;
    public static void unregisterProvider(String name);
    public static void start(String name) throws Exception;
    public static void stop(String name);
    public static void reload(String name) throws Exception;
    public static Optional<RuntimeHandle> runtime(String name);
    public static List<String> names();
    public static ValidationResult validate(String name);
}
```

`available()` is true while the plugin has an active `BotManager`. Provider registration uses the same bot configuration directory as local bots.

## `DiscordApi`

```java
public final class DiscordApi {
    public boolean connected();
    public String applicationId();
    public String botUserId();
    public String botUsername();
    public CompletableFuture<DiscordResponse> send(String channelId, String content);
    public CompletableFuture<DiscordResponse> request(String method, String path, String body);
    public CompletableFuture<DiscordResponse> interactionReply(
        String interactionId,
        String token,
        String content,
        boolean ephemeral
    );
    public CompletableFuture<DiscordResponse> interactionDefer(
        String interactionId,
        String token,
        boolean ephemeral
    );
    public CompletableFuture<DiscordResponse> editOriginal(String token, String content);
    public CompletableFuture<DiscordResponse> followup(String token, String content, boolean ephemeral);
    public CompletableFuture<DiscordResponse> registerGlobalCommands(Collection<SlashCommand> commands);
    public void presence(String status, String activityType, String activityText);
}
```

## `MinecraftApi`

```java
public final class MinecraftApi {
    public Map<String, Object> snapshot();
    public void command(String command);
    public void broadcast(String message);
}
```

`command(...)` and `broadcast(...)` are scheduled on the Bukkit server thread.

## `HttpApi`

```java
public final class HttpApi {
    public CompletableFuture<HttpResponse> request(
        String method,
        String url,
        Map<String, List<String>> headers,
        String body,
        Duration timeout
    );

    public CompletableFuture<HttpResponse> get(String url);
}
```

HTTP work is asynchronous and uses the timeout, worker-count and response-size limits from MineBOT's plugin configuration.

## `StorageApi`

```java
public final class StorageApi {
    public Object get(String key);
    public Object getOrDefault(String key, Object defaultValue);
    public boolean contains(String key);
    public Map<String, Object> snapshot();
    public void set(String key, Object value);
    public void delete(String key);
}
```

Storage is persisted in the bot's `data.json` file.

## `ConsoleApi`

```java
public final class ConsoleApi {
    public AutoCloseable subscribe(Consumer<ConsoleLogEvent> consumer);
}
```

Console events are delivered through the bot's serial dispatcher.

## `SlashCommand`

```java
public record SlashCommand(
    String name,
    String description,
    List<Map<String, Object>> options
) {
    public static SlashCommand simple(String name, String description);
    public Map<String, Object> toDiscord();
}
```

Command names must contain 1-32 lowercase letters, digits, `_` or `-`. Descriptions are limited to 1-100 characters. MineBOT validates option types, names, descriptions, nesting depth and the 25-option limit before registration.

## `Interaction`

```java
public final class Interaction {
    public EventData event();
    public Map<String, Object> options();
    public CompletableFuture<DiscordResponse> reply(String content);
    public CompletableFuture<DiscordResponse> reply(String content, boolean ephemeral);
    public CompletableFuture<DiscordResponse> defer(boolean ephemeral);
    public CompletableFuture<DiscordResponse> edit(String content);
    public CompletableFuture<DiscordResponse> followup(String content, boolean ephemeral);
}
```

## `EventData`

```java
public final class EventData {
    public EventData(Map<String, Object> values);
    public Map<String, Object> asMap();
    public Object get(String key);
    public String string(String key);
    public boolean bool(String key, boolean defaultValue);
    public long longValue(String key, long defaultValue);
}
```

`get(...)` supports dot-separated paths through nested maps. Event values are frozen into immutable maps, lists and sets when the `EventData` object is created.

## `BotConfiguration`

```java
public record BotConfiguration(
    String name,
    boolean enabled,
    long intents,
    String activityType,
    String activityText,
    boolean autoRegisterSlash
) {}
```

## `BotProvider`

```java
@FunctionalInterface
public interface BotProvider {
    JavaBot create() throws Exception;
}
```

## `RuntimeHandle`

```java
public interface RuntimeHandle {
    String name();
    BotLifecycle lifecycle();
    boolean running();
    long pendingTasks();
    long droppedTasks();
    long staleTasks();
    long completedTasks();
}
```

## `BotLifecycle`

```java
public enum BotLifecycle {
    CREATED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
```

## `ValidationResult`

```java
public record ValidationResult(
    boolean valid,
    List<String> checks,
    List<String> errors
) {}
```

## `DiscordResponse`

```java
public record DiscordResponse(
    boolean ok,
    int status,
    String body,
    String error,
    Map<String, List<String>> headers
) {}
```

## `HttpResponse`

```java
public record HttpResponse(
    boolean ok,
    int status,
    String body,
    String error,
    Map<String, List<String>> headers,
    long elapsedMillis
) {}
```

## `ConsoleLogEvent`

```java
public record ConsoleLogEvent(
    Instant timestamp,
    String level,
    String logger,
    String message,
    ConsoleThrowable throwable
) {
    public String stackTrace();
}
```

## `ConsoleThrowable`

```java
public record ConsoleThrowable(
    String type,
    String message,
    String stackTrace,
    List<ConsoleThrowable> suppressed,
    ConsoleThrowable cause
) {}
```
