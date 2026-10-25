package mithila001.service_advertiser.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

class U_LoggingConfigurationTests {
    @TempDir Path directory;

    @Test void nativeJsonFilesRotateAndConsoleStaysReadable() throws Exception {
        String console = runProbe("file");
        assertTrue(console.contains("request_id=configuration-check"));
        assertTrue(console.contains("method=\"GET\""));
        try (var paths = Files.list(directory)) {
            var logs = paths.filter(p -> p.getFileName().toString().endsWith(".json")
                    || p.getFileName().toString().endsWith(".gz")).toList();
            assertTrue(logs.size() > 1, "Size rotation must produce an archive");
            var mapper = JsonMapper.builder().build();
            int count = 0;
            for (Path log : logs) {
                String content;
                if (log.toString().endsWith(".gz")) {
                    try (var input = new GZIPInputStream(Files.newInputStream(log))) {
                        content = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } else content = Files.readString(log);
                for (String line : content.lines().toList()) {
                    var json = mapper.readTree(line);
                    assertEquals("configuration-check", json.get("request_id").stringValue());
                    assertEquals("GET", json.get("method").stringValue());
                    assertNotNull(json.get("@timestamp"));
                    count++;
                }
            }
            assertTrue(count > 0);
        }
    }

    @Test void emptyFileNameDisablesFilesAndDockerConsoleIsJson() throws Exception {
        String console = runProbe("docker");
        assertFalse(Files.exists(directory.resolve("application.json")));
        var mapper = JsonMapper.builder().build();
        var jsonLines = console.lines().filter(line -> line.startsWith("{" )).toList();
        assertFalse(jsonLines.isEmpty());
        for (String line : jsonLines) {
            assertEquals("configuration-check", mapper.readTree(line).get("request_id").stringValue());
        }
    }

    private String runProbe(String mode) throws Exception {
        Path output = directory.resolve("console-" + mode + ".txt");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path"), Probe.class.getName(), directory.toString(), mode)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Logging configuration probe timed out");
        }
        String result = Files.readString(output);
        assertEquals(0, process.exitValue(), result);
        return result;
    }

    // A child JVM keeps Boot's logging initialization and system properties out of other tests.
    public static class Probe {
        public static void main(String[] args) throws Exception {
            var environment = new StandardEnvironment();
            var yaml = new YamlPropertySourceLoader().load("application",
                    new FileSystemResource("src/main/resources/application.yaml"));
            yaml.forEach(source -> environment.getPropertySources().addLast(source));
            boolean docker = args[1].equals("docker");
            environment.getPropertySources().addFirst(new MapPropertySource("probe", Map.of(
                    "LOGGING_FILE_NAME", docker ? "" : Path.of(args[0], "application.json").toString(),
                    "logging.structured.format.console", docker ? "logstash" : "",
                    "logging.logback.rollingpolicy.max-file-size", "1KB",
                    "logging.logback.rollingpolicy.total-size-cap", "8KB")));
            var system = new LogbackLoggingSystem(Probe.class.getClassLoader());
            var file = LogFile.get(environment);
            if (docker && file != null) throw new AssertionError("Empty LOGGING_FILE_NAME must disable files");
            system.beforeInitialize();
            system.getSystemProperties(environment).apply(file);
            system.initialize(new LoggingInitializationContext(environment), null, file);
            MDC.put("request_id", "configuration-check");
            var log = LoggerFactory.getLogger(Probe.class);
            for (int i = 0; i < 100; i++) {
                log.atInfo().addKeyValue("method", "GET").addKeyValue("event", "configuration_check")
                        .log("Log rotation check " + i + " " + "x".repeat(100));
            }
            MDC.clear();
            ((ch.qos.logback.classic.Logger) log).getLoggerContext().stop();
        }
    }
}
