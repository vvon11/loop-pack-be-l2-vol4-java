package com.loopers.batch.infrastructure.ranking;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.support.config.RankingWeightProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code product_metrics}(commerce-streamer 소유) 를 기간 범위로 집계해 TOP N 을 산출한다. batch 는 이 테이블에
 * {@code @Entity} 를 두지 않고 네이티브 SQL 만 실행한다 — 소유하지 않는 테이블에 엔티티를 추가하면 local 의
 * {@code ddl-auto: create} 가 streamer 가 쌓은 데이터를 DROP/CREATE 로 날릴 수 있다
 * ({@code CouponIssueJdbcRepository} 와 같은 이유의 선택).
 *
 * <p>집계·정렬·TOP N 산출을 전부 SQL 에서 끝내므로 애플리케이션에는 최대 {@code limit} 건만 유입된다.
 * {@code LIMIT} 이 {@code ROW_NUMBER()} 뒤에 적용되므로 반환되는 rank 는 항상 조밀한 {@code 1..N} 이다.</p>
 */
@Repository
public class ProductMetricsAggregationJdbcRepository {

    private static final String AGGREGATE_SQL = """
            SELECT agg.product_id, agg.view_count, agg.like_count, agg.sales_count, agg.score,
                   ROW_NUMBER() OVER (ORDER BY agg.score DESC, agg.product_id ASC) AS rank_no
            FROM (
                SELECT product_id,
                       SUM(view_count) AS view_count, SUM(like_count) AS like_count, SUM(sales_count) AS sales_count,
                       SUM(view_count) * :viewWeight + SUM(like_count) * :likeWeight
                     + SUM(sales_count) * :salesWeight AS score
                FROM product_metrics
                WHERE metric_date BETWEEN :periodStart AND :periodEnd
                GROUP BY product_id
            ) agg
            ORDER BY agg.score DESC, agg.product_id ASC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RankingWeightProperties weightProperties;

    public ProductMetricsAggregationJdbcRepository(
            NamedParameterJdbcTemplate jdbcTemplate, RankingWeightProperties weightProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.weightProperties = weightProperties;
    }

    public List<ProductRankAggregate> aggregate(PeriodRange range, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("viewWeight", weightProperties.view())
                .addValue("likeWeight", weightProperties.like())
                .addValue("salesWeight", weightProperties.sales())
                .addValue("periodStart", range.start())
                .addValue("periodEnd", range.end())
                .addValue("limit", limit);

        return jdbcTemplate.query(AGGREGATE_SQL, params, (rs, rowNum) -> new ProductRankAggregate(
                rs.getLong("product_id"),
                rs.getLong("rank_no"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count"),
                rs.getBigDecimal("score")
        ));
    }
}
