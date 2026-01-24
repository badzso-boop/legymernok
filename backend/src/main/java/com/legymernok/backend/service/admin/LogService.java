package com.legymernok.backend.service.admin;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogService {

    private final Path LOG_PATH = Paths.get("logs/application.log");

    public List<String> getLatestLogs(int limit) {
        if (!Files.exists(LOG_PATH)) {
            return Collections.singletonList("Log file not found at: " + LOG_PATH.toAbsolutePath());
        }

        try (Stream<String> lines = Files.lines(LOG_PATH)) {
            // Ez nem a leghatékonyabb óriási fájloknál, de 10MB-ig bőven jó.
            // Hatékonyabb megoldás a RandomAccessFile lenne, ha nagyon nagy a fájl.
            List<String> allLines = lines.collect(Collectors.toList());

            int startIndex = Math.max(0, allLines.size() - limit);
            List<String> lastLines = allLines.subList(startIndex, allLines.size());

            return lastLines;
        } catch (IOException e) {
            return Collections.singletonList("Error reading log file: " + e.getMessage());
        }
    }
}