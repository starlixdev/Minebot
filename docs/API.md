# MineBOT Java API

MineBOT `2.1.1-java` exposes the following public Java API.

## `JavaBot`

A bot implementation extends `JavaBot`. MineBOT supplies its `BotContext` and calls the lifecycle and event methods at runtime.

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

## `BotContext`

`BotContext` is the per-bot entry point for configuration, Discord, Minecraft, storage, HTTP, console logs, scheduling, secrets, and runtime state.

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

## `MineBotJavaApi`

Static API for creating bots, registering providers, changing runtime state, listing bot names, and validating configuration.

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

## `DiscordApi`

Discord connection state, REST requests, interaction responses, command registration, and presence updates.

```java
public final class DiscordApi {
    public boolean connected();
    public String applicationId();
    public String botUserId();
    public String botUsername();
    public CompletableFuture<DiscordResponse> send(String channelId, String content);
    public CompletableFuture<DiscordResponse> request(String method, String path, String body);
    public CompletableFuture<DiscordResponse> interactionReply(String interactionId, String token, String content, boolean ephemeral);
    public CompletableFuture<DiscordResponse> interactionDefer(String interactionId, String token, boolean ephemeral);
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

## `ConsoleApi`

`subscribe` returns an `AutoCloseable` handle for removing the subscription.

```java
public final class ConsoleApi {
    public AutoCloseable subscribe(Consumer<ConsoleLogEvent> consumer);
}
```

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
    public Map<String, Object> asMap();
    public Object get(String key);
    public String string(String key);
    public boolean bool(String key, boolean defaultValue);
    public long longValue(String key, long defaultValue);
}
```

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
