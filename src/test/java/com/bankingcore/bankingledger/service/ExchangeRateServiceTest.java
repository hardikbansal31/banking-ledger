package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.ExchangeRateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private WebClient exchangeRateWebClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private ExchangeRateProperties props;

    @Mock
    private RBucket<String> rBucket;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        // Only set up common mocks here if necessary, but typically done per test
    }

    // Guards against: Unnecessary external API calls when currencies are identical
    @Test
    void getRate_shouldReturnOne_whenCurrenciesAreTheSame() {
        BigDecimal result = exchangeRateService.getRate("USD", "USD");

        assertThat(result).isEqualByComparingTo(BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN));
        verifyNoInteractions(redissonClient, exchangeRateWebClient);
    }

    // Guards against: Exhausting third-party API quotas by ignoring the cache
    @Test
    void getRate_shouldReturnCachedRate_whenCacheHit() {
        doReturn(rBucket).when(redissonClient).getBucket(anyString());
        when(rBucket.get()).thenReturn("0.920000");

        BigDecimal result = exchangeRateService.getRate("USD", "EUR");

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.920000"));
        verifyNoInteractions(exchangeRateWebClient);
    }

    // Guards against: Failing to populate cache when a valid API response is received
    @Test
    @SuppressWarnings("unchecked")
    void getRate_shouldFetchFromApiAndCache_whenCacheMiss() {
        doReturn(rBucket).when(redissonClient).getBucket(anyString());
        when(rBucket.get()).thenReturn(null); // Cache miss
        
        when(props.getApiKey()).thenReturn("mock-api-key");
        when(props.getCacheTtlMinutes()).thenReturn(60);

        // Deep mock WebClient
        when(exchangeRateWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        Map<String, Object> mockResponse = Map.of(
            "result", "success",
            "conversion_rates", Map.of("EUR", 0.92)
        );
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(mockResponse));

        BigDecimal result = exchangeRateService.getRate("USD", "EUR");

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.920000"));
        // Assert that the fetched rate is stored in the cache
        verify(rBucket).set(eq("0.920000"), any(Duration.class));
    }

    // Guards against: Silently failing and returning null/stale rates if API goes down
    @Test
    @SuppressWarnings("unchecked")
    void getRate_shouldThrowException_whenApiFails() {
        doReturn(rBucket).when(redissonClient).getBucket(anyString());
        when(rBucket.get()).thenReturn(null); // Cache miss
        
        when(props.getApiKey()).thenReturn("mock-api-key");

        // Deep mock WebClient
        when(exchangeRateWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        Map<String, Object> mockResponse = Map.of(
            "result", "error",
            "error-type", "unsupported-code"
        );
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(mockResponse));

        assertThatThrownBy(() -> exchangeRateService.getRate("USD", "INVALID_COIN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exchange rate API error");
    }
}
