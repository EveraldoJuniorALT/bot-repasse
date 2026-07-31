package com.bot_repasse.bot.domain.port;

import com.bot_repasse.bot.domain.model.PromoPost;


public interface WhatsAppPublisher {
    /**
     * Envia o post formatado para o canal do WhatsApp.
     *
     * @param post Objeto de domínio contendo as informações da promoção.
     * @throws RuntimeException em caso de falhas de comunicação ou validação da API de destino.
     */
    void publish(PromoPost post);
}
