package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ExchangeRateController — exposes exchange rate lookups.
 *
 * GET /exchange-rates/{from}/{to}  → get current rate
 * DELETE /admin/exchange-rates/{from}/{to}/cache → evict cached rate
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/exchange-rates/{from}/{to}")
    public ResponseEntity<Map<String, Object>> getRate(
            @PathVariable String from,
            @PathVariable String to) {

        log.debug("GET /exchange-rates/{}/{}", from, to);
        BigDecimal rate = exchangeRateService.getRate(from, to);

        return ResponseEntity.ok(Map.of(
                "fromCurrency", from.toUpperCase(),
                "toCurrency",   to.toUpperCase(),
                "rate",         rate,
                "source",       "cached-or-live"
        ));
    }

    @DeleteMapping("/admin/exchange-rates/{from}/{to}/cache")
    public ResponseEntity<Void> evictCache(
            @PathVariable String from,
            @PathVariable String to) {

        log.info("DELETE /admin/exchange-rates/{}/{}/cache", from, to);
        exchangeRateService.evictCache(from, to);
        return ResponseEntity.noContent().build();
    }
}