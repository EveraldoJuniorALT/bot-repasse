package com.bot_repasse.bot.infra.whatsapp.dto;

import com.bot_repasse.bot.application.service.TextSplitter;
import com.bot_repasse.bot.config.WhatsAppProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import com.bot_repasse.bot.domain.port.WhatsAppPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeileyWhatsAppPublisher implements WhatsAppPublisher {

    private final WebClient webClient;
    private final WhatsAppProperties properties;

    // Limite oficial da legenda de mídia no WhatsApp
    private static final int MAX_CAPTION_LENGTH = 1024;

    @Override
    public void publish(PromoPost post) {
        log.info("[WHATSAPP] Preparando postagem ID: {}", post.id());

        boolean hasMedia = post.mediaBytes() != null && post.mediaBytes().length > 0;

        // Fatiamos o texto caso seja maior que o limite (usando nosso utilitário da Etapa 9)
        List<String> textParts = TextSplitter.split(post.text(), MAX_CAPTION_LENGTH);

        if (hasMedia) {
            enviarMidiaComLegenda(post, textParts);
        } else {
            enviarApenasTexto(textParts);
        }
    }

    private void enviarMidiaComLegenda(PromoPost post, List<String> textParts) {
        // A primeira parte do texto fatido vai como legenda da foto
        String caption = textParts.isEmpty() ? "" : textParts.getFirst();
        String base64Media = Base64.getEncoder().encodeToString(post.mediaBytes());

        WhatsAppMediaMessage mediaPayload = new WhatsAppMediaMessage(
                properties.destinationId(),
                "image",
                post.mimeType() != null ? post.mimeType() : "image/jpeg",
                caption,
                base64Media
        );

        log.info("[WHATSAPP] Enviando mídia em Base64 para o canal...");
        enviarRequisicao("/message/sendMedia/", mediaPayload);

        // Se o texto era gigante e sobrou, enviamos o restante como mensagens de texto normais
        for (int i = 1; i < textParts.size(); i++) {
            enviarMensagemTexto(textParts.get(i));
        }
    }

    private void enviarApenasTexto(List<String> textParts) {
        for (String part : textParts) {
            enviarMensagemTexto(part);
        }
    }

    private void enviarMensagemTexto(String texto) {
        log.info("[WHATSAPP] Enviando bloco de texto para o canal...");
        WhatsAppTextMessage textPayload = new WhatsAppTextMessage(
                properties.destinationId(),
                texto
        );
        enviarRequisicao("/message/sendText/", textPayload);
    }

    /**
     * Centraliza a chamada HTTP para a API do Baileys.
     */
    private void enviarRequisicao(String endpoint, Object payload) {
        String url = properties.apiUrl() + endpoint + properties.instanceName();
        System.out.println("[WHATSAPP] Enviando requisição para: " + url + " com payload: " + payload);

        try {
            webClient.post()
                    .uri(url)
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (WebClientResponseException e) {

            log.error("[WHATSAPP] ERRO DA API EVOLUTION (Causa Raiz): {}", e.getResponseBodyAsString());
            throw new RuntimeException("Falha no envio para o WhatsApp", e);

        } catch (Exception e) {
            log.error("[WHATSAPP] Erro interno ou de rede: {}", e.getMessage());
            throw new RuntimeException("Falha no envio para o WhatsApp", e);
        }
    }
}
