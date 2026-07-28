package com.jobdri.jobdri_api.global.config;

import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisQueueProperties;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingQueueProperties;
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
            JobPostingQueueProperties jobPostingQueueProperties
    ) {
        return QueueBuilder.durable(jobPostingQueueProperties.getQueue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", jobPostingQueueProperties.getDlq())
                .build();
    }

    @Bean
    public Queue jobPostingIngestDeadLetterQueue(
            JobPostingQueueProperties jobPostingQueueProperties
    ) {
        return QueueBuilder.durable(jobPostingQueueProperties.getDlq()).build();
    }

    @Bean
    public Binding jobPostingIngestBinding(
            @Qualifier("jobPostingIngestQueue") Queue jobPostingIngestQueue,
            DirectExchange workerExchange,
            JobPostingQueueProperties jobPostingQueueProperties
    ) {
        return BindingBuilder.bind(jobPostingIngestQueue).to(workerExchange).with(jobPostingQueueProperties.getRoutingKey());
    }

    @Bean
    public Queue analysisQueue(
            AnalysisQueueProperties analysisQueueProperties
    ) {
        return QueueBuilder.durable(analysisQueueProperties.getQueue())
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", analysisQueueProperties.getDlq())
                .build();
    }

    @Bean
    public Queue analysisDeadLetterQueue(
            AnalysisQueueProperties analysisQueueProperties
    ) {
        return QueueBuilder.durable(analysisQueueProperties.getDlq()).build();
    }

    @Bean
    public Binding analysisBinding(
            @Qualifier("analysisQueue") Queue analysisQueue,
            DirectExchange workerExchange,
            AnalysisQueueProperties analysisQueueProperties
    ) {
        return BindingBuilder.bind(analysisQueue).to(workerExchange).with(analysisQueueProperties.getRoutingKey());
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
