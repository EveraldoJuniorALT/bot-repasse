package com.bot_repasse.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.whatsapp")
public record WhatsAppProperties(String destinationId) {
}
