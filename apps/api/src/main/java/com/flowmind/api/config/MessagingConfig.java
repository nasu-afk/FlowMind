package com.flowmind.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {
    @Bean DirectExchange flowMindExchange(@Value("${flowmind.outbox.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean DirectExchange flowMindDeadLetterExchange(@Value("${flowmind.outbox.exchange}") String name) {
        return new DirectExchange(name + ".dlx", true, false);
    }

    @Bean Queue executionQueue(@Value("${flowmind.outbox.exchange}") String exchange) {
        return QueueBuilder.durable("flowmind.execution.queued")
            .deadLetterExchange(exchange + ".dlx").deadLetterRoutingKey("execution.queued.failed").build();
    }

    @Bean Queue executionDeadLetterQueue() {
        return QueueBuilder.durable("flowmind.execution.queued.dlq").build();
    }

    @Bean Binding executionBinding(Queue executionQueue, DirectExchange flowMindExchange) {
        return BindingBuilder.bind(executionQueue).to(flowMindExchange).with("execution.queued");
    }

    @Bean Binding executionResumeBinding(Queue executionQueue, DirectExchange flowMindExchange) {
        return BindingBuilder.bind(executionQueue).to(flowMindExchange).with("execution.resumed");
    }

    @Bean Binding executionDeadLetterBinding(Queue executionDeadLetterQueue, DirectExchange flowMindDeadLetterExchange) {
        return BindingBuilder.bind(executionDeadLetterQueue).to(flowMindDeadLetterExchange).with("execution.queued.failed");
    }
}

