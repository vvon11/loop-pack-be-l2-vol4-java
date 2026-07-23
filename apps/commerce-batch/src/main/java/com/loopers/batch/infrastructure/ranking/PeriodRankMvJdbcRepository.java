package com.loopers.batch.infrastructure.ranking;

import com.loopers.batch.domain.ranking.PeriodRankRow;
import com.loopers.batch.domain.ranking.RankingPeriodType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * MV 테이블({@code mv_product_rank_weekly}/{@code monthly}) 쓰기 전용 리포지토리. commerce-api 가 Hibernate
 * {@code NORMALIZE_UTC} 로 읽으므로 {@code calculated_at} 은 UTC 로 변환해 바인딩한다
 * ({@code CouponIssueJdbcRepository} 와 동일 처리).
 */
@Repository
public class PeriodRankMvJdbcRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PeriodRankMvJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteByPeriodStart(RankingPeriodType periodType, LocalDate periodStart) {
        jdbcTemplate.update(
                "DELETE FROM " + periodType.tableName() + " WHERE period_start = :periodStart",
                new MapSqlParameterSource("periodStart", periodStart));
    }

    public void insertAll(RankingPeriodType periodType, List<PeriodRankRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO " + periodType.tableName()
                + " (period_start, period_end, product_id, rank_no, view_count, like_count, sales_count, score, calculated_at) "
                + "VALUES (:periodStart, :periodEnd, :productId, :rankNo, :viewCount, :likeCount, :salesCount, :score, :calculatedAt)";

        MapSqlParameterSource[] params = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("periodStart", row.periodStart())
                        .addValue("periodEnd", row.periodEnd())
                        .addValue("productId", row.productId())
                        .addValue("rankNo", row.rankNo())
                        .addValue("viewCount", row.viewCount())
                        .addValue("likeCount", row.likeCount())
                        .addValue("salesCount", row.salesCount())
                        .addValue("score", row.score())
                        .addValue("calculatedAt", utc(row.calculatedAt())))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    private static LocalDateTime utc(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
