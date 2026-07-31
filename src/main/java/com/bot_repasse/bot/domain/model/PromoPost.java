package com.bot_repasse.bot.domain.model;

public record PromoPost(
        String id,
        String text,
        byte[] mediaBytes,
        String mimeType) {
}
