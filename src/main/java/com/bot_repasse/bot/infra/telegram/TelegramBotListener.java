package com.bot_repasse.bot.infra.telegram;

import com.bot_repasse.bot.application.service.PromoPostOrchestrator;
import com.bot_repasse.bot.config.TelegramProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final long startTime;

    @Autowired
    public TelegramBotListener(TelegramProperties properties, PromoPostOrchestrator orchestrator, TelegramMediaDownloader mediaDownloader) {
        super(properties.token());
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.mediaDownloader = mediaDownloader;
        this.startTime = System.currentTimeMillis() / 1000L;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().getDate() < startTime) return;

            Message message = update.getMessage();
            log.info("[TELEGRAM] Nova postagem interceptada. ID: {}", message.getMessageId());
            processPost(message);
        }
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
                PhotoSize largestPhoto = message.getPhoto().stream().max(Comparator.comparingLong((PhotoSize photo) -> {
                    long width = photo.getWidth() == null ? 0L : photo.getWidth();

                    long height = photo.getHeight() == null ? 0L : photo.getHeight();

                    return width * height;
                }).thenComparingLong((PhotoSize photo) -> photo.getFileSize() == null ? 0L : photo.getFileSize()))
                        .orElseThrow(() -> new IllegalStateException("Array de fotos vazio."));

                log.info("[TELEGRAM] Foto selecionada. resolução={}x{}, tamanhoInformado={} bytes, fileId={}", largestPhoto.getWidth(), largestPhoto.getHeight(), largestPhoto.getFileSize(), largestPhoto.getFileId());

                // Pede ao Telegram o FilePath (endereço de download) da foto
                org.telegram.telegrambots.meta.api.objects.File file = execute(new GetFile(largestPhoto.getFileId()));

                log.info("[TELEGRAM] Baixando imagem de {} bytes...", largestPhoto.getFileSize());
                mediaBytes = mediaDownloader.downloadFileToMemory(properties.token(), file.getFilePath());

                if (mediaBytes == null || mediaBytes.length == 0) {
                    throw new IllegalStateException(
                            "O Telegram retornou uma imagem vazia."
                    );
                }

                log.info(
                        "[TELEGRAM] Download concluído. tamanhoInformado={} bytes, tamanhoRecebido={} bytes, filePath={}",
                        largestPhoto.getFileSize(),
                        mediaBytes.length,
                        file.getFilePath()
                );

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
