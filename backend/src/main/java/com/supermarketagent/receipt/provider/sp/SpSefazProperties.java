package com.supermarketagent.receipt.provider.sp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("sefaz.sp")
public record SpSefazProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration readTimeout,
        @DefaultValue("1s") Duration minInterval,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration retryBackoff,
        @DefaultValue("SupermarketUltimateAgent/0.1 (+https://github.com/Projetos-Pessoais-gbrito/supermarket-ultimate-agent)")
        String userAgent) {
}
