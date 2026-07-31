package com.bot_repasse.bot.infra.cache;

import com.bot_repasse.bot.domain.port.IdempotencyCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CaffeineIdempotencyCache implements IdempotencyCache {

    // Cria um cache seguro para concorrência (Thread-Safe)
    // - Expira registros após 24h
    // - Limita a 10.000 IDs no máximo para proteger a memória da máquina
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    @Override
    public boolean checkAndCache(String postId) {
        // A operação 'putIfAbsent' tenta inserir o valor no mapa.
        // Se a chave não existia, ela insere e retorna 'null'.
        // Se a chave já existia, ela não altera nada e retorna o valor antigo (Boolean.TRUE).
        Boolean existing = cache.asMap().putIfAbsent(postId, Boolean.TRUE);

        // Retorna TRUE se a postagem é nova.
        return existing == null;
    }
}
