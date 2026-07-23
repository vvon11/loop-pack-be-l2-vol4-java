package com.loopers.job.ranking.monthly;

import com.loopers.batch.job.ranking.monthly.MonthlyProductRankingJobConfig;
import com.loopers.batch.support.RankingSchemaFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * monthlyProductRankingJob E2E. Weekly 와 겹치는 로직(가중합·동점·재실행 등)은 Phase 4/{@code
 * ProductMetricsAggregationJdbcRepositoryIntegrationTest} 에서 이미 검증했으므로, 여기서는 월 경계에 집중한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyProductRankingJobConfig.JOB_NAME)
class MonthlyProductRankingJobE2ETest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(MonthlyProductRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        RankingSchemaFixture.createProductMetricsTable(jdbcTemplate);
        RankingSchemaFixture.createMonthlyRankTable(jdbcTemplate);
        RankingSchemaFixture.truncateProductMetrics(jdbcTemplate);
        RankingSchemaFixture.truncateMonthlyRank(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @DisplayName("31일까지 있는 달(7월)은 1일과 31일 지표를 모두 포함하고, 다음 달 1일은 제외한다.")
    @Test
    void julyHasThirtyOneDays_includesFirstAndLastDay() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 1), 1L, 1);
        seedMetrics(LocalDate.of(2026, 7, 31), 1L, 2);
        seedMetrics(LocalDate.of(2026, 8, 1), 1L, 100); // 다음 달 — 제외

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(targetDateParams("20260715"));

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = monthlyRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("view_count")).isEqualTo(3L); // 1(7/1) + 2(7/31), 8/1 은 제외
        assertThat(rows.get(0).get("period_start")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 7, 1)));
        assertThat(rows.get(0).get("period_end")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 7, 31)));
    }

    @DisplayName("30일까지 있는 달(4월)은 30일을 마지막날로 포함하고, 5월 1일은 제외한다.")
    @Test
    void aprilHasThirtyDays_excludesNextMonth() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 4, 30), 1L, 5);
        seedMetrics(LocalDate.of(2026, 5, 1), 1L, 100); // 다음 달 — 제외

        // act
        jobLauncherTestUtils.launchJob(targetDateParams("20260415"));

        // assert
        List<Map<String, Object>> rows = monthlyRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("view_count")).isEqualTo(5L);
        assertThat(rows.get(0).get("period_end")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 4, 30)));
    }

    @DisplayName("윤년 2월은 29일까지를 마지막날로 포함한다.")
    @Test
    void leapFebruary_includesTwentyNinthDay() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2024, 2, 29), 1L, 7);
        seedMetrics(LocalDate.of(2024, 3, 1), 1L, 100); // 다음 달 — 제외

        // act
        jobLauncherTestUtils.launchJob(targetDateParams("20240215"));

        // assert
        List<Map<String, Object>> rows = monthlyRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("view_count")).isEqualTo(7L);
        assertThat(rows.get(0).get("period_end")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2024, 2, 29)));
    }

    @DisplayName("정상 적재 시 상품별 가중합 순위를 부여한다.")
    @Test
    void loadsMonthlyTopRanking() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 10), 1L, 10);
        seedMetrics(LocalDate.of(2026, 7, 20), 2L, 5);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(targetDateParams("20260715"));

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = monthlyRows();
        assertThat(rows).extracting(row -> row.get("product_id")).containsExactly(1L, 2L);
    }

    @DisplayName("동일 targetDate 로 재실행해도 MV 행이 중복되지 않는다.")
    @Test
    void rerunWithSameTargetDate_doesNotDuplicateRows() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 10), 1L, 10);
        JobParameters params = targetDateParams("20260715");

        // act
        jobLauncherTestUtils.launchJob(params);
        jobRepositoryTestUtils.removeJobExecutions();
        jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(monthlyRows()).hasSize(1);
    }

    private JobParameters targetDateParams(String targetDate) {
        return new JobParametersBuilder().addString("targetDate", targetDate).toJobParameters();
    }

    private void seedMetrics(LocalDate metricDate, long productId, int viewCount) {
        jdbcTemplate.update(
                "INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, updated_at) "
                        + "VALUES (?, ?, ?, 0, 0, ?)",
                metricDate, productId, viewCount, SEED_TIME);
    }

    private List<Map<String, Object>> monthlyRows() {
        return jdbcTemplate.queryForList("SELECT * FROM mv_product_rank_monthly ORDER BY rank_no");
    }
}
