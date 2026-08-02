package com.bot_repasse.bot.config;

import com.bot_repasse.bot.infra.telegram.TelegramBotListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@Profile("!test")
public class TelegramBotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotListener botListener) {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(botListener);
            System.out.println("🤖 Antena do Telegram ligada! Bot registrado com sucesso.");
            return api;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao registrar o bot no Telegram", e);
        }
    }
}
