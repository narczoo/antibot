package pl.antybot.plugin.manager;

import pl.antybot.plugin.AntiBotPlugin;
import pl.antybot.plugin.config.Settings;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiVpnManager {

    public enum VpnCheckStatus {
        CLEAN,
        VPN,
        HOSTING,
        ERROR,
        OVERLOAD,
        SKIPPED
    }

    public static class VpnResult {
        private final VpnCheckStatus status;
        private final boolean vpn;
        private final String provider;
        private final String reason;
        private final long timestamp;

        public VpnResult(VpnCheckStatus status, boolean vpn, String provider, String reason) {
            this.status = status;
            this.vpn = vpn;
            this.provider = provider;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }

        public VpnCheckStatus getStatus() {
            return status;
        }

        public boolean isVpn() {
            return vpn;
        }

        public String getProvider() {
            return provider;
        }

        public String getReason() {
            return reason;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isFailure() {
            return status == VpnCheckStatus.ERROR || status == VpnCheckStatus.OVERLOAD;
        }

        @Override
        public String toString() {
            String tag;
            switch (status) {
                case VPN -> tag = "§c[VPN/PROXY]";
                case HOSTING -> tag = "§c[HOSTING/DATACENTER]";
                case CLEAN -> tag = "§a[CZYSTE IP]";
                case OVERLOAD -> tag = "§e[PRZECIĄŻENIE]";
                case ERROR -> tag = "§6[BŁĄD API]";
                case SKIPPED -> tag = "§7[POMINIĘTO]";
                default -> tag = "§7[NIEZNANY]";
            }
            return tag + " §7(Źródło: §e" + provider + "§7, Opis: §f" + reason + "§7)";
        }
    }

    private final AntiBotPlugin plugin;
    private HttpClient httpClient;

    // Pamięć podręczna zweryfikowanych wyników (CLEAN, VPN, HOSTING)
    private final Map<String, VpnResult> verifiedCache = new ConcurrentHashMap<>();

    // Krótkotrwały cooldown dla błędów API / przeciążeń (zapobiega zarzucaniu API kolejnymi zapytaniami)
    private final Map<String, Long> failureCooldown = new ConcurrentHashMap<>();

    // Mechanizm Single-Flight: deduplikacja równoległych zapytań dla tego samego IP
    private final Map<String, CompletableFuture<VpnResult>> inFlightChecks = new ConcurrentHashMap<>();

    // Ograniczona pula wątków roboczych i kolejka zadań
    private ThreadPoolExecutor workerExecutor;

    // Semafor ograniczający liczbę równoległych zapytań HTTP do zewnętrznych API
    private Semaphore httpSemaphore;

    // Globalny licznik rate-limit zapytań na minutę
    private final AtomicInteger checksInCurrentMinute = new AtomicInteger(0);
    private volatile long currentMinuteWindow = System.currentTimeMillis() / 60000L;

    // Metryki i statystyki do monitorowania
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong singleFlightHits = new AtomicLong(0);
    private final AtomicLong rejectedChecks = new AtomicLong(0);
    private final AtomicLong rateLimitedChecks = new AtomicLong(0);
    private final AtomicLong apiErrors = new AtomicLong(0);
    private final AtomicLong blockedVpns = new AtomicLong(0);

    // Throttling logów administracyjnych (zapobiega spamowaniu konsoli przy ataku)
    private volatile long lastLogTimestamp = 0L;
    private final AtomicInteger suppressedLogCount = new AtomicInteger(0);

    // Wzorce regex dla ProxyCheck.io
    private final Pattern proxyCheckStatusPattern = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private final Pattern proxyCheckProxyPattern = Pattern.compile("\"proxy\"\\s*:\\s*\"(yes|no)\"", Pattern.CASE_INSENSITIVE);
    private final Pattern proxyCheckTypePattern = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    // Wzorce regex dla IP-API.com
    private final Pattern ipApiStatusPattern = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private final Pattern ipApiProxyPattern = Pattern.compile("\"proxy\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private final Pattern ipApiHostingPattern = Pattern.compile("\"hosting\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    public AntiVpnManager(AntiBotPlugin plugin) {
        this.plugin = plugin;
        initExecutionEngine();
    }

    public synchronized void initExecutionEngine() {
        Settings s = plugin.getSettings();
        int threads = Math.max(1, s.getAntiVpnMaxWorkerThreads());
        int queueSize = Math.max(10, s.getAntiVpnMaxPendingTasks());
        int permits = Math.max(1, s.getAntiVpnMaxConcurrentRequests());
        int connectTimeout = Math.max(500, s.getAntiVpnConnectTimeoutMs());

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();

        this.httpSemaphore = new Semaphore(permits, true);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AntyBot-AntiVPN-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        // Zgodnie z wymaganiami: ograniczona kolejka i kontrolowana pula wątków.
        // Odrzucenie zadania rzuca RejectedExecutionException zamiast CallerRunsPolicy,
        // aby nie blokować wątków Paper/Netty zapytaniami HTTP przy przeciążeniu.
        this.workerExecutor = new ThreadPoolExecutor(
                threads,
                threads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                threadFactory,
                (r, executor) -> {
                    rejectedChecks.incrementAndGet();
                    throw new RejectedExecutionException("Kolejka zapytań AntiVPN jest pełna (rozmiar: " + queueSize + ")");
                }
        );
    }

    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) return false;
        for (int i = 0; i < ip.length(); i++) {
            char c = ip.charAt(i);
            if (c != '.' && c != ':' && (c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                return false;
            }
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.getHostAddress().equalsIgnoreCase(ip);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPrivateOrLocal(String ip) {
        if (ip == null || ip.isEmpty()) return true;
        if (ip.equalsIgnoreCase("127.0.0.1") || ip.equalsIgnoreCase("0:0:0:0:0:0:0:1") || ip.equalsIgnoreCase("localhost")) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Główna metoda asynchroniczna sprawdzająca adres IP z wykorzystaniem
     * Single-Flight, semafora, limitów zapytań i pamięci podręcznej.
     */
    public CompletableFuture<VpnResult> checkAsync(String ip, String playerName, UUID uuid, boolean isTrusted) {
        totalChecks.incrementAndGet();

        // 1. Walidacja adresu IP
        if (!isValidIp(ip)) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.ERROR, false, "System", "Niepoprawny format adresu IP"));
        }

        // 2. Adresy lokalne / prywatne
        if (isPrivateOrLocal(ip)) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.SKIPPED, false, "Lokalny", "Prywatny/Lokalny adres IP"));
        }

        Settings settings = plugin.getSettings();

        // 3. Biała lista IP
        if (settings.getAntiVpnWhitelistIps().contains(ip)) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.SKIPPED, false, "Biała Lista", "Adres IP na białej liście"));
        }

        // 4. Biała lista graczy
        if (playerName != null && settings.getAntiVpnWhitelistPlayers().contains(playerName.toLowerCase())) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.SKIPPED, false, "Biała Lista", "Gracz na białej liście"));
        }

        // 5. Zaufani gracze (TrustManager)
        if (settings.isAntiVpnBypassTrusted() && isTrusted) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.SKIPPED, false, "TrustManager", "Zaufany gracz omija weryfikację VPN"));
        }

        // 6. Tryb ATTACK_ONLY
        if (settings.getAntiVpnTriggerMode().equalsIgnoreCase("ATTACK_ONLY") && !plugin.getAttackManager().isAttackMode()) {
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.SKIPPED, false, "Tryb", "Weryfikacja wyłączona poza trybem ataku"));
        }

        // 7. Sprawdzenie pamięci podręcznej (Cache)
        long maxAgeMillis = (long) settings.getAntiVpnCacheTimeHours() * 3600_000L;
        VpnResult cached = verifiedCache.get(ip);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.getTimestamp() < maxAgeMillis) {
                cacheHits.incrementAndGet();
                return CompletableFuture.completedFuture(cached);
            } else {
                verifiedCache.remove(ip);
            }
        }

        // 8. Sprawdzenie cooldownu błędów dla danego IP
        Long lastFailure = failureCooldown.get(ip);
        if (lastFailure != null) {
            long cooldownMillis = (long) settings.getAntiVpnIpCheckCooldownSeconds() * 1000L;
            if (System.currentTimeMillis() - lastFailure < cooldownMillis) {
                return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.ERROR, false, "Cooldown", "Aktywny cooldown po wcześniejszym błędzie API"));
            } else {
                failureCooldown.remove(ip);
            }
        }

        // 9. Single-Flight: deduplikacja zapytań dla identycznego adresu IP w locie
        CompletableFuture<VpnResult> inFlight = inFlightChecks.get(ip);
        if (inFlight != null) {
            singleFlightHits.incrementAndGet();
            return inFlight;
        }

        // 10. Globalny rate limit nowych zapytań na minutę
        if (!tryAcquireGlobalRateLimit()) {
            rateLimitedChecks.incrementAndGet();
            logThrottledWarning("[AntiVPN] Osiągnięto globalny limit zapytań HTTP na minutę (" + settings.getAntiVpnGlobalChecksPerMinute() + "/min). Zastosowano politykę przeciążenia.");
            return CompletableFuture.completedFuture(new VpnResult(VpnCheckStatus.OVERLOAD, false, "RateLimit", "Przekroczono globalny limit zapytań na minutę"));
        }

        // 11. Zarejestrowanie zadania Single-Flight i wysłanie do puli wątków
        CompletableFuture<VpnResult> promise = new CompletableFuture<>();
        CompletableFuture<VpnResult> previous = inFlightChecks.putIfAbsent(ip, promise);
        if (previous != null) {
            singleFlightHits.incrementAndGet();
            return previous;
        }

        try {
            workerExecutor.execute(() -> {
                try {
                    VpnResult result = executeHttpCheck(ip);
                    if (result.isFailure()) {
                        failureCooldown.put(ip, System.currentTimeMillis());
                    } else {
                        verifiedCache.put(ip, result);
                        if (result.isVpn()) {
                            blockedVpns.incrementAndGet();
                        }
                    }
                    promise.complete(result);
                } catch (Throwable t) {
                    apiErrors.incrementAndGet();
                    failureCooldown.put(ip, System.currentTimeMillis());
                    promise.complete(new VpnResult(VpnCheckStatus.ERROR, false, "System", "Nieoczekiwany błąd: " + t.getMessage()));
                } finally {
                    inFlightChecks.remove(ip);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlightChecks.remove(ip);
            logThrottledWarning("[AntiVPN] Przeciążenie: odrzucono zadanie z powodu pełnej kolejki wykonawczej. Aktywne zadania: " + workerExecutor.getActiveCount() + ", Oczekujące: " + workerExecutor.getQueue().size());
            VpnResult overloadResult = new VpnResult(VpnCheckStatus.OVERLOAD, false, "Kolejka", "Kolejka zapytań AntiVPN jest pełna");
            promise.complete(overloadResult);
            return CompletableFuture.completedFuture(overloadResult);
        }

        return promise;
    }

    /**
     * Bezpieczne synchroniczne wywołanie z ograniczonym czasem oczekiwania,
     * dedykowane dla AsyncPlayerPreLoginEvent (wątek asynchroniczny Netty/Paper).
     */
    public VpnResult checkSyncWithTimeout(String ip, String playerName, UUID uuid, boolean isTrusted) {
        CompletableFuture<VpnResult> future = checkAsync(ip, playerName, uuid, isTrusted);
        if (future.isDone()) {
            try {
                return future.get();
            } catch (Exception e) {
                return new VpnResult(VpnCheckStatus.ERROR, false, "System", "Błąd odczytu: " + e.getMessage());
            }
        }

        Settings s = plugin.getSettings();
        long waitTimeoutMs = s.getAntiVpnRequestTimeoutMs() + 500L;

        try {
            return future.get(waitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logThrottledWarning("[AntiVPN] Przekroczono limit czasu oczekiwania na weryfikację IP " + ip + " (" + waitTimeoutMs + "ms).");
            failureCooldown.put(ip, System.currentTimeMillis());
            return new VpnResult(VpnCheckStatus.OVERLOAD, false, "Timeout", "Przekroczono limit czasu oczekiwania");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VpnResult(VpnCheckStatus.OVERLOAD, false, "Przerwano", "Przerwano oczekiwanie na odpowiedź API");
        } catch (ExecutionException e) {
            apiErrors.incrementAndGet();
            failureCooldown.put(ip, System.currentTimeMillis());
            return new VpnResult(VpnCheckStatus.ERROR, false, "Błąd", "Wyjątek podczas weryfikacji: " + e.getCause().getMessage());
        }
    }

    public boolean isVpn(String ip, String playerName, UUID uuid, boolean isTrusted) {
        VpnResult res = checkSyncWithTimeout(ip, playerName, uuid, isTrusted);
        return res.isVpn();
    }

    private boolean tryAcquireGlobalRateLimit() {
        int max = plugin.getSettings().getAntiVpnGlobalChecksPerMinute();
        if (max <= 0) return true;
        long minute = System.currentTimeMillis() / 60000L;
        if (minute != currentMinuteWindow) {
            currentMinuteWindow = minute;
            checksInCurrentMinute.set(0);
        }
        return checksInCurrentMinute.incrementAndGet() <= max;
    }

    private VpnResult executeHttpCheck(String ip) {
        Settings s = plugin.getSettings();
        String provider = s.getAntiVpnProvider().toUpperCase();
        boolean blockHosting = s.isAntiVpnBlockHosting();
        String apiKey = s.getAntiVpnApiKey();
        long permitTimeout = s.getAntiVpnRequestTimeoutMs();

        boolean permitAcquired = false;
        try {
            // Ograniczenie liczby równoległych zapytań HTTP za pomocą semafora
            permitAcquired = httpSemaphore.tryAcquire(permitTimeout, TimeUnit.MILLISECONDS);
            if (!permitAcquired) {
                logThrottledWarning("[AntiVPN] Osiągnięto limit równoczesnych zapytań HTTP (" + s.getAntiVpnMaxConcurrentRequests() + "). Oczekiwanie na permit wygasło.");
                return new VpnResult(VpnCheckStatus.OVERLOAD, false, "Semafor", "Brak wolnych permitów HTTP w wyznaczonym czasie");
            }

            VpnResult result = null;

            if (provider.equals("PROXYCHECK")) {
                result = queryProxyCheck(ip, apiKey, blockHosting);
            } else if (provider.equals("IP_API")) {
                result = queryIpApi(ip, blockHosting);
            } else {
                // Tryb AUTO: ProxyCheck.io jako główny, fallback do IP-API.com
                result = queryProxyCheck(ip, apiKey, blockHosting);
                if (result == null || result.getStatus() == VpnCheckStatus.ERROR) {
                    result = queryIpApi(ip, blockHosting);
                }
            }

            if (result != null) {
                return result;
            }

            apiErrors.incrementAndGet();
            return new VpnResult(VpnCheckStatus.ERROR, false, "API", "Brak poprawnej odpowiedzi od dostawców baz VPN");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new VpnResult(VpnCheckStatus.OVERLOAD, false, "Semafor", "Przerwano oczekiwanie na permit");
        } finally {
            if (permitAcquired) {
                httpSemaphore.release();
            }
        }
    }

    private VpnResult queryProxyCheck(String ip, String apiKey, boolean blockHosting) {
        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            StringBuilder urlBuilder = new StringBuilder("https://proxycheck.io/v2/").append(encodedIp).append("?vpn=1&asn=1");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                urlBuilder.append("&key=").append(URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8));
            }

            int timeoutMs = plugin.getSettings().getAntiVpnRequestTimeoutMs();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "AntyBot-Paper1.21.4")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();

                Matcher statusMatcher = proxyCheckStatusPattern.matcher(body);
                if (statusMatcher.find()) {
                    String status = statusMatcher.group(1).toLowerCase();
                    if (!status.equals("ok") && !status.equals("warning")) {
                        // Np. "denied" lub limit zapytań
                        return new VpnResult(VpnCheckStatus.ERROR, false, "ProxyCheck.io", "Status odpowiedzi: " + status);
                    }
                }

                Matcher proxyMatcher = proxyCheckProxyPattern.matcher(body);
                Matcher typeMatcher = proxyCheckTypePattern.matcher(body);

                String isProxyStr = proxyMatcher.find() ? proxyMatcher.group(1).toLowerCase() : "no";
                String typeStr = typeMatcher.find() ? typeMatcher.group(1) : "Unknown";

                boolean isProxy = isProxyStr.equals("yes");
                boolean isBadType = typeStr.equalsIgnoreCase("VPN") ||
                        typeStr.equalsIgnoreCase("TOR") ||
                        typeStr.equalsIgnoreCase("Compromised Server");

                if (isProxy || isBadType) {
                    return new VpnResult(VpnCheckStatus.VPN, true, "ProxyCheck.io", "Wykryto VPN/Proxy (" + typeStr + ")");
                }

                if (blockHosting && (typeStr.equalsIgnoreCase("Hosting") || typeStr.toLowerCase().contains("data center") || typeStr.equalsIgnoreCase("Business"))) {
                    if (typeStr.equalsIgnoreCase("Hosting") || typeStr.toLowerCase().contains("data center")) {
                        return new VpnResult(VpnCheckStatus.HOSTING, true, "ProxyCheck.io", "Centrum Danych/Hosting (" + typeStr + ")");
                    }
                }

                return new VpnResult(VpnCheckStatus.CLEAN, false, "ProxyCheck.io", "Czyste IP (" + typeStr + ")");
            } else {
                return new VpnResult(VpnCheckStatus.ERROR, false, "ProxyCheck.io", "Kod HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logThrottledWarning("[AntiVPN] Błąd połączenia z ProxyCheck.io dla " + ip + ": " + e.getMessage());
            return new VpnResult(VpnCheckStatus.ERROR, false, "ProxyCheck.io", "Błąd połączenia: " + e.getMessage());
        }
    }

    private VpnResult queryIpApi(String ip, boolean blockHosting) {
        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            int timeoutMs = plugin.getSettings().getAntiVpnRequestTimeoutMs();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + encodedIp + "?fields=status,message,proxy,hosting"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "AntyBot-Paper1.21.4")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();

                Matcher statusMatcher = ipApiStatusPattern.matcher(body);
                if (statusMatcher.find() && statusMatcher.group(1).equalsIgnoreCase("success")) {
                    Matcher proxyMatcher = ipApiProxyPattern.matcher(body);
                    Matcher hostingMatcher = ipApiHostingPattern.matcher(body);

                    boolean proxy = proxyMatcher.find() && Boolean.parseBoolean(proxyMatcher.group(1));
                    boolean hosting = hostingMatcher.find() && Boolean.parseBoolean(hostingMatcher.group(1));

                    if (proxy) {
                        return new VpnResult(VpnCheckStatus.VPN, true, "IP-API", "Wykryto sieć VPN / Proxy");
                    }

                    if (blockHosting && hosting) {
                        return new VpnResult(VpnCheckStatus.HOSTING, true, "IP-API", "Wykryto Centrum Danych / Serwer Hostingowy");
                    }

                    return new VpnResult(VpnCheckStatus.CLEAN, false, "IP-API", "Czyste IP domowe");
                } else {
                    return new VpnResult(VpnCheckStatus.ERROR, false, "IP-API", "Odpowiedź błędu z serwera");
                }
            } else {
                return new VpnResult(VpnCheckStatus.ERROR, false, "IP-API", "Kod HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            logThrottledWarning("[AntiVPN] Błąd połączenia z IP-API dla " + ip + ": " + e.getMessage());
            return new VpnResult(VpnCheckStatus.ERROR, false, "IP-API", "Błąd połączenia: " + e.getMessage());
        }
    }

    private void logThrottledWarning(String message) {
        long now = System.currentTimeMillis();
        if (now - lastLogTimestamp > 10000L) {
            int suppressed = suppressedLogCount.getAndSet(0);
            if (suppressed > 0) {
                plugin.getLogger().warning(message + " (+ " + suppressed + " podobnych zdarzeń w ostatnich 10s)");
            } else {
                plugin.getLogger().warning(message);
            }
            lastLogTimestamp = now;
        } else {
            suppressedLogCount.incrementAndGet();
        }
    }

    public void cleanExpiredCache() {
        long maxAgeMillis = (long) plugin.getSettings().getAntiVpnCacheTimeHours() * 3600_000L;
        long now = System.currentTimeMillis();
        verifiedCache.entrySet().removeIf(entry -> (now - entry.getValue().getTimestamp()) > maxAgeMillis);

        long cooldownMillis = (long) plugin.getSettings().getAntiVpnIpCheckCooldownSeconds() * 1000L;
        failureCooldown.entrySet().removeIf(entry -> (now - entry.getValue()) > cooldownMillis);
    }

    public void clearCache() {
        verifiedCache.clear();
        failureCooldown.clear();
    }

    public void shutdown() {
        if (workerExecutor != null) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        inFlightChecks.clear();
        failureCooldown.clear();
    }

    // Gettery do monitoringu i komendy /antibot vpn status
    public int getCacheSize() {
        return verifiedCache.size();
    }

    public int getFailureCooldownCount() {
        return failureCooldown.size();
    }

    public int getInFlightCount() {
        return inFlightChecks.size();
    }

    public int getActiveWorkerThreads() {
        return workerExecutor != null ? workerExecutor.getActiveCount() : 0;
    }

    public int getPendingTasksCount() {
        return workerExecutor != null ? workerExecutor.getQueue().size() : 0;
    }

    public int getAvailableHttpPermits() {
        return httpSemaphore != null ? httpSemaphore.availablePermits() : 0;
    }

    public long getTotalChecks() {
        return totalChecks.get();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getSingleFlightHits() {
        return singleFlightHits.get();
    }

    public long getRejectedChecks() {
        return rejectedChecks.get();
    }

    public long getRateLimitedChecks() {
        return rateLimitedChecks.get();
    }

    public long getApiErrors() {
        return apiErrors.get();
    }

    public long getBlockedVpns() {
        return blockedVpns.get();
    }

    public Map<String, VpnResult> getCache() {
        return verifiedCache;
    }
}
