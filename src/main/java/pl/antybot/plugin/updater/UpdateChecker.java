package pl.antybot.plugin.updater;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.antybot.plugin.AntiBotPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    public static class UpdateResult {
        private final boolean updateAvailable;
        private final String currentVersion;
        private final String latestVersion;
        private final String downloadUrl;
        private final boolean error;
        private final String errorMessage;

        public UpdateResult(boolean updateAvailable, String currentVersion, String latestVersion, String downloadUrl) {
            this.updateAvailable = updateAvailable;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.error = false;
            this.errorMessage = null;
        }

        public UpdateResult(String currentVersion, String errorMessage) {
            this.updateAvailable = false;
            this.currentVersion = currentVersion;
            this.latestVersion = currentVersion;
            this.downloadUrl = null;
            this.error = true;
            this.errorMessage = errorMessage;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public boolean isError() {
            return error;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final AntiBotPlugin plugin;
    private final HttpClient httpClient;
    private BukkitTask scheduledTask;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;
    private volatile String downloadUrl = null;

    private final Pattern tagNamePattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private final Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private final Pattern htmlUrlPattern = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public UpdateChecker(AntiBotPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    public void start() {
        stop();
        if (!plugin.getSettings().isUpdateCheckerEnabled()) {
            return;
        }

        // Pierwsze sprawdzenie asynchronicznie po 3 sekundach od startu serwera
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            checkAsync().thenAccept(this::handleStartupResult);
        }, 60L);

        // Cykliczne sprawdzanie jeśli skonfigurowano interwał
        int intervalHours = plugin.getSettings().getUpdateCheckerIntervalHours();
        if (intervalHours > 0) {
            long intervalTicks = intervalHours * 3600L * 20L;
            scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                checkAsync().thenAccept(res -> {
                    if (res.isUpdateAvailable()) {
                        plugin.getLogger().info("[UpdateChecker] Nowa wersja v" + res.getLatestVersion() + " jest dostępna do pobrania: " + res.getDownloadUrl());
                    }
                });
            }, intervalTicks, intervalTicks);
        }
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public CompletableFuture<UpdateResult> checkAsync() {
        String repo = plugin.getSettings().getUpdateCheckerGithubRepo();
        String currentVersion = plugin.getDescription().getVersion();

        if (repo == null || repo.trim().isEmpty() || !repo.contains("/")) {
            return CompletableFuture.completedFuture(new UpdateResult(currentVersion, "Niepoprawna nazwa repozytorium GitHub w config.yml (wymagany format: wlasciciel/repo)"));
        }

        String targetRepo = repo.trim();
        return CompletableFuture.supplyAsync(() -> queryGithub(targetRepo, currentVersion));
    }

    private UpdateResult queryGithub(String repo, String currentVersion) {
        try {
            // 1. Próba pobrania najnowszego wydania (Releases)
            String releasesUrl = "https://api.github.com/repos/" + repo + "/releases/latest";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(releasesUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "AntyBot-UpdateChecker")
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                Matcher tagMatcher = tagNamePattern.matcher(body);
                String tag = tagMatcher.find() ? tagMatcher.group(1) : null;

                if (tag == null) {
                    Matcher nameMatcher = namePattern.matcher(body);
                    tag = nameMatcher.find() ? nameMatcher.group(1) : null;
                }

                if (tag != null) {
                    Matcher urlMatcher = htmlUrlPattern.matcher(body);
                    String url = urlMatcher.find() ? urlMatcher.group(1) : "https://github.com/" + repo + "/releases/latest";
                    return processFoundVersion(tag, currentVersion, url);
                }
            } else if (response.statusCode() == 404) {
                // 2. Jeśli brak oficjalnego Release, sprawdzamy najnowsze tagi Git
                String tagsUrl = "https://api.github.com/repos/" + repo + "/tags";
                HttpRequest tagsRequest = HttpRequest.newBuilder()
                        .uri(URI.create(tagsUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "AntyBot-UpdateChecker")
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> tagsResponse = httpClient.send(tagsRequest, HttpResponse.BodyHandlers.ofString());
                if (tagsResponse.statusCode() == 200) {
                    String body = tagsResponse.body();
                    Matcher nameMatcher = namePattern.matcher(body);
                    if (nameMatcher.find()) {
                        String tag = nameMatcher.group(1);
                        String url = "https://github.com/" + repo + "/releases";
                        return processFoundVersion(tag, currentVersion, url);
                    }
                }
            }

            return new UpdateResult(currentVersion, "GitHub API zwróciło status HTTP " + response.statusCode());
        } catch (Exception e) {
            return new UpdateResult(currentVersion, "Błąd połączenia z GitHub API: " + e.getMessage());
        }
    }

    private UpdateResult processFoundVersion(String remoteTag, String currentVersion, String url) {
        String cleanRemote = remoteTag.replaceAll("(?i)^v", "").trim();
        String cleanCurrent = currentVersion.replaceAll("(?i)^v", "").trim();

        boolean hasNewer = compareVersions(cleanCurrent, cleanRemote) < 0;

        this.updateAvailable = hasNewer;
        this.latestVersion = cleanRemote;
        this.downloadUrl = url;

        return new UpdateResult(hasNewer, currentVersion, cleanRemote, url);
    }

    private void handleStartupResult(UpdateResult res) {
        if (res.isError()) {
            plugin.getLogger().log(Level.FINE, "[UpdateChecker] Nie udało się sprawdzić aktualizacji: " + res.getErrorMessage());
            return;
        }

        if (res.isUpdateAvailable()) {
            plugin.getLogger().info("==========================================================");
            plugin.getLogger().info("  DOSTĘPNA JEST NOWA WERSJA PLUGINU ANTYBOT!");
            plugin.getLogger().info("  Zainstalowana wersja: v" + res.getCurrentVersion());
            plugin.getLogger().info("  Najnowsza wersja:     v" + res.getLatestVersion());
            plugin.getLogger().info("  Pobierz aktualizację: " + res.getDownloadUrl());
            plugin.getLogger().info("==========================================================");
        } else {
            plugin.getLogger().info("[UpdateChecker] Korzystasz z najnowszej wersji pluginu (v" + res.getCurrentVersion() + ").");
        }
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "0";
        if (v2 == null) v2 = "0";

        String[] parts1 = v1.split("[.-]");
        String[] parts2 = v2.split("[.-]");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int num1 = 0;
            int num2 = 0;

            if (i < parts1.length) {
                try {
                    num1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {
                }
            }

            if (i < parts2.length) {
                try {
                    num2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
                } catch (Exception ignored) {
                }
            }

            if (num1 < num2) return -1;
            if (num1 > num2) return 1;
        }

        return 0;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion != null ? latestVersion : plugin.getDescription().getVersion();
    }

    public String getCurrentVersion() {
        return plugin.getDescription().getVersion();
    }

    public String getDownloadUrl() {
        return downloadUrl != null ? downloadUrl : "https://github.com/" + plugin.getSettings().getUpdateCheckerGithubRepo() + "/releases/latest";
    }

    public String getGithubRepo() {
        return plugin.getSettings().getUpdateCheckerGithubRepo();
    }
}
