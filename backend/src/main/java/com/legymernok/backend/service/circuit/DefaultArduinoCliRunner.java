package com.legymernok.backend.service.circuit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Default ArduinoCliRunner — runs a local arduino-cli process.
 * Active when the "production" profile is NOT active (dev, test, staging).
 */
@Component
@Profile("!production")
@Slf4j
public class DefaultArduinoCliRunner implements ArduinoCliRunner {

    @Value("${arduino.cli.path:arduino-cli}")
    private String arduinoCliPath;

    @Value("${arduino.cli.compile.timeout-seconds:120}")
    private int compileTimeoutSeconds;

    @Override
    public Result run(String fqbn, Path sketchDir, Path outputDir)
            throws IOException, InterruptedException {

        List<String> command = List.of(
                arduinoCliPath, "compile",
                "--fqbn", fqbn,
                "--output-dir", outputDir.toAbsolutePath().toString(),
                sketchDir.toAbsolutePath().toString()
        );
        log.debug("Running: {}", String.join(" ", command));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(compileTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new Result(false, "Compilation timed out after " + compileTimeoutSeconds + " seconds.");
        }

        String output = new String(process.getInputStream().readAllBytes());
        return new Result(process.exitValue() == 0, output);
    }
}
