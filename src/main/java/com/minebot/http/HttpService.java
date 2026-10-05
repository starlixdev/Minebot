package com.minebot.http;

import com.minebot.util.ExecutorFactory;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;

public final class HttpService implements AutoCloseable {
   private static final int MAX_REDIRECTS = 5;
   private final ExecutorService executor;
   private final HttpClient client;
   private final Duration defaultTimeout;
   private final int maxResponseBytes;

   public HttpService(Duration var1, Duration var2, int var3, int var4) {
      if (var1 == null || var1.isZero() || var1.isNegative()) {
         throw new IllegalArgumentException("connectTimeout must be positive");
      } else if (var2 == null || var2.isZero() || var2.isNegative()) {
         throw new IllegalArgumentException("defaultTimeout must be positive");
      } else if (var3 < 1 || var3 > 64) {
         throw new IllegalArgumentException("workerThreads must be between 1 and 64");
      } else if (var4 < 1024) {
         throw new IllegalArgumentException("maxResponseBytes must be at least 1024");
      } else {
         this.defaultTimeout = var2;
         this.maxResponseBytes = var4;
         AtomicInteger var5 = new AtomicInteger();
         this.executor = ExecutorFactory.newBoundedFixedThreadPool(var3, var1x -> {
            Thread var2x = new Thread(var1x, "MineBOT-HTTP-" + var5.incrementAndGet());
            var2x.setDaemon(true);
            return var2x;
         });
         this.client = HttpClient.newBuilder().connectTimeout(var1).executor(this.executor).followRedirects(Redirect.NEVER).build();
      }
   }

   public Duration defaultTimeout() {
      return this.defaultTimeout;
   }

   public CompletableFuture<HttpService.Result> execute(HttpService.Request var1) {
      return this.execute(var1, System.nanoTime(), 0);
   }

   private CompletableFuture<HttpService.Result> execute(HttpService.Request var1, long var2, int var4) {
      HttpRequest var5;
      try {
         var5 = this.buildRequest(var1);
      } catch (RuntimeException var7) {
         return CompletableFuture.completedFuture(failure(var2, "INVALID_REQUEST", "Invalid HTTP request"));
      }

      return this.client.sendAsync(var5, limitedBodyHandler(this.maxResponseBytes)).handle((var5x, var6) -> {
         long var7x = elapsedMillis(var2);
         if (var6 != null) {
            return CompletableFuture.completedFuture(classifyFailure(var6, var7x));
         } else {
            Optional var9 = redirectTarget(var1.uri(), (HttpResponse<?>)var5x);
            if (var9.isPresent() && var4 < 5 && sameOrigin(var1.uri(), (URI)var9.get())) {
               return this.execute(redirectedRequest(var1, var5x.statusCode(), (URI)var9.get()), var2, var4 + 1);
            } else {
               byte[] var10 = var5x.body() == null ? new byte[0] : var5x.body();
               Charset var11 = charset(var5x.headers());
               String var12 = new String(var10, var11);
               int var13 = var5x.statusCode();
               boolean var14 = var13 >= 200 && var13 < 300;
               String var15 = var14 ? "" : "HTTP " + var13;
               String var16 = var14 ? "" : "HTTP";
               return CompletableFuture.completedFuture(new HttpService.Result(var14, var13, var12, var15, var16, copyHeaders(var5x.headers()), var7x));
            }
         }
      }).thenCompose(var0 -> (CompletionStage<HttpService.Result>)var0);
   }

   private static Optional<URI> redirectTarget(URI var0, HttpResponse<?> var1) {
      int var2 = var1.statusCode();
      if (var2 != 301 && var2 != 302 && var2 != 303 && var2 != 307 && var2 != 308) {
         return Optional.empty();
      } else {
         Optional var3 = var1.headers().firstValue("Location");
         if (!var3.isEmpty() && !((String)var3.get()).isBlank()) {
            try {
               URI var4 = var0.resolve((String)var3.get());
               String var5 = var4.getScheme();
               return var5 != null && (var5.equalsIgnoreCase("http") || var5.equalsIgnoreCase("https")) ? Optional.of(var4) : Optional.empty();
            } catch (RuntimeException var6) {
               return Optional.empty();
            }
         } else {
            return Optional.empty();
         }
      }
   }

