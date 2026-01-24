package com.legymernok.backend.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import com.legymernok.backend.service.admin.WebSocketLogService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class LoggingConfig {

    private final WebSocketLogService webSocketLogService;

    @PostConstruct
    public void configureLogback() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Encoder beállítása (formázás)
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level --- [%thread] %logger{36} : %msg%n");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        // Appender létrehozása
        WebSocketLogAppender webSocketAppender = new WebSocketLogAppender(webSocketLogService, encoder);
        webSocketAppender.setContext(loggerContext);
        webSocketAppender.setName("WEBSOCKET");
        webSocketAppender.start();

        // Hozzáadás a ROOT loggerhez
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(webSocketAppender);
    }
}
