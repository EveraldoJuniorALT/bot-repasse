package com.bot_repasse.bot.infra.whatsapp.dto;

import com.bot_repasse.bot.application.service.TextSplitter;
import com.bot_repasse.bot.config.WhatsAppProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import com.bot_repasse.bot.domain.port.WhatsAppPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeileyWhatsAppPublisher implements WhatsAppPublisher {

    private static final int MAX_CAPTION_LENGTH = 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;
    private final WhatsAppProperties properties;

    @Override
    public void publish(PromoPost post) {
        boolean hasMedia = post.mediaBytes() != null
                && post.mediaBytes().length > 0;

        List<String> textParts = TextSplitter.split(
                post.text(),
                MAX_CAPTION_LENGTH
        );

        log.info(
                "[WHATSAPP] Preparando postagem. postId={}, possuiMidia={}, bytes={}, mimeType={}",
                post.id(),
                hasMedia,
                hasMedia ? post.mediaBytes().length : 0,
                post.mimeType()
        );

        if (hasMedia) {
            enviarMidiaComLegenda(post, textParts);
        } else {
            enviarApenasTexto(textParts);
        }
    }

    private void enviarMidiaComLegenda(
            PromoPost post,
            List<String> textParts
    ) {
        String caption = textParts.isEmpty()
                ? ""
                : textParts.getFirst();

        String mimeType = normalizarMimeType(post.mimeType());
        String fileName = criarNomeArquivo(post.id(), mimeType);

        /*
         * ByteArrayResource representa os mesmos bytes baixados do Telegram.
         *
         * É essencial sobrescrever getFilename(), pois um arquivo multipart
         * sem nome pode ser interpretado incorretamente pela API.
         */
        ByteArrayResource fileResource =
                new ByteArrayResource(post.mediaBytes()) {
                    @Override
                    public String getFilename() {
                        return fileName;
                    }
                };

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();

        multipart.part(
                "number",
                properties.destinationId()
        );

        multipart.part(
                "mediatype",
                "image"
        );

        multipart.part(
                "mimetype",
                mimeType
        );

        multipart.part(
                "caption",
                caption
        );

        multipart.part(
                "fileName",
                fileName
        );

        /*
         * Este é o campo que funcionou no curl.
         *
         * Não utilizar "media" e não transformar o arquivo em Base64.
         */
        multipart.part("file", fileResource)
                .filename(fileName)
                .contentType(MediaType.parseMediaType(mimeType));

        String url = construirUrl("/message/sendMedia/");

        log.info(
                "[WHATSAPP] Enviando mídia multipart. postId={}, destino={}, arquivo={}, mimeType={}, bytes={}, legenda={}",
                post.id(),
                properties.destinationId(),
                fileName,
                mimeType,
                post.mediaBytes().length,
                caption.length()
        );

        try {
            String response = webClient.post()
                    .uri(url)
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(
                            BodyInserters.fromMultipartData(
                                    multipart.build()
                            )
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            log.info(
                    "[WHATSAPP] Evolution aceitou a mídia. postId={}, resposta={}",
                    post.id(),
                    resumirResposta(response)
            );

        } catch (WebClientResponseException exception) {
            log.error(
                    "[WHATSAPP] Evolution rejeitou a mídia. postId={}, status={}, resposta={}",
                    post.id(),
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );

            throw new IllegalStateException(
                    "Falha no envio da mídia para o WhatsApp",
                    exception
            );

        } catch (Exception exception) {
            log.error(
                    "[WHATSAPP] Erro interno no envio da mídia. postId={}, erro={}",
                    post.id(),
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Falha no envio da mídia para o WhatsApp",
                    exception
            );
        }

        /*
         * A primeira parte foi enviada como legenda.
         * As partes restantes são publicadas como texto.
         */
        for (int index = 1; index < textParts.size(); index++) {
            enviarMensagemTexto(
                    post.id(),
                    textParts.get(index),
                    index + 1,
                    textParts.size()
            );
        }
    }

    private void enviarApenasTexto(List<String> textParts) {
        if (textParts.isEmpty()) {
            log.warn(
                    "[WHATSAPP] Post sem texto e sem mídia. Nada para enviar."
            );
            return;
        }

        for (int index = 0; index < textParts.size(); index++) {
            enviarMensagemTexto(
                    null,
                    textParts.get(index),
                    index + 1,
                    textParts.size()
            );
        }
    }

    private void enviarMensagemTexto(
            String postId,
            String text,
            int currentPart,
            int totalParts
    ) {
        WhatsAppTextMessage payload = new WhatsAppTextMessage(
                properties.destinationId(),
                text
        );

        String url = construirUrl("/message/sendText/");

        log.info(
                "[WHATSAPP] Enviando texto. postId={}, parte={}/{}, caracteres={}",
                postId,
                currentPart,
                totalParts,
                text.length()
        );

        try {
            String response = webClient.post()
                    .uri(url)
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            log.info(
                    "[WHATSAPP] Texto aceito pela Evolution. postId={}, parte={}/{}, resposta={}",
                    postId,
                    currentPart,
                    totalParts,
                    resumirResposta(response)
            );

        } catch (WebClientResponseException exception) {
            log.error(
                    "[WHATSAPP] Evolution rejeitou o texto. postId={}, status={}, resposta={}",
                    postId,
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );

            throw new IllegalStateException(
                    "Falha no envio do texto para o WhatsApp",
                    exception
            );

        } catch (Exception exception) {
            log.error(
                    "[WHATSAPP] Erro interno no envio do texto. postId={}, erro={}",
                    postId,
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Falha no envio do texto para o WhatsApp",
                    exception
            );
        }
    }

    private String normalizarMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.IMAGE_JPEG_VALUE;
        }

        String normalized = mimeType
                .trim()
                .toLowerCase();

        if (!normalized.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Tipo de mídia não suportado: " + mimeType
            );
        }

        return normalized;
    }

    private String criarNomeArquivo(
            String postId,
            String mimeType
    ) {
        String extension = switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/jpeg", "image/jpg" -> ".jpg";
            default -> ".jpg";
        };

        String safeId = postId == null
                ? "sem-id"
                : postId.replaceAll("[^a-zA-Z0-9_-]", "_");

        return "telegram-" + safeId + extension;
    }

    private String construirUrl(String endpoint) {
        String baseUrl = properties.apiUrl();

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        return baseUrl
                + endpoint
                + properties.instanceName();
    }

    private String resumirResposta(String response) {
        if (response == null || response.isBlank()) {
            return "<resposta vazia>";
        }

        int maxLength = 500;

        if (response.length() <= maxLength) {
            return response;
        }

        return response.substring(0, maxLength)
                + "...[resposta truncada]";
    }
}