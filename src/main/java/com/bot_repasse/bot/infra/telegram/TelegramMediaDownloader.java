package com.bot_repasse.bot.infra.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class TelegramMediaDownloader {

    /*
     * Tempo máximo de cada tentativa individual.
     */
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(25);

    /*
     * Limite total considerando:
     *
     * - tentativa inicial;
     * - duas novas tentativas;
     * - intervalos entre tentativas.
     */
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(90);

    private final WebClient webClient;

    public TelegramMediaDownloader(WebClient webClient) {
        this.webClient = webClient;
    }

    public byte[] downloadFileToMemory(String botToken, String filePath) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Token do Telegram não informado.");
        }

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("FilePath do Telegram não informado.");
        }

        String url = String.format("https://api.telegram.org/file/bot%s/%s", botToken, filePath);

        long startedAt = System.nanoTime();

        try {
            byte[] bytes = webClient.get()
                    .uri(url)
                    .accept(MediaType.IMAGE_JPEG, MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    /*
                     * Converte respostas HTTP de erro em exceções.
                     */.onStatus(HttpStatusCode::isError, ClientResponse::createException)
                    .bodyToMono(byte[].class)
                    /*
                     * Impede uma tentativa individual de ficar
                     * aguardando indefinidamente.
                     */.timeout(ATTEMPT_TIMEOUT)
                    /*
                     * Repete somente erros temporários:
                     *
                     * - timeout;
                     * - conexão;
                     * - resposta HTTP 5xx.
                     */.retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(5))
                            .jitter(0.25).filter(this::isRetryable)
                            .doBeforeRetry(signal -> log.warn("[TELEGRAM] Falha temporária no download. " + "Nova tentativa {}/2. causa={}", signal.totalRetries() + 1, signal.failure().getClass().getSimpleName())))
                    /*
                     * Proteção adicional para a operação inteira.
                     */.block(TOTAL_TIMEOUT);

            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("O Telegram retornou um arquivo vazio.");
            }

            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            log.info("[TELEGRAM] Imagem baixada com sucesso. bytes={}, duração={}ms", bytes.length, elapsedMs);

            return bytes;

        } catch (Exception exception) {
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            log.error("[TELEGRAM] Download da imagem falhou após {}ms. filePath={}, causa={}", elapsedMs, filePath, exception.getMessage(), exception);

            throw new IllegalStateException("Não foi possível baixar a imagem do Telegram.", exception);
        }
    }

    private boolean isRetryable(Throwable error) {
        if (error instanceof TimeoutException) {
            return true;
        }

        if (error instanceof WebClientRequestException) {
            return true;
        }

        if (error instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        Throwable cause = error.getCause();

        if (cause != null && cause != error) {
            return isRetryable(cause);
        }
        return false;
    }
}