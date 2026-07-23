package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.PeriodProductRank;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MV 는 commerce-batch 가 JDBC 로 적재하는 조회 전용 테이블이라, 시딩도 엔티티가 아닌 JdbcTemplate 직접 INSERT
 * 로 한다(batch 의 실제 적재 경로를 그대로 재현).
 */
@SpringBootTest
class PeriodRankingJpaRepositoryIntegrationTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private PeriodRankingJpaRepository periodRankingJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("weekly MV 를 rank_no 오름차순으로 페이징 조회한다.")
    @Test
    void page_returnsWeeklyRowsOrderedByRank() {
        // arrange
        LocalDate periodStart = LocalDate.of(2026, 7, 20);
        seedWeekly(periodStart, 3L, 3);
        seedWeekly(periodStart, 1L, 1);
        seedWeekly(periodStart, 2L, 2);

        // act
        List<PeriodProductRank> result = periodRankingJpaRepository.page(RankingPeriod.WEEKLY, periodStart, 0, 10);

        // assert
        assertThat(result).extracting(PeriodProductRank::getProductId).containsExactly(1L, 2L, 3L);
        assertThat(result).extracting(PeriodProductRank::getRank).containsExactly(1L, 2L, 3L);
    }

    @DisplayName("연속된 두 페이지를 조회하면 항목이 겹치지 않는다.")
    @Test
    void page_consecutivePagesDoNotOverlap() {
        // arrange
        LocalDate periodStart = LocalDate.of(2026, 7, 20);
        for (long rank = 1; rank <= 5; rank++) {
            seedWeekly(periodStart, rank, (int) rank);
        }

        // act
        List<PeriodProductRank> firstPage = periodRankingJpaRepository.page(RankingPeriod.WEEKLY, periodStart, 0, 2);
        List<PeriodProductRank> secondPage = periodRankingJpaRepository.page(RankingPeriod.WEEKLY, periodStart, 1, 2);

        // assert
        assertThat(firstPage).extracting(PeriodProductRank::getProductId).containsExactly(1L, 2L);
        assertThat(secondPage).extracting(PeriodProductRank::getProductId).containsExactly(3L, 4L);
    }

    @DisplayName("total() 은 대상 기간 MV 전체 행 수를 반환한다.")
    @Test
    void total_returnsRowCountForPeriod() {
        // arrange
        LocalDate periodStart = LocalDate.of(2026, 7, 20);
        seedWeekly(periodStart, 1L, 1);
        seedWeekly(periodStart, 2L, 2);
        seedWeekly(LocalDate.of(2026, 7, 27), 3L, 1); // 다른 기간 — 카운트에서 제외

        // act & assert
        assertThat(periodRankingJpaRepository.total(RankingPeriod.WEEKLY, periodStart)).isEqualTo(2L);
    }

    @DisplayName("MV 가 없는 기간은 빈 페이지와 total 0 을 반환한다.")
    @Test
    void page_returnsEmpty_whenNoMvForPeriod() {
        LocalDate periodStart = LocalDate.of(2026, 7, 20);

        assertThat(periodRankingJpaRepository.page(RankingPeriod.WEEKLY, periodStart, 0, 10)).isEmpty();
        assertThat(periodRankingJpaRepository.total(RankingPeriod.WEEKLY, periodStart)).isZero();
    }

    @DisplayName("period 에 따라 weekly/monthly 테이블로 분기한다.")
    @Test
    void page_branchesByPeriodType() {
        // arrange
        LocalDate periodStart = LocalDate.of(2026, 7, 1);
        seedWeekly(periodStart, 1L, 1);
        seedMonthly(periodStart, 2L, 1);

        // act
        List<PeriodProductRank> weekly = periodRankingJpaRepository.page(RankingPeriod.WEEKLY, periodStart, 0, 10);
        List<PeriodProductRank> monthly = periodRankingJpaRepository.page(RankingPeriod.MONTHLY, periodStart, 0, 10);

        // assert
        assertThat(weekly).extracting(PeriodProductRank::getProductId).containsExactly(1L);
        assertThat(monthly).extracting(PeriodProductRank::getProductId).containsExactly(2L);
    }

    @DisplayName("DAILY 로 조회하면 예외가 발생한다(@Repository 프록시가 IllegalArgumentException 을 InvalidDataAccessApiUsageException 으로 변환).")
    @Test
    void page_throws_whenPeriodIsDaily() {
        assertThrows(InvalidDataAccessApiUsageException.class,
                () -> periodRankingJpaRepository.page(RankingPeriod.DAILY, LocalDate.now(), 0, 10));
    }

    private void seedWeekly(LocalDate periodStart, long productId, int rankNo) {
        seed("mv_product_rank_weekly", periodStart, productId, rankNo);
    }

    private void seedMonthly(LocalDate periodStart, long productId, int rankNo) {
        seed("mv_product_rank_monthly", periodStart, productId, rankNo);
    }

    private void seed(String table, LocalDate periodStart, long productId, int rankNo) {
        jdbcTemplate.update(
                "INSERT INTO " + table
                        + " (period_start, period_end, product_id, rank_no, view_count, like_count, sales_count, score, calculated_at) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 0, 0, ?)",
                periodStart, periodStart.plusDays(6), productId, rankNo, CALCULATED_AT);
    }
}
