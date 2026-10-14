package com.monitorlatino.ytdetect.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class YtApiApplication {

    private static final Logger log = LoggerFactory.getLogger(YtApiApplication.class);

    public static void main(String[] args) {
        loadDotEnvIfExists();
        SpringApplication.run(YtApiApplication.class, args);
    }

    public static void loadDotEnvIfExists() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            envPath = Path.of("../.env");
        }
        if (Files.exists(envPath)) {
            try (var lines = Files.lines(envPath)) {
                lines.map(String::trim)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#") && l.contains("="))
                        .forEach(line -> {
                            int eqIdx = line.indexOf('=');
                            String key = line.substring(0, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                    (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        });
                log.info("Loaded environment variables from {}", envPath.toAbsolutePath());
            } catch (Exception e) {
                log.warn("Could not load .env file: {}", e.getMessage());
            }
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
