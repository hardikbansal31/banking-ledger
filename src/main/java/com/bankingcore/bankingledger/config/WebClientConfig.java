package com.bankingcore.bankingledger.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClientConfig — configures the WebClient used for external HTTP calls.
 *
 * WHY WebClient AND NOT RestTemplate?
 *   RestTemplate is synchronous and blocking — the calling thread waits
 *   for the HTTP response. Under load this exhausts the thread pool.
 *   WebClient is non-blocking and reactive — it releases the thread while
 *   waiting for the response. For external API calls (exchange rates) that
 *   may take 200-500ms, this is the correct choice.
 *
 *   Note: we still call .block() in ExchangeRateService because the rest
 *   of our codebase is synchronous (JPA, @Transactional). A fully reactive
 *   stack would use Mono/Flux throughout. .block() is acceptable here
 *   because the result is cached in Redis — we only hit the API once per hour.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient exchangeRateWebClient(ExchangeRateProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("WebClient → {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("WebClient ← HTTP {}", response.statusCode());
            return Mono.just(response);
        });
    }
}