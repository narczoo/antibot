package pl.antybot.plugin.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MojangResolver {

    public static class MojangProfile {
        private final boolean premium;
        private final String mojangUuid;
        private final String exactName;
        private final long cachedAt;

        public MojangProfile(boolean premium, String mojangUuid, String exactName) {
            this.premium = premium;
            this.mojangUuid = mojangUuid;
            this.exactName = exactName;
            this.cachedAt = System.currentTimeMillis();
        }

        public boolean isPremium() {
            return premium;
        }

        public String getMojangUuid() {
            return mojangUuid;
        }

        public String getExactName() {
            return exactName;
        }

        public long getCachedAt() {
            return cachedAt;
        }
    }

    private static final int MAX_CACHE_SIZE = 5000;
    private static final long CACHE_TTL_MILLIS = 3600_000L; // 1 godzina

    private final HttpClient httpClient;
    private final Map<String, MojangProfile> cache = new ConcurrentHashMap<>();
    private final Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([a-zA-Z0-9_-]+)\"");
    private final Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    public MojangResolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public MojangProfile resolve(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new MojangProfile(false, null, username);
        }

        String lower = username.toLowerCase();
        long now = System.currentTimeMillis();

        MojangProfile cached = cache.get(lower);
        if (cached != null) {
            if ((now - cached.getCachedAt()) <= CACHE_TTL_MILLIS) {
                return cached;
            } else {
                cache.remove(lower);
            }
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "AntyBot-Auth")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            MojangProfile profile;
            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher idMatcher = idPattern.matcher(body);
                Matcher nameMatcher = namePattern.matcher(body);

                String id = idMatcher.find() ? idMatcher.group(1) : null;
                String exactName = nameMatcher.find() ? nameMatcher.group(1) : username;

                profile = new MojangProfile(true, id, exactName);
            } else {
                // 404 lub 204 = brak konta na Mojang (konto non-premium)
                profile = new MojangProfile(false, null, username);
            }

            ensureCacheCapacity();
            cache.put(lower, profile);
            return profile;
        } catch (Exception e) {
            // W razie bledu polaczenia z Mojang zwracamy non-premium bez zapisywania w cache na stale
            return new MojangProfile(false, null, username);
        }
    }

    private void ensureCacheCapacity() {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();
            if (cache.size() >= MAX_CACHE_SIZE) {
                // Usuwanie 20% najstarszych wpisów, aby zapobiec wyczerpaniu pamięci RAM
                int toRemove = MAX_CACHE_SIZE / 5;
                Iterator<String> it = cache.keySet().iterator();
                while (it.hasNext() && toRemove > 0) {
                    it.next();
                    it.remove();
                    toRemove--;
                }
            }
        }
    }

    public void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> (now - entry.getValue().getCachedAt()) > CACHE_TTL_MILLIS);
    }

    public void invalidate(String username) {
        if (username != null) {
            cache.remove(username.toLowerCase());
        }
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }
}
