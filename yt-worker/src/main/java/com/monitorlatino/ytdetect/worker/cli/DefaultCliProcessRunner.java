package com.monitorlatino.ytdetect.worker.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultCliProcessRunner implements CliProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCliProcessRunner.class);
    private static final long DEFAULT_TIMEOUT_MINUTES = 60;

    @Override
    public ProcessResult runCommand(List<String> command, File workingDirectory) throws IOException, InterruptedException {
        log.debug("Executing CLI command: {} in directory {}", String.join(" ", command), workingDirectory);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory);
        }

        Process process = processBuilder.start();

        StringBuilder outputBuilder = new StringBuilder();
        StringBuilder errorBuilder = new StringBuilder();

        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                log.debug("Error reading process output: {}", e.getMessage());
            }
        });

        Thread errorThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorBuilder.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                log.debug("Error reading process error stream: {}", e.getMessage());
            }
        });

        outputThread.start();
        errorThread.start();

        boolean completed = process.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            log.error("Command timed out after {} minutes: {}", DEFAULT_TIMEOUT_MINUTES, command.get(0));
            return new ProcessResult(-1, outputBuilder.toString(), "Process timed out after " + DEFAULT_TIMEOUT_MINUTES + " minutes");
        }

        outputThread.join(2000);
        errorThread.join(2000);

        int exitCode = process.exitValue();
        return new ProcessResult(exitCode, outputBuilder.toString(), errorBuilder.toString());
    }
}
