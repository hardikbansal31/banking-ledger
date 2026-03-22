package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.ExchangeRateProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

/**
 * ExchangeRateService — fetches and caches currency exchange rates.
 *
 * CACHING STRATEGY:
 *   Cache key : "banking:exchange-rate:{fromCurrency}:{toCurrency}"
 *   Cache TTL : 60 minutes (configurable via app.exchange-rate.cache-ttl-minutes)
 *   Backend   : Redis via Redisson RBucket
 *
 *   On cache hit  → return cached rate immediately (no API call)
 *   On cache miss → call external API, store result, return rate
 *
 *   The free tier of exchangerate-api.com allows 1,500 requests/month.
 *   With 60-minute TTL and ~150 currency pairs, we stay well within limits.
 *
 * FALLBACK BEHAVIOUR:
 *   If the external API call fails (network error, rate limit, invalid key),
 *   the service throws a RuntimeException which causes the transfer to fail
 *   with a 503. This is intentional — we never silently use a stale rate
 *   for a financial transaction. Better to fail loudly than silently wrong.
 *
 * API RESPONSE SHAPE (exchangerate-api.com v6):
 * {
 *   "result": "success",
 *   "base_code": "USD",
 *   "conversion_rates": {
 *     "EUR": 0.92,
 *     "GBP": 0.79,
 *     "INR": 83.12,
 *     ...
 *   }
 * }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String CACHE_PREFIX = "banking:exchange-rate:";
    private static final String SAME_CURRENCY = "1.000000";

    private final WebClient             exchangeRateWebClient;
    private final RedissonClient        redissonClient;
    private final ExchangeRateProperties props;

    /**
     * Returns the exchange rate from {@code fromCurrency} to {@code toCurrency}.
     * Uses Redis cache — only calls the external API on cache miss.
     *
     * @return rate as BigDecimal with scale 6 (e.g. 0.920000 for USD→EUR)
     */
    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase();
        String to   = toCurrency.toUpperCase();

        // Same currency — rate is always exactly 1
        if (from.equals(to)) {
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN);
        }

        String cacheKey = CACHE_PREFIX + from + ":" + to;
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);

        // Cache hit
        String cached = bucket.get();
        if (cached != null) {
            log.debug("Exchange rate cache HIT: {} → {} = {}", from, to, cached);
            return new BigDecimal(cached);
        }

        // Cache miss — fetch from API
        log.info("Exchange rate cache MISS: {} → {} — calling external API", from, to);
        BigDecimal rate = fetchRateFromApi(from, to);

        // Store in cache with configured TTL
        bucket.set(rate.toPlainString(),
                Duration.ofMinutes(props.getCacheTtlMinutes()));
        log.info("Exchange rate cached: {} → {} = {} (TTL={}min)",
                from, to, rate, props.getCacheTtlMinutes());

        return rate;
    }

    /**
     * Converts {@code amount} from {@code fromCurrency} to {@code toCurrency}.
     * Applies the exchange rate and rounds to 4 decimal places.
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        BigDecimal rate = getRate(fromCurrency, toCurrency);
        BigDecimal converted = amount.multiply(rate).setScale(4, RoundingMode.HALF_EVEN);

        log.debug("Currency conversion: {} {} → {} {} (rate={})",
                amount, fromCurrency, converted, toCurrency, rate);

        return converted;
    }

    /**
     * Evicts a cached rate — useful for testing or manual cache refresh.
     */
    public void evictCache(String fromCurrency, String toCurrency) {
        String cacheKey = CACHE_PREFIX + fromCurrency.toUpperCase()
                + ":" + toCurrency.toUpperCase();
        redissonClient.getBucket(cacheKey).delete();
        log.info("Exchange rate cache evicted: {} → {}", fromCurrency, toCurrency);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private BigDecimal fetchRateFromApi(String fromCurrency, String toCurrency) {
        String url = "/" + props.getApiKey() + "/latest/" + fromCurrency;

        try {
            Map<String, Object> response = exchangeRateWebClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new RuntimeException(
                        "Exchange rate API returned null response for " + fromCurrency);
            }

            String result = (String) response.get("result");
            if (!"success".equals(result)) {
                String errorType = (String) response.getOrDefault("error-type", "unknown");
                throw new RuntimeException(
                        "Exchange rate API error: " + errorType
                                + " for " + fromCurrency + " → " + toCurrency);
            }

            Map<String, Object> rates = (Map<String, Object>) response.get("conversion_rates");
            if (rates == null || !rates.containsKey(toCurrency)) {
                throw new RuntimeException(
                        "Currency not found in exchange rate response: " + toCurrency);
            }

            Object rateObj = rates.get(toCurrency);
            BigDecimal rate;
            if (rateObj instanceof Number) {
                rate = BigDecimal.valueOf(((Number) rateObj).doubleValue())
                        .setScale(6, RoundingMode.HALF_EVEN);
            } else {
                rate = new BigDecimal(rateObj.toString())
                        .setScale(6, RoundingMode.HALF_EVEN);
            }

            log.info("Exchange rate fetched from API: {} → {} = {}", fromCurrency, toCurrency, rate);
            return rate;

        } catch (WebClientResponseException ex) {
            log.error("Exchange rate API HTTP error: {} {} — {}",
                    ex.getStatusCode(), fromCurrency, ex.getMessage());
            throw new RuntimeException(
                    "Exchange rate service unavailable: HTTP " + ex.getStatusCode(), ex);
        } catch (RuntimeException ex) {
            log.error("Exchange rate fetch failed for {} → {}: {}",
                    fromCurrency, toCurrency, ex.getMessage());
            throw ex;
        }
    }
}