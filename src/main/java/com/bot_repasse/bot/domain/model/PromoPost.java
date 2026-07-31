package com.bot_repasse.bot.domain.model;

public record PromoPost(String id, String text, byte[] mediaBytes, String mimeType) {
    /**
     * Construtor compacto do Record.
     * Executado automaticamente sempre que um PromoPost é instanciado.
     * Implementa o padrão "Fail-Fast", garantindo a integridade do domínio.
     */
    public PromoPost {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O ID da postagem é obrigatório para garantir a idempotência.");
        }

        boolean hasText = text != null && !text.isBlank();
        boolean hasMedia = mediaBytes != null && mediaBytes.length > 0;

        if (!hasText && !hasMedia) {
            throw new IllegalArgumentException("Uma postagem de promoção não pode ser vazia. Deve conter texto ou mídia.");
        }
    }
}
