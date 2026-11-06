package com.ufo.ufo.support.fixture;

import com.ufo.ufo.domain.chat.application.ChatEventCodec;
import com.ufo.ufo.domain.chat.application.ChatEventOutboxDispatcher;
import com.ufo.ufo.domain.chat.application.ChatEventOutboxProcessor;
import com.ufo.ufo.domain.chat.application.ChatEventOutboxRecorder;
import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import({ChatEventCodec.class, ChatEventOutboxRecorder.class, ChatEventOutboxProcessor.class,
        ChatEventOutboxDispatcher.class})
public class ChatOutboxTestConfig {

    @Bean
    public Executor chatEventOutboxExecutor() {
        return Runnable::run;
    }
}
