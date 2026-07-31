package com.bot_repasse.bot.domain.port;

import com.bot_repasse.bot.domain.model.PromoPost;

public interface WhatsAppPublisher {
    /**
     * Envia a publicação formatada para o WhatsApp.
     * Deve lançar exceção em caso de falha para que a aplicação trate o retry.
     */
    void publish(PromoPost post);
}
