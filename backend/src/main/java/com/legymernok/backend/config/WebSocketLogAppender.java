package com.legymernok.backend.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import com.legymernok.backend.service.admin.WebSocketLogService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Important: This is not a @Component because it is added to the context manually
public class WebSocketLogAppender extends AppenderBase<ILoggingEvent> {

    private final WebSocketLogService webSocketLogService;
    private final PatternLayoutEncoder encoder;

    public WebSocketLogAppender(WebSocketLogService webSocketLogService, PatternLayoutEncoder encoder) {
        this.webSocketLogService = webSocketLogService;
        this.encoder = encoder;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (webSocketLogService == null) return;

        // Format the log according to the configured pattern
        byte[] byteArray = encoder.encode(eventObject);
        String formattedLog = new String(byteArray, StandardCharsets.UTF_8);

        // Send
        webSocketLogService.sendLog(formattedLog);
    }
}
