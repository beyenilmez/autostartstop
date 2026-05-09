package com.autostartstop.update;

import com.autostartstop.PluginLogger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * Checks the project's build.gradle.kts on the main branch for a newer plugin version and notifies
 * when an update is available.
 */
public class UpdateChecker {
  private static final Logger logger = PluginLogger.get(UpdateChecker.class);
  private static final String GITHUB_RELEASES =
      "https://github.com/beyenilmez/autostartstop/releases";
  private static final String MODRINTH = "https://modrinth.com/plugin/autostartstop";
  private static final String HANGAR = "https://hangar.papermc.io/beyenilmez/AutoStartStop";
  private static final String BUILD_GRADLE_URL =
      "https://raw.githubusercontent.com/beyenilmez/autostartstop/refs/heads/main/build.gradle.kts";
  private static final Pattern VERSION_PATTERN =
      Pattern.compile("^\\s*version\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

  private static final AtomicInteger threadCounter = new AtomicInteger(0);
  private static final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r);
            t.setName("autostartstop-update-checker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
          });

  private final String currentVersion;

  public UpdateChecker(String currentVersion) {
    this.currentVersion = normalizeVersion(currentVersion);
  }

  /**
   * Runs the update check asynchronously and logs a visible message if a newer version is
   * available.
   */
  public void checkAsync(Runnable onComplete) {
    CompletableFuture.runAsync(
        () -> {
          try {
            Optional<String> latest = fetchLatestVersion();
            if (latest.isPresent() && isNewer(latest.get(), currentVersion)) {
              logUpdateAvailable(latest.get());
            }
          } catch (Exception e) {
            logger.debug("Update check failed: {}", e.getMessage());
          } finally {
            if (onComplete != null) {
              onComplete.run();
            }
          }
        },
        executor);
  }

  /** Shuts down the update checker executor. Should be called during plugin shutdown. */
  public static void shutdown() {
    logger.debug("Shutting down update checker executor...");
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.debug("Update checker executor did not terminate gracefully, forcing shutdown");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    logger.debug("Update checker executor shut down");
  }

  private Optional<String> fetchLatestVersion() {
    try (HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(BUILD_GRADLE_URL))
              .timeout(HTTP_TIMEOUT)
              .header("Accept", "text/plain")
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        logger.debug("Update check returned HTTP {}", response.statusCode());
        return Optional.empty();
      }
      return parseVersion(response.body());
    } catch (Exception e) {
      logger.debug("Failed to fetch build.gradle.kts: {}", e.getMessage());
      return Optional.empty();
    }
  }

  static Optional<String> parseVersion(String buildScript) {
    if (buildScript == null || buildScript.isEmpty()) {
      return Optional.empty();
    }
    Matcher m = VERSION_PATTERN.matcher(buildScript);
    if (m.find()) {
      String version = m.group(1).trim();
      if (!version.isEmpty()) {
        return Optional.of(version);
      }
    }
    return Optional.empty();
  }

  private void logUpdateAvailable(String latest) {
    String currentDisplay = currentVersion.startsWith("v") ? currentVersion : "v" + currentVersion;
    String latestDisplay = latest.startsWith("v") ? latest : "v" + latest;
    logger.info(" ");
    logger.info(" ========== AutoStartStop Update Available ==========");
    logger.info(" {} (current) -> {} (latest)", currentDisplay, latestDisplay);
    logger.info(" GitHub:   {}", GITHUB_RELEASES);
    logger.info(" Modrinth: {}", MODRINTH);
    logger.info(" Hangar:   {}", HANGAR);
    logger.info(" =====================================================");
    logger.info(" ");
  }

  /**
   * Compares two version strings. Supports formats: 1.0.0, 1.0.0-alpha, 1.0.0-beta, v1.0.0. Returns
   * true if latest is newer than current.
   */
  static boolean isNewer(String latest, String current) {
    String a = normalizeVersion(latest);
    String b = normalizeVersion(current);
    if (a.equals(b)) {
      return false;
    }
    return compareVersions(a, b) > 0;
  }

  private static String normalizeVersion(String v) {
    if (v == null || v.isEmpty()) {
      return "0.0.0";
    }
    v = v.trim();
    if (v.startsWith("v") || v.startsWith("V")) {
      v = v.substring(1);
    }
    return v;
  }

  /**
   * Compare two normalized versions (e.g. "1.0.1-beta", "1.0.0"). Returns positive if a > b,
   * negative if a < b, 0 if equal.
   */
  private static int compareVersions(String a, String b) {
    String[] aParts = a.split("-", 2);
    String[] bParts = b.split("-", 2);
    String aBase = aParts[0].trim();
    String bBase = bParts[0].trim();
    String aPre = aParts.length > 1 ? aParts[1].trim().toLowerCase() : "";
    String bPre = bParts.length > 1 ? bParts[1].trim().toLowerCase() : "";

    int baseCmp = compareBaseVersion(aBase, bBase);
    if (baseCmp != 0) {
      return baseCmp;
    }
    // Same base: no prerelease > prerelease; same type: compare lexicographically
    if (aPre.isEmpty() && bPre.isEmpty()) return 0;
    if (aPre.isEmpty()) return 1; // a is release, b is prerelease
    if (bPre.isEmpty()) return -1; // a is prerelease, b is release
    return aPre.compareTo(bPre);
  }

  private static int compareBaseVersion(String a, String b) {
    String[] aSeg = a.split(Pattern.quote("."));
    String[] bSeg = b.split(Pattern.quote("."));
    int max = Math.max(aSeg.length, bSeg.length);
    for (int i = 0; i < max; i++) {
      int aNum = i < aSeg.length ? parseSegment(aSeg[i]) : 0;
      int bNum = i < bSeg.length ? parseSegment(bSeg[i]) : 0;
      if (aNum != bNum) {
        return Integer.compare(aNum, bNum);
      }
    }
    return 0;
  }

  private static int parseSegment(String s) {
    if (s == null || s.isEmpty()) return 0;
    s = s.trim();
    int i = 0;
    while (i < s.length() && Character.isDigit(s.charAt(i))) {
      i++;
    }
    if (i == 0) return 0;
    try {
      return Integer.parseInt(s.substring(0, i));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
