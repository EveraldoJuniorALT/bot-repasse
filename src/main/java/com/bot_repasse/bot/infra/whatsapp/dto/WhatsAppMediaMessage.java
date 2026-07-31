package com.bot_repasse.bot.infra.whatsapp.dto;

public record WhatsAppMediaMessage(String number, String mediatype, String mimetype, String caption, String media) {
}
