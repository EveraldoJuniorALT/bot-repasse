package com.bot_repasse.bot.domain.port;

import com.bot_repasse.bot.domain.model.PromoPost;

public interface PostOrchestrator {
    /**
     * Recebe um post da infraestrutura do Telegram e coordena o fluxo até o WhatsApp.
     */
    void processNewPost(PromoPost post);
}
