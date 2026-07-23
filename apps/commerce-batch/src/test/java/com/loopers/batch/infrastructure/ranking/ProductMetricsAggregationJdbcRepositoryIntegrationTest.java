package com.loopers.batch.infrastructure.ranking;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.support.RankingSchemaFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Batch 없이 집계 SQL 자체를 못박는 통합 테스트. product_metrics 는 streamer 소유라 batch 엔티티가
 * 없어 ddl-auto 가 만들어주지 않으므로 {@link RankingSchemaFixture} 로 직접 DDL 생성한다.
 */
// application.yml 의 spring.batch.job.name 기본값("NONE")이 실제 Job 이름이 아니라 JobLauncherApplicationRunner 가
// 부팅 시 "No job found with name 'NONE'" 로 실패한다 — Job 을 다루지 않는 이 테스트는 빈 값으로 덮어써 회피한다.
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.name=")
class ProductMetricsAggregationJdbcRepositoryIntegrationTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private ProductMetricsAggregationJdbcRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RankingSchemaFixture.createProductMetricsTable(jdbcTemplate);
        RankingSchemaFixture.truncateProductMetrics(jdbcTemplate);
    }

    @DisplayName("가중합 score 를 view*0.1 + like*0.2 + sales*0.7 로 정확히 계산한다.")
    @Test
    void computesWeightedScore() {
        // arrange
        seed(LocalDate.of(2026, 7, 20), 1L, 10, 10, 10);

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @DisplayName("기간 밖 데이터는 집계에서 제외한다.")
    @Test
    void excludesRowsOutsidePeriod() {
        // arrange
        seed(LocalDate.of(2026, 7, 19), 1L, 100, 0, 0);
        seed(LocalDate.of(2026, 7, 20), 1L, 5, 0, 0);
        seed(LocalDate.of(2026, 7, 21), 1L, 100, 0, 0);

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).viewCount()).isEqualTo(5L);
    }

    @DisplayName("기간 내 여러 날짜의 지표를 상품별로 SUM 한다.")
    @Test
    void sumsAcrossMultipleDaysInPeriod() {
        // arrange
        seed(LocalDate.of(2026, 7, 20), 1L, 3, 0, 0);
        seed(LocalDate.of(2026, 7, 21), 1L, 4, 0, 0);

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-26"), 100);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).viewCount()).isEqualTo(7L);
    }

    @DisplayName("점수가 같으면 product_id 오름차순으로 정렬해 결정적 순위를 부여한다.")
    @Test
    void tieBreaksByProductIdAscending() {
        // arrange
        seed(LocalDate.of(2026, 7, 20), 3L, 10, 0, 0);
        seed(LocalDate.of(2026, 7, 20), 1L, 10, 0, 0);
        seed(LocalDate.of(2026, 7, 20), 2L, 10, 0, 0);

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).extracting(ProductRankAggregate::productId).containsExactly(1L, 2L, 3L);
        assertThat(result).extracting(ProductRankAggregate::rankNo).containsExactly(1L, 2L, 3L);
    }

    @DisplayName("반환되는 rank_no 는 LIMIT 이전 전체 순위를 반영해 항상 조밀한 1..N 이다.")
    @Test
    void rankIsDenseAfterLimit() {
        // arrange
        for (long productId = 1; productId <= 105; productId++) {
            seed(LocalDate.of(2026, 7, 20), productId, (int) (200 - productId), 0, 0);
        }

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).hasSize(100);
        assertThat(result.get(0).rankNo()).isEqualTo(1L);
        assertThat(result.get(99).rankNo()).isEqualTo(100L);
        assertThat(result).extracting(ProductRankAggregate::productId).containsExactly(1L, 2L, 3L, 4L, 5L,
                6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 25L,
                26L, 27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L, 41L, 42L, 43L, 44L,
                45L, 46L, 47L, 48L, 49L, 50L, 51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L, 61L, 62L, 63L,
                64L, 65L, 66L, 67L, 68L, 69L, 70L, 71L, 72L, 73L, 74L, 75L, 76L, 77L, 78L, 79L, 80L, 81L, 82L,
                83L, 84L, 85L, 86L, 87L, 88L, 89L, 90L, 91L, 92L, 93L, 94L, 95L, 96L, 97L, 98L, 99L, 100L);
    }

    @DisplayName("like_count 음수(순감소)에도 score 필터 없이 그대로 반영한다.")
    @Test
    void allowsNegativeLikeCount() {
        // arrange
        seed(LocalDate.of(2026, 7, 20), 1L, 0, -5, 0);

        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).likeCount()).isEqualTo(-5L);
        assertThat(result.get(0).score()).isEqualByComparingTo(new BigDecimal("-1.0"));
    }

    @DisplayName("집계 대상이 없으면 빈 목록을 반환한다.")
    @Test
    void returnsEmpty_whenNoRowsInPeriod() {
        // act
        List<ProductRankAggregate> result = repository.aggregate(range("2026-07-20", "2026-07-20"), 100);

        // assert
        assertThat(result).isEmpty();
    }

    private PeriodRange range(String start, String end) {
        return new PeriodRange(LocalDate.parse(start), LocalDate.parse(end));
    }

    private void seed(LocalDate metricDate, long productId, int viewCount, int likeCount, int salesCount) {
        jdbcTemplate.update(
                "INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                metricDate, productId, viewCount, likeCount, salesCount, SEED_TIME);
    }
}
