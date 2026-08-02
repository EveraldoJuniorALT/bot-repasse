package com.bot_repasse.bot.application.service;

import com.bot_repasse.bot.domain.model.PromoPost;
import com.bot_repasse.bot.domain.port.IdempotencyCache;
import com.bot_repasse.bot.domain.port.PostOrchestrator;
import com.bot_repasse.bot.domain.port.WhatsAppPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromoPostOrchestrator implements PostOrchestrator {

    private final IdempotencyCache idempotencyCache;
    private final WhatsAppPublisher whatsAppPublisher;

    @Override
    public void processNewPost(PromoPost post) {
        log.info("ORQUESTRADOR Recebido novo post do Telegram. ID: {}", post.id());

        // 1. Verifica Idempotência
        if (!idempotencyCache.checkAndCache(post.id())) {
            log.info("ORQUESTRADOR Post ID: {} já processado anteriormente. Ignorando.", post.id());
            return;
        }

        // 2. Tenta repassar para o WhatsApp
        try {
            log.info("ORQUESTRADOR Iniciando publicação do Post ID: {} no WhatsApp...", post.id());

            whatsAppPublisher.publish(post);

            log.info("ORQUESTRADOR Post ID: {} aceito pela Evolution API para publicação no WhatsApp.", post.id());
        } catch (Exception exception) {
            log.error("ORQUESTRADOR Falha ao publicar o Post ID: {} no WhatsApp.", post.id(), exception);
        }
    }
}
