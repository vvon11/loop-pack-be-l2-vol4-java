package com.loopers.batch.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * batch 는 product_metrics/MV 테이블에 대한 {@code @Entity} 가 없어 ddl-auto 가 만들어주지 않는다.
 * 테스트가 직접 DDL 로 생성한다({@code docs/week10/02-round10-ranking-schema.sql} 과 동일한 정의) —
 * streamer 의 {@code CouponIssueProcessorIntegrationTest} 와 같은 패턴.
 */
public final class RankingSchemaFixture {

    private RankingSchemaFixture() {
    }

    public static void createProductMetricsTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics (
                    metric_date  DATE        NOT NULL,
                    product_id   BIGINT      NOT NULL,
                    like_count   BIGINT      NOT NULL DEFAULT 0,
                    sales_count  BIGINT      NOT NULL DEFAULT 0,
                    view_count   BIGINT      NOT NULL DEFAULT 0,
                    updated_at   DATETIME(6) NOT NULL,
                    PRIMARY KEY (metric_date, product_id)
                )""");
    }

    public static void createWeeklyRankTable(JdbcTemplate jdbcTemplate) {
        createPeriodRankTable(jdbcTemplate, "mv_product_rank_weekly");
    }

    public static void createMonthlyRankTable(JdbcTemplate jdbcTemplate) {
        createPeriodRankTable(jdbcTemplate, "mv_product_rank_monthly");
    }

    private static void createPeriodRankTable(JdbcTemplate jdbcTemplate, String tableName) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    period_start  DATE          NOT NULL,
                    period_end    DATE          NOT NULL,
                    product_id    BIGINT        NOT NULL,
                    rank_no       BIGINT        NOT NULL,
                    view_count    BIGINT        NOT NULL,
                    like_count    BIGINT        NOT NULL,
                    sales_count   BIGINT        NOT NULL,
                    score         DECIMAL(19,4) NOT NULL,
                    calculated_at DATETIME(6)   NOT NULL,
                    PRIMARY KEY (period_start, product_id),
                    UNIQUE KEY uk_%s_period_rank (period_start, rank_no)
                )""".formatted(tableName, tableName));
    }

    public static void truncateProductMetrics(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    public static void truncateWeeklyRank(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
    }

    public static void truncateMonthlyRank(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
    }
}
