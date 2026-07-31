package com.bot_repasse.bot.infra.telegram;

import com.bot_repasse.bot.application.service.PromoPostOrchestrator;
import com.bot_repasse.bot.config.TelegramProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Comparator;

@Slf4j
@Component
public class TelegramBotListener extends TelegramLongPollingBot {

    private final TelegramProperties properties;
    private final PromoPostOrchestrator orchestrator;
    private final TelegramMediaDownloader mediaDownloader;

    public TelegramBotListener(TelegramProperties properties,
                               PromoPostOrchestrator orchestrator,
                               TelegramMediaDownloader mediaDownloader) {
        super(properties.token());
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.mediaDownloader = mediaDownloader;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = extractMessageFromUpdate(update);
        if (message == null) return;

        // Valida se a mensagem veio do canal que configuramos no .env
        String chatId = String.valueOf(message.getChatId());
        if (!chatId.equals(properties.channelId())) {
            return;
        }

        log.info("[TELEGRAM] Nova postagem interceptada. ID: {}", message.getMessageId());
        processPost(message);
    }

    private void processPost(Message message) {
        String messageId = String.valueOf(message.getMessageId());
        String text = extractText(message);
        byte[] mediaBytes = null;
        String mimeType = null;

        // Verifica se há foto anexa
        if (message.hasPhoto()) {
            try {
                // O Telegram envia um array com várias resoluções da mesma foto.
                // Usamos Streams para pegar a de maior tamanho (maior qualidade).
                PhotoSize largestPhoto = message.getPhoto().stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElseThrow(() -> new IllegalStateException("Array de fotos vazio."));

                // Pede ao Telegram o FilePath (endereço de download) da foto
                org.telegram.telegrambots.meta.api.objects.File file = execute(new GetFile(largestPhoto.getFileId()));

                log.info("[TELEGRAM] Baixando imagem de {} bytes...", largestPhoto.getFileSize());
                mediaBytes = mediaDownloader.downloadFileToMemory(properties.token(), file.getFilePath());
                mimeType = "image/jpeg"; // O Telegram padroniza fotos como JPEG

            } catch (TelegramApiException e) {
                log.error("[TELEGRAM] Erro na API ao buscar informações do arquivo: {}", e.getMessage());
            } catch (Exception e) {
                log.error("[TELEGRAM] Erro durante o download do arquivo: {}", e.getMessage());
            }
        }

        try {
            // Cria o objeto de domínio (Fail-Fast: vai dar erro se for vazio)
            PromoPost post = new PromoPost(messageId, text, mediaBytes, mimeType);

            // Entrega para o nosso cérebro/orquestrador!
            orchestrator.processNewPost(post);

        } catch (IllegalArgumentException e) {
            log.warn("[TELEGRAM] Postagem ignorada pela regra de negócio: {}", e.getMessage());
        }
    }

    private Message extractMessageFromUpdate(Update update) {
        if (update.hasChannelPost()) return update.getChannelPost();
        if (update.hasMessage()) return update.getMessage();
        return null;
    }

    private String extractText(Message message) {
        // Se a foto tem legenda, o texto vem no Caption. Senão, vem no Text.
        if (message.getCaption() != null && !message.getCaption().isBlank()) return message.getCaption();
        if (message.getText() != null && !message.getText().isBlank()) return message.getText();
        return null;
    }

    @Override
    public String getBotUsername() {
        return "GarimpoDeOfertas";
    }
}
