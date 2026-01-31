package com.autostartstop.update;

import com.autostartstop.Log;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Checks GitHub releases for newer plugin versions and notifies when an update is available.
 * Uses the hub4j github-api library for API access.
 */
public class UpdateChecker {
    private static final Logger logger = Log.get(UpdateChecker.class);
    private static final String GITHUB_RELEASES = "https://github.com/beyenilmez/autostartstop/releases";
    private static final String MODRINTH = "https://modrinth.com/plugin/autostartstop";
    private static final String HANGAR = "https://hangar.papermc.io/beyenilmez/AutoStartStop";
    private static final String REPO = "beyenilmez/autostartstop";

    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
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
     * Runs the update check asynchronously and logs a visible message if a newer version is available.
     */
    public void checkAsync(Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<UpdateInfo> latest = fetchLatestRelease();
                if (latest.isPresent() && isNewer(latest.get().getTagName(), currentVersion)) {
                    logUpdateAvailable(latest.get());
                }
            } catch (Exception e) {
                logger.debug("Update check failed: {}", e.getMessage());
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, executor);
    }

    private Optional<UpdateInfo> fetchLatestRelease() {
        try {
            GitHub github = GitHub.connectAnonymously();
            GHRepository repo = github.getRepository(REPO);
            Iterator<GHRelease> it = repo.listReleases().iterator();
            if (it.hasNext()) {
                GHRelease release = it.next();
                String tagName = release.getTagName();
                if (tagName != null && !tagName.isEmpty()) {
                    return Optional.of(new UpdateInfo(tagName));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.debug("Failed to fetch releases: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void logUpdateAvailable(UpdateInfo info) {
        String latest = info.getTagName();
        String currentDisplay = currentVersion.startsWith("v") ? currentVersion : "v" + currentVersion;
        if (!latest.startsWith("v")) {
            latest = "v" + latest;
        }
        logger.info(" ");
        logger.info(" ========== AutoStartStop Update Available ==========");
        logger.info(" {} (current) -> {} (latest)", currentDisplay, latest);
        logger.info(" GitHub:   {}", GITHUB_RELEASES);
        logger.info(" Modrinth: {}", MODRINTH);
        logger.info(" Hangar:   {}", HANGAR);
        logger.info(" =====================================================");
        logger.info(" ");
    }

    /**
     * Compares two version strings. Supports formats: 1.0.0, 1.0.0-alpha, 1.0.0-beta, v1.0.0.
     * Returns true if latest is newer than current.
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
     * Compare two normalized versions (e.g. "1.0.1-beta", "1.0.0").
     * Returns positive if a > b, negative if a < b, 0 if equal.
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
        if (aPre.isEmpty()) return 1;   // a is release, b is prerelease
        if (bPre.isEmpty()) return -1;  // a is prerelease, b is release
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

    private static final class UpdateInfo {
        private final String tagName;

        UpdateInfo(String tagName) {
            this.tagName = tagName;
        }

        String getTagName() { return tagName; }
    }
}
