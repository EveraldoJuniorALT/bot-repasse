package com.bot_repasse.bot.domain.port;

public interface IdempotencyCache {
    /**
     * Verifica se o post já foi processado. Se não, adiciona ao cache.
     * @return true se o post for inédito, false se já foi processado.
     */
    boolean checkAndCache(String postId);
}