   private static HttpService.Request redirectedRequest(HttpService.Request var0, int var1, URI var2) {
      String var3 = var0.method();
      String var4 = var0.body();
      boolean var5 = var1 == 303 || (var1 == 301 || var1 == 302) && var3.equalsIgnoreCase("POST");
      Map<String, List<String>> var6 = var0.headers();
      if (var5) {
         var3 = "GET";
         var4 = null;
         LinkedHashMap var7 = new LinkedHashMap();

         for (Entry var9 : var6.entrySet()) {
            if (!((String)var9.getKey()).equalsIgnoreCase("Content-Type")) {
               var7.put((String)var9.getKey(), (List)var9.getValue());
            }
         }

         var6 = var7;
      }

      return new HttpService.Request(var3, var2, (Map<String, List<String>>)var6, var4, var0.timeout());
   }

   private static boolean sameOrigin(URI var0, URI var1) {
      return var0.getScheme().equalsIgnoreCase(var1.getScheme())
         && Objects.equals(normalizeHost(var0.getHost()), normalizeHost(var1.getHost()))
         && effectivePort(var0) == effectivePort(var1);
   }

   private static String normalizeHost(String var0) {
      return var0 == null ? "" : var0.toLowerCase(Locale.ROOT);
   }

   private static int effectivePort(URI var0) {
      if (var0.getPort() >= 0) {
         return var0.getPort();
      } else if ("https".equalsIgnoreCase(var0.getScheme())) {
         return 443;
      } else {
         return "http".equalsIgnoreCase(var0.getScheme()) ? 80 : -1;
      }
   }

   private HttpRequest buildRequest(HttpService.Request var1) {
      String var2 = var1.method().trim().toUpperCase(Locale.ROOT);
      if (!var2.matches("[A-Z]+")) {
         throw new IllegalArgumentException("Invalid method");
      } else {
         Duration var3 = var1.timeout();
         if (!var3.isZero() && !var3.isNegative() && var3.compareTo(Duration.ofMinutes(5L)) <= 0) {
            Builder var4 = HttpRequest.newBuilder(var1.uri()).timeout(var3);
            int var5 = 0;

            for (Entry var7 : var1.headers().entrySet()) {
               String var8 = (String)var7.getKey();
               if (var8 == null || var8.isBlank() || var8.length() > 256) {
                  throw new IllegalArgumentException("Invalid header name");
               }

               for (String var10 : (List<String>)var7.getValue()) {
                  if (++var5 > 64) {
                     throw new IllegalArgumentException("Too many headers");
                  }

                  if (var10 == null || var10.length() > 16384) {
                     throw new IllegalArgumentException("Invalid header value");
                  }

                  var4.header(var8, var10);
               }
            }

            BodyPublisher var11 = var1.body() == null ? BodyPublishers.noBody() : BodyPublishers.ofString(var1.body(), StandardCharsets.UTF_8);
            var4.method(var2, var11);
            return var4.build();
         } else {
            throw new IllegalArgumentException("Invalid timeout");
         }
      }
   }

   private static BodyHandler<byte[]> limitedBodyHandler(int var0) {
      return var1 -> new HttpService.LimitedSubscriber(var0);
   }

   private static HttpService.Result classifyFailure(Throwable var0, long var1) {
      Throwable var3 = unwrap(var0);
      if (var3 instanceof HttpService.ResponseTooLargeException) {
         return new HttpService.Result(false, 0, "", "HTTP response exceeded the configured size limit", "RESPONSE_TOO_LARGE", Map.of(), var1);
      } else if (var3 instanceof HttpTimeoutException || var3 instanceof TimeoutException) {
         return new HttpService.Result(false, 0, "", "HTTP request timed out", "TIMEOUT", Map.of(), var1);
      } else if (containsCause(var3, UnresolvedAddressException.class) || containsCause(var3, UnknownHostException.class)) {
         return new HttpService.Result(false, 0, "", "DNS resolution failed", "DNS", Map.of(), var1);
      } else if (containsCause(var3, ConnectException.class)) {
         return new HttpService.Result(false, 0, "", "Connection failed", "CONNECTION", Map.of(), var1);
      } else {
         return containsCause(var3, SSLException.class)
            ? new HttpService.Result(false, 0, "", "TLS connection failed", "TLS", Map.of(), var1)
            : new HttpService.Result(false, 0, "", "Network request failed", "NETWORK", Map.of(), var1);
      }
   }

   private static HttpService.Result failure(long var0, String var2, String var3) {
      return new HttpService.Result(false, 0, "", var3, var2, Map.of(), elapsedMillis(var0));
   }

