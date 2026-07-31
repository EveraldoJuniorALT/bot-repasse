package com.bot_repasse.bot.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromoPostTest {

    @Test
    @DisplayName("Deve criar postagem com sucesso quando houver ID e texto")
    void deveCriarPostagemComTexto() {
        PromoPost post = new PromoPost("123", "Promoção imperdível", null, null);

        assertEquals("123", post.id());
        assertEquals("Promoção imperdível", post.text());
        assertNull(post.mediaBytes());
    }

    @Test
    @DisplayName("Deve criar postagem com sucesso quando houver ID e mídia")
    void deveCriarPostagemComMidia() {
        PromoPost post = new PromoPost("124", null, new byte[]{1, 2, 3}, "image/jpeg");

        assertEquals("124", post.id());
        assertNotNull(post.mediaBytes());
    }

    @Test
    @DisplayName("Deve lançar exceção se ID for nulo")
    void deveLancarExcecaoSeIdNulo() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new PromoPost(null, "Texto legal", null, null));

        assertEquals("O ID da postagem é obrigatório para garantir a idempotência.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção se ID for vazio")
    void deveLancarExcecaoSeIdVazio() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new PromoPost("   ", "Texto legal", null, null));

        assertEquals("O ID da postagem é obrigatório para garantir a idempotência.", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar exceção se texto e mídia forem ausentes")
    void deveLancarExcecaoSeCorpoVazio() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new PromoPost("125", "", null, null));

        assertEquals("Uma postagem de promoção não pode ser vazia. Deve conter texto ou mídia.", exception.getMessage());
    }

}