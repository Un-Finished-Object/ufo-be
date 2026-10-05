package com.ufo.ufo.support.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class LogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private final Logger logger;
    private final List<ILoggingEvent> events = new ArrayList<>();

    public LogCapture(Class<?> source) {
        this(source.getName());
    }

    public LogCapture(String source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        start();
        logger.addAppender(this);
    }

    @Override
    protected synchronized void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        events.add(event);
    }

    public synchronized List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }
}