   private static Throwable unwrap(Throwable var0) {
      Throwable var1 = var0;

      while ((var1 instanceof CompletionException || var1 instanceof ExecutionException) && var1.getCause() != null) {
         var1 = var1.getCause();
      }

      return var1;
   }

   private static boolean containsCause(Throwable var0, Class<? extends Throwable> var1) {
      for (Throwable var2 = var0; var2 != null; var2 = var2.getCause()) {
         if (var1.isInstance(var2)) {
            return true;
         }
      }

      return false;
   }

   private static Charset charset(HttpHeaders var0) {
      Optional var1 = var0.firstValue("Content-Type");
      if (var1.isPresent()) {
         String var2 = (String)var1.get();

         for (String var6 : var2.split(";")) {
            String var7 = var6.trim();
            if (var7.regionMatches(true, 0, "charset=", 0, 8)) {
               String var8 = var7.substring(8).trim().replace("\"", "");

               try {
                  return Charset.forName(var8);
               } catch (UnsupportedCharsetException | IllegalCharsetNameException var10) {
               }
            }
         }
      }

      return StandardCharsets.UTF_8;
   }

   private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> var0) {
      if (var0 != null && !var0.isEmpty()) {
         LinkedHashMap var1 = new LinkedHashMap();

         for (Entry var3 : var0.entrySet()) {
            List var4 = (List)var3.getValue();
            var1.put((String)var3.getKey(), var4 == null ? List.of() : List.copyOf(var4));
         }

         return Collections.unmodifiableMap(var1);
      } else {
         return Map.of();
      }
   }

   private static Map<String, List<String>> copyHeaders(HttpHeaders var0) {
      LinkedHashMap var1 = new LinkedHashMap();
      var0.map().forEach((var1x, var2) -> var1.put(var1x.toLowerCase(Locale.ROOT), List.copyOf(var2)));
      return Collections.unmodifiableMap(var1);
   }

   private static long elapsedMillis(long var0) {
      return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - var0);
   }

   @Override
   public void close() {
      this.executor.shutdownNow();
   }

   private static final class LimitedSubscriber implements BodySubscriber<byte[]> {
      private final CompletableFuture<byte[]> body = new CompletableFuture<>();
      private final ByteArrayOutputStream out = new ByteArrayOutputStream();
      private final int maxBytes;
      private Subscription subscription;
      private int size;

      private LimitedSubscriber(int var1) {
         this.maxBytes = var1;
      }

      @Override
      public CompletionStage<byte[]> getBody() {
         return this.body;
      }

      @Override
      public void onSubscribe(Subscription var1) {
         if (this.subscription != null) {
            var1.cancel();
         } else {
            this.subscription = var1;
            var1.request(1L);
         }
      }

      public void onNext(List<ByteBuffer> var1) {
         try {
            for (ByteBuffer var3 : var1) {
               int var4 = var3.remaining();
               if ((long)this.size + var4 > this.maxBytes) {
                  this.subscription.cancel();
                  this.body.completeExceptionally(new HttpService.ResponseTooLargeException());
                  return;
               }

               byte[] var5 = new byte[var4];
               var3.get(var5);
               this.out.write(var5, 0, var5.length);
               this.size += var5.length;
            }

            this.subscription.request(1L);
         } catch (Throwable var6) {
            this.subscription.cancel();
            this.body.completeExceptionally(var6);
         }
      }

      @Override
      public void onError(Throwable var1) {
         this.body.completeExceptionally(var1);
      }

      @Override
      public void onComplete() {
         this.body.complete(this.out.toByteArray());
      }
   }

   public record Request(String method, URI uri, Map<String, List<String>> headers, String body, Duration timeout) {
      public Request(String method, URI uri, Map<String, List<String>> headers, String body, Duration timeout) {
         Objects.requireNonNull(method, "method");
         Objects.requireNonNull(uri, "uri");
         headers = HttpService.immutableHeaders(headers);
         Objects.requireNonNull(timeout, "timeout");
         this.method = method;
         this.uri = uri;
         this.headers = headers;
         this.body = body;
         this.timeout = timeout;
      }
   }

   private static final class ResponseTooLargeException extends RuntimeException {
      private static final long serialVersionUID = 1L;
   }

   public record Result(boolean ok, int status, String body, String error, String errorType, Map<String, List<String>> headers, long durationMillis) {
   }
}
