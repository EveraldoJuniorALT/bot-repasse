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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeileyWhatsAppPublisher implements WhatsAppPublisher {

    /*
     * O WhatsApp aceita até 1024 caracteres na legenda de uma mídia.
     * O restante é enviado depois como mensagem de texto.
     */
    private static final int MAX_CAPTION_LENGTH = 1024;

    /*
     * Tempo máximo para a Evolution responder.
     * Uploads de mídia podem demorar mais que mensagens de texto.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;
    private final WhatsAppProperties properties;

    @Override
    public void publish(PromoPost post) {
        log.info(
                "[WHATSAPP] Preparando postagem. postId={}, possuiMidia={}, tamanhoTexto={}",
                post.id(),
                hasMedia(post),
                post.text() == null ? 0 : post.text().length()
        );

        List<String> textParts = TextSplitter.split(
                post.text(),
                MAX_CAPTION_LENGTH
        );

        if (hasMedia(post)) {
            enviarMidiaComLegenda(post, textParts);
            return;
        }

        enviarApenasTexto(textParts);
    }

    /**
     * Envia a imagem usando multipart/form-data.
     * <p>
     * Esse formato replica exatamente o curl que funcionou:
     * <p>
     * number
     * mediatype
     * mimetype
     * caption
     * fileName
     * file
     */
    private void enviarMidiaComLegenda(
            PromoPost post,
            List<String> textParts
    ) {
        String caption = textParts.isEmpty()
                ? ""
                : textParts.getFirst();

        String mimeType = normalizarMimeType(post.mimeType());
        String fileName = criarNomeArquivo(post.id(), mimeType);

        ByteArrayResource fileResource =
                criarArquivoMultipart(post.mediaBytes(), fileName);

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
         * Atenção: o nome correto do campo é "file".
         * Usar "media" gera "Unexpected field" na 2.4.0-rc2.
         */
        multipart.part("file", fileResource)
                .filename(fileName)
                .contentType(MediaType.parseMediaType(mimeType));

        log.info(
                "[WHATSAPP] Enviando mídia multipart. postId={}, destino={}, arquivo={}, mimeType={}, bytes={}, captionLength={}",
                post.id(),
                properties.destinationId(),
                fileName,
                mimeType,
                post.mediaBytes().length,
                caption.length()
        );

        String response = enviarMultipart(
                "/message/sendMedia/",
                multipart
        );

        log.info(
                "[WHATSAPP] Mídia aceita pela Evolution API. postId={}, resposta={}",
                post.id(),
                resumirResposta(response)
        );

        /*
         * A primeira parte foi enviada como legenda.
         * Caso o texto ultrapasse 1024 caracteres, enviamos o restante
         * como mensagens comuns.
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

    /**
     * Envia posts que possuem somente texto.
     */
    private void enviarApenasTexto(List<String> textParts) {
        if (textParts.isEmpty()) {
            log.warn(
                    "[WHATSAPP] Post sem texto e sem blocos válidos para envio."
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

        log.info(
                "[WHATSAPP] Enviando texto. postId={}, parte={}/{}, caracteres={}",
                postId,
                currentPart,
                totalParts,
                text.length()
        );

        String response = enviarJson(
                "/message/sendText/",
                payload
        );

        log.info(
                "[WHATSAPP] Texto aceito pela Evolution API. postId={}, parte={}/{}, resposta={}",
                postId,
                currentPart,
                totalParts,
                resumirResposta(response)
        );
    }

    /**
     * Faz chamadas JSON, utilizadas por mensagens de texto.
     */
    private String enviarJson(
            String endpoint,
            Object payload
    ) {
        String url = construirUrl(endpoint);

        try {
            return webClient.post()
                    .uri(url)
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .exchangeToMono(response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(responseBody -> {
                                        if (response.statusCode()
                                                .is2xxSuccessful()) {

                                            return Mono.just(responseBody);
                                        }

                                        return Mono.error(
                                                criarErroEvolution(
                                                        url,
                                                        response.statusCode()
                                                                .value(),
                                                        responseBody
                                                )
                                        );
                                    })
                    )
                    .timeout(REQUEST_TIMEOUT)
                    .block();

        } catch (Exception exception) {
            tratarErro(endpoint, exception);
            throw new IllegalStateException(
                    "Falha ao enviar mensagem de texto para o WhatsApp",
                    exception
            );
        }
    }

    /**
     * Faz chamadas multipart, utilizadas no envio de fotos.
     */
    private String enviarMultipart(
            String endpoint,
            MultipartBodyBuilder multipart
    ) {
        String url = construirUrl(endpoint);

        try {
            return webClient.post()
                    .uri(url)
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(
                            BodyInserters.fromMultipartData(
                                    multipart.build()
                            )
                    )
                    .exchangeToMono(response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(responseBody -> {
                                        if (response.statusCode()
                                                .is2xxSuccessful()) {

                                            return Mono.just(responseBody);
                                        }

                                        return Mono.error(
                                                criarErroEvolution(
                                                        url,
                                                        response.statusCode()
                                                                .value(),
                                                        responseBody
                                                )
                                        );
                                    })
                    )
                    .timeout(REQUEST_TIMEOUT)
                    .block();

        } catch (Exception exception) {
            tratarErro(endpoint, exception);
            throw new IllegalStateException(
                    "Falha ao enviar mídia para o WhatsApp",
                    exception
            );
        }
    }

    private ByteArrayResource criarArquivoMultipart(
            byte[] bytes,
            String fileName
    ) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private boolean hasMedia(PromoPost post) {
        return post.mediaBytes() != null
                && post.mediaBytes().length > 0;
    }

    private String normalizarMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.IMAGE_JPEG_VALUE;
        }

        /*
         * A implementação atual trata apenas imagens.
         * Impede que outro tipo de mídia seja enviado como image.
         */
        if (!mimeType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Tipo de mídia não suportado: " + mimeType
            );
        }

        return mimeType.toLowerCase();
    }

    private String criarNomeArquivo(
            String postId,
            String mimeType
    ) {
        String extension = switch (mimeType) {
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/jpg", MediaType.IMAGE_JPEG_VALUE -> ".jpg";
            default -> ".jpg";
        };

        String safePostId = postId.replaceAll(
                "[^a-zA-Z0-9_-]",
                "_"
        );

        return "telegram-" + safePostId + extension;
    }

    private String construirUrl(String endpoint) {
        String apiUrl = properties.apiUrl();

        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(
                    0,
                    apiUrl.length() - 1
            );
        }

        return apiUrl
                + endpoint
                + properties.instanceName();
    }

    private IllegalStateException criarErroEvolution(
            String url,
            int statusCode,
            String responseBody
    ) {
        return new IllegalStateException(
                "Evolution API retornou HTTP "
                        + statusCode
                        + ". url="
                        + url
                        + ", resposta="
                        + responseBody
        );
    }

    private void tratarErro(
            String endpoint,
            Exception exception
    ) {
        log.error(
                "[WHATSAPP] Falha na comunicação com a Evolution API. endpoint={}, instancia={}, destino={}, erro={}",
                endpoint,
                properties.instanceName(),
                properties.destinationId(),
                exception.getMessage(),
                exception
        );
    }

    /**
     * Evita colocar respostas gigantes nos logs.
     */
    private String resumirResposta(String response) {
        if (response == null || response.isBlank()) {
            return "<resposta vazia>";
        }

        int maxLength = 600;

        if (response.length() <= maxLength) {
            return response;
        }

        return response.substring(0, maxLength)
                + "...[resposta truncada]";
    }
}