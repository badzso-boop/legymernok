package com.legymernok.backend.service.circuit;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction over the arduino-cli process invocation.
 * Extracted to allow unit testing of ArduinoCompilerService without spawning real processes.
 */
public interface ArduinoCliRunner {

    Result run(String fqbn, Path sketchDir, Path outputDir) throws IOException, InterruptedException;

    record Result(boolean success, String output) {}
}
