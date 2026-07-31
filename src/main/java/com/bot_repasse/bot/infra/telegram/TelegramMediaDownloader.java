package com.bot_repasse.bot.infra.telegram;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TelegramMediaDownloader {

    private final WebClient webClient;

    public TelegramMediaDownloader(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Faz o download direto de um arquivo da API do Telegram para a memória RAM.
     */
    public byte[] downloadFileToMemory(String botToken, String filePath) {
        String url = String.format("https://api.telegram.org/file/bot%s/%s", botToken, filePath);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .block(); // Bloqueante apenas para a thread que está processando esta mensagem
    }
}
