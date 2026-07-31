package com.bot_repasse.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot.telegram")
public record TelegramProperties(String token, String channelId) {
}
