package com.loopers.job.ranking.weekly;

import com.loopers.batch.job.ranking.weekly.WeeklyProductRankingJobConfig;
import com.loopers.batch.support.RankingSchemaFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * weeklyProductRankingJob 의 Chunk Step E2E. product_metrics/MV 는 batch 에 엔티티가 없어 ddl-auto 가
 * 만들어주지 않으므로 {@link RankingSchemaFixture} 로 직접 DDL 생성한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyProductRankingJobConfig.JOB_NAME)
class WeeklyProductRankingJobE2ETest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    // 2026-07-20 은 월요일 → 주간 범위 2026-07-20 ~ 2026-07-26
    private static final String TARGET_DATE = "20260720";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(WeeklyProductRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        RankingSchemaFixture.createProductMetricsTable(jdbcTemplate);
        RankingSchemaFixture.createWeeklyRankTable(jdbcTemplate);
        RankingSchemaFixture.truncateProductMetrics(jdbcTemplate);
        RankingSchemaFixture.truncateWeeklyRank(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @DisplayName("기간 내 지표를 가중합·정렬해 MV 에 적재한다.")
    @Test
    void loadsWeeklyTopRanking() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 20), 1L, 10, 10, 10); // score = 1+2+7 = 10.0

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(targetDateParams(TARGET_DATE));

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> rows = weeklyRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("product_id")).isEqualTo(1L);
        assertThat(rows.get(0).get("rank_no")).isEqualTo(1L);
        assertThat((BigDecimal) rows.get(0).get("score")).isEqualByComparingTo("10.0000");

        StepExecution stepExecution = jobExecution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(1);
        assertThat(stepExecution.getWriteCount()).isEqualTo(1);
        assertThat(stepExecution.getCommitCount()).isEqualTo(1); // chunkSize=TOP_N 이라 전체가 1 커밋
    }

    @DisplayName("기간 밖 날짜의 지표는 집계에서 제외한다.")
    @Test
    void excludesMetricsOutsidePeriod() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 19), 1L, 100, 0, 0); // 기간 시작 하루 전 — 제외
        seedMetrics(LocalDate.of(2026, 7, 20), 1L, 3, 0, 0);   // 기간 첫날 — 포함

        // act
        jobLauncherTestUtils.launchJob(targetDateParams(TARGET_DATE));

        // assert
        List<Map<String, Object>> rows = weeklyRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("view_count")).isEqualTo(3L);
    }

    @DisplayName("점수가 같으면 product_id 오름차순으로 결정적 순위를 부여한다.")
    @Test
    void tieBreaksByProductIdAscending() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 20), 3L, 10, 0, 0);
        seedMetrics(LocalDate.of(2026, 7, 20), 1L, 10, 0, 0);
        seedMetrics(LocalDate.of(2026, 7, 20), 2L, 10, 0, 0);

        // act
        jobLauncherTestUtils.launchJob(targetDateParams(TARGET_DATE));

        // assert
        List<Map<String, Object>> rows = weeklyRows();
        assertThat(rows).extracting(row -> row.get("product_id")).containsExactly(1L, 2L, 3L);
    }

    @DisplayName("집계 대상이 100개를 넘으면 TOP 100 만 적재한다.")
    @Test
    void capsAtTop100() throws Exception {
        // arrange
        for (long productId = 1; productId <= 105; productId++) {
            seedMetrics(LocalDate.of(2026, 7, 20), productId, (int) (200 - productId), 0, 0);
        }

        // act
        jobLauncherTestUtils.launchJob(targetDateParams(TARGET_DATE));

        // assert
        assertThat(weeklyRows()).hasSize(100);
    }

    @DisplayName("동일 targetDate 로 재실행해도 MV 행이 중복되지 않는다.")
    @Test
    void rerunWithSameTargetDate_doesNotDuplicateRows() throws Exception {
        // arrange
        seedMetrics(LocalDate.of(2026, 7, 20), 1L, 10, 0, 0);
        JobParameters params = targetDateParams(TARGET_DATE);

        // act — 1차 실행 후 JobRepository 메타데이터를 지우고 동일 파라미터로 재실행(운영의 "동일 파라미터 재실행"을 재현)
        jobLauncherTestUtils.launchJob(params);
        jobRepositoryTestUtils.removeJobExecutions();
        jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(weeklyRows()).hasSize(1);
    }

    @DisplayName("집계 결과가 0건이어도 대상 기간의 기존 MV 는 삭제되고, 다른 기간의 MV 는 보존된다.")
    @Test
    void emptyAggregate_stillDeletesTargetPeriod_preservesOtherPeriods() throws Exception {
        // arrange — 대상 주간(7/20~7/26)과 다른 주간(7/13~7/19)에 각각 기존 MV 행을 미리 심어 둔다.
        seedWeeklyRankRow(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 1L);
        seedWeeklyRankRow(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), 2L);
        // product_metrics 는 비워 둔다 → 대상 기간 집계 결과 0건.

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(targetDateParams(TARGET_DATE));

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        List<Map<String, Object>> remaining = jdbcTemplate.queryForList(
                "SELECT period_start, product_id FROM mv_product_rank_weekly ORDER BY period_start");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("period_start")).isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 7, 13)));
    }

    @DisplayName("targetDate 파라미터가 없으면 FAILED 로 종료한다.")
    @Test
    void failsWhenTargetDateMissing() throws Exception {
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder().toJobParameters());

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("targetDate 형식이 올바르지 않으면 FAILED 로 종료한다.")
    @Test
    void failsWhenTargetDateMalformed() throws Exception {
        var jobExecution = jobLauncherTestUtils.launchJob(targetDateParams("2026-07-20"));

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    private JobParameters targetDateParams(String targetDate) {
        // addLocalDate 를 쓰면 SpEL 이 ISO 형식(yyyy-MM-dd)으로 변환돼 BASIC_ISO_DATE 파싱이 깨진다 — addString 사용.
        return new JobParametersBuilder().addString("targetDate", targetDate).toJobParameters();
    }

    private void seedMetrics(LocalDate metricDate, long productId, int viewCount, int likeCount, int salesCount) {
        jdbcTemplate.update(
                "INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                metricDate, productId, viewCount, likeCount, salesCount, SEED_TIME);
    }

    private void seedWeeklyRankRow(LocalDate periodStart, LocalDate periodEnd, long productId) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly "
                        + "(period_start, period_end, product_id, rank_no, view_count, like_count, sales_count, score, calculated_at) "
                        + "VALUES (?, ?, ?, 1, 0, 0, 0, 0, ?)",
                periodStart, periodEnd, productId, SEED_TIME);
    }

    private List<Map<String, Object>> weeklyRows() {
        return jdbcTemplate.queryForList("SELECT * FROM mv_product_rank_weekly ORDER BY rank_no");
    }
}
