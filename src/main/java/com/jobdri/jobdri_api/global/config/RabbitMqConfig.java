package com.jobdri.jobdri_api.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange workerExchange(
            @Value("${app.worker.exchange:jobdri.worker.exchange}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue jobPostingIngestQueue(
            @Value("${app.worker.job-posting.queue:jobdri.job-posting.ingest}") String queueName,
            @Value("${app.worker.job-posting.dlq:jobdri.job-posting.ingest.dlq}") String deadLetterQueueName
    ) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", deadLetterQueueName)
                .build();
    }

    @Bean
    public Queue jobPostingIngestDeadLetterQueue(
            @Value("${app.worker.job-posting.dlq:jobdri.job-posting.ingest.dlq}") String deadLetterQueueName
    ) {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding jobPostingIngestBinding(
            @Qualifier("jobPostingIngestQueue") Queue jobPostingIngestQueue,
            DirectExchange workerExchange,
            @Value("${app.worker.job-posting.routing-key:job-posting.ingest}") String routingKey
    ) {
        return BindingBuilder.bind(jobPostingIngestQueue).to(workerExchange).with(routingKey);
    }

    @Bean
    public Queue analysisQueue(
            @Value("${app.worker.analysis.queue:jobdri.analysis.execute}") String queueName,
            @Value("${app.worker.analysis.dlq:jobdri.analysis.execute.dlq}") String deadLetterQueueName
    ) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", deadLetterQueueName)
                .build();
    }

    @Bean
    public Queue analysisDeadLetterQueue(
            @Value("${app.worker.analysis.dlq:jobdri.analysis.execute.dlq}") String deadLetterQueueName
    ) {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding analysisBinding(
            @Qualifier("analysisQueue") Queue analysisQueue,
            DirectExchange workerExchange,
            @Value("${app.worker.analysis.routing-key:analysis.execute}") String routingKey
    ) {
        return BindingBuilder.bind(analysisQueue).to(workerExchange).with(routingKey);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
