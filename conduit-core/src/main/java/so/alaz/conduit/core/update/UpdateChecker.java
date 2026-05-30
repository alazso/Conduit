package so.alaz.conduit.core.update;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort update checker. Queries the GitHub releases API for the latest
 * tag, compares it to the running version, and logs an advisory if a newer
 * release exists. Network and parsing failures are logged at debug level and
 * never disrupt startup.
 */
@ApiStatus.Internal
public final class UpdateChecker {

    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final String currentVersion;
    private final URI latestReleaseEndpoint;
    private final Executor executor;
    private final Logger logger;
    private final HttpClient httpClient;

    public UpdateChecker(@NotNull String currentVersion, @NotNull URI latestReleaseEndpoint,
                         @NotNull Executor executor, @NotNull Logger logger) {
        this.currentVersion = currentVersion;
        this.latestReleaseEndpoint = latestReleaseEndpoint;
        this.executor = executor;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Run the check asynchronously on the configured executor.
     */
    public void checkAsync() {
        executor.execute(this::check);
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(latestReleaseEndpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.debug("Update check skipped: HTTP {}", response.statusCode());
                return;
            }
            parseAndReport(response.body());
        } catch (Exception e) {
            logger.debug("Update check failed: {}", e.getMessage());
        }
    }

    private void parseAndReport(String body) {
        Matcher matcher = TAG_NAME.matcher(body);
        if (!matcher.find()) {
            logger.debug("Update check: no tag_name in response");
            return;
        }
        try {
            SemanticVersion latest = SemanticVersion.parse(matcher.group(1));
            SemanticVersion current = SemanticVersion.parse(currentVersion);
            if (latest.isNewerThan(current)) {
                logger.info("A newer Conduit release is available: {} (running {}).", latest, current);
            }
        } catch (IllegalArgumentException e) {
            logger.debug("Update check: unparseable version ({})", e.getMessage());
        }
    }
}
