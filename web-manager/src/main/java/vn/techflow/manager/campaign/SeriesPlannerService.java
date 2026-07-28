package vn.techflow.manager.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeriesPlannerService {
    private static final String MARKER = "SERIES_PLAN_B64=";
    private final ObjectMapper json;
    private final Path projectDirectory;
    private final String pythonCommand;
    private final String plannerScript;

    public SeriesPlannerService(
            ObjectMapper json,
            @Value("${techflow.project-dir:..}") String projectDirectory,
            @Value("${techflow.python-command:python}") String pythonCommand,
            @Value("${techflow.series-planner-script:series_planner.py}") String plannerScript) {
        this.json = json;
        this.projectDirectory = Path.of(projectDirectory).toAbsolutePath().normalize();
        this.pythonCommand = pythonCommand;
        this.plannerScript = plannerScript;
    }

    public JsonNode plan(Campaign campaign) {
        Process process = null;
        Path outputFile = null;
        try {
            outputFile = java.nio.file.Files.createTempFile("techflow-series-plan-", ".log");
            process = new ProcessBuilder(List.of(
                    pythonCommand, plannerScript,
                    "--theme", campaign.getTheme(),
                    "--episodes", String.valueOf(campaign.getEpisodeCount()),
                    "--audience", campaign.getAudience()
            )).directory(projectDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(Duration.ofMinutes(2).toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Series Planner exceeded the two-minute timeout");
            }
            String output = java.nio.file.Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("Series Planner exited with " + process.exitValue() + ": " + tail(output, 2000));
            }
            String payload = output.lines()
                    .filter(line -> line.startsWith(MARKER))
                    .map(line -> line.substring(MARKER.length()).trim())
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IOException("Series Planner metadata marker is missing"));
            return json.readTree(Base64.getUrlDecoder().decode(payload));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Series planning was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build the series plan: " + exception.getMessage(), exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (outputFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Temporary log only; do not mask the planner result.
                }
            }
        }
    }

    private static String tail(String value, int limit) {
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
