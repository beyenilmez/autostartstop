package com.autostartstop.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads Velocity proxy jars from PaperMC's Fill v3 API and caches them on disk so subsequent
 * test runs reuse the same artifact.
 *
 * <p>PaperMC does not publish proxy jars to a Maven repository — only the API artifact is on Maven.
 * The proxy itself is only served via {@code https://fill.papermc.io/v3/...} (which in turn
 * redirects to a content-addressed {@code fill-data.papermc.io} URL), so the integration tests
 * fetch them directly.
 *
 * <p>Both {@code version} and {@code build} accept the literal {@code "latest"} sentinel:
 *
 * <ul>
 *   <li>{@code version="latest"} resolves to the highest version in the {@code 3.0.0} family.
 *   <li>{@code build="latest"} resolves to the highest build of the chosen version.
 * </ul>
 */
final class VelocityJarResolver {

  private static final String API_BASE = "https://fill.papermc.io/v3/projects/velocity";
  private static final Path CACHE_DIR = Path.of("build", "velocity-cache");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

  /** The modern Velocity family this resolver targets when {@code version="latest"}. */
  private static final String VERSION_FAMILY = "3.0.0";

  private static final Pattern URL_FIELD = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern NAME_FIELD =
      Pattern.compile("\"name\"\\s*:\\s*\"(velocity-[^\"]+\\.jar)\"");

  private record BuildArtifact(String fileName, String downloadUrl) {}

  private VelocityJarResolver() {}

  /**
   * Returns a path to the Velocity jar for the given {@code version} and {@code build}, downloading
   * it on first request.
   *
   * @param version a Velocity version string (e.g. {@code "3.4.0"}, {@code "3.5.0-SNAPSHOT"}) or
   *     the literal {@code "latest"}
   * @param build a numeric build id (e.g. {@code "566"}) or the literal {@code "latest"}
   */
  static Path resolve(String version, String build) throws IOException, InterruptedException {
    Files.createDirectories(CACHE_DIR);
    String resolvedVersion = "latest".equals(version) ? fetchLatestVersion() : version;
    BuildArtifact artifact = fetchBuildArtifact(resolvedVersion, build);
    Path target = CACHE_DIR.resolve(artifact.fileName);
    if (Files.isRegularFile(target) && Files.size(target) > 0) {
      return target;
    }
    download(URI.create(artifact.downloadUrl), target);
    return target;
  }

  private static String fetchLatestVersion() throws IOException, InterruptedException {
    URI uri = URI.create(API_BASE);
    String body = httpGetText(uri);
    // Family blocks list versions newest-first, e.g. "3.0.0": ["3.5.0-SNAPSHOT", "3.4.0", ...].
    Pattern familyVersions =
        Pattern.compile("\"" + Pattern.quote(VERSION_FAMILY) + "\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");
    Matcher m = familyVersions.matcher(body);
    if (!m.find()) {
      throw new IOException(
          "Could not find '" + VERSION_FAMILY + "' family in PaperMC response: " + body);
    }
    return m.group(1);
  }

  private static BuildArtifact fetchBuildArtifact(String version, String build)
      throws IOException, InterruptedException {
    URI uri = URI.create(API_BASE + "/versions/" + version + "/builds/" + build);
    String body = httpGetText(uri);
    Matcher nameMatch = NAME_FIELD.matcher(body);
    Matcher urlMatch = URL_FIELD.matcher(body);
    if (!nameMatch.find() || !urlMatch.find()) {
      throw new IOException("Could not parse build artifact from " + uri + ": " + body);
    }
    return new BuildArtifact(nameMatch.group(1), urlMatch.group(1));
  }

  private static void download(URI uri, Path target) throws IOException, InterruptedException {
    Path tmp = Files.createTempFile(target.getParent(), "velocity-", ".part");
    try {
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build();
      HttpResponse<Path> response =
          newHttpClient().send(request, HttpResponse.BodyHandlers.ofFile(tmp));
      if (response.statusCode() != 200) {
        throw new IOException("Failed to download " + uri + ": HTTP " + response.statusCode());
      }
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private static String httpGetText(URI uri) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT).GET().build();
    HttpResponse<String> response =
        newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException("HTTP " + response.statusCode() + " for " + uri);
    }
    return response.body();
  }

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(HTTP_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
