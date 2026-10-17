package com.schoolsphere.notifications.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider interfaces for external notification channels (spec 11.7); SMS can back Africa's Talking. */
public final class Providers {

    private Providers() {
    }

    public interface EmailProvider {

        void send(String to, String subject, String body);
    }

    public interface SmsProvider {

        void send(String phoneNumber, String message);
    }

    @Configuration
    public static class DevProviders {

        private static final Logger log = LoggerFactory.getLogger(DevProviders.class);

        @Bean
        @ConditionalOnMissingBean(EmailProvider.class)
        public EmailProvider loggingEmailProvider() {
            return (to, subject, body) -> log.info("email.channel to={} subject={}", to, subject);
        }

        @Bean
        @ConditionalOnMissingBean(SmsProvider.class)
        public SmsProvider loggingSmsProvider() {
            return (phone, message) -> log.info("sms.channel to={} message={}", phone, message);
        }
    }
}
