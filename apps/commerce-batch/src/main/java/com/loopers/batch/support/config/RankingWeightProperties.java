package com.loopers.batch.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 주간·월간 랭킹 가중치. commerce-streamer 의 {@code RankingProperties}(일간, double)와 독립적으로 소유한다 —
 * 공유 모듈로 추출하지 않는다(값 drift 는 운영 규칙으로 관리). {@code BigDecimal} 인 이유: MySQL 이 DOUBLE 이
 * 아닌 DECIMAL 산술을 하도록 해 score 정렬이 부동소수점 오차 없이 결정적이게 한다.
 */
@ConfigurationProperties(prefix = "batch.ranking.weight")
public record RankingWeightProperties(BigDecimal view, BigDecimal like, BigDecimal sales) {
}
