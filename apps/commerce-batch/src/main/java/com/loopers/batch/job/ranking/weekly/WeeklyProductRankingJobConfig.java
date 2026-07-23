package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.domain.ranking.PeriodRange;
import com.loopers.batch.domain.ranking.PeriodRankRow;
import com.loopers.batch.domain.ranking.ProductRankAggregate;
import com.loopers.batch.domain.ranking.RankingPeriodType;
import com.loopers.batch.domain.ranking.RankingPolicy;
import com.loopers.batch.domain.ranking.TargetDate;
import com.loopers.batch.infrastructure.ranking.PeriodRankMvJdbcRepository;
import com.loopers.batch.infrastructure.ranking.ProductMetricsAggregationJdbcRepository;
import com.loopers.batch.job.ranking.step.PeriodRankItemProcessor;
import com.loopers.batch.job.ranking.step.PeriodRankMvItemWriter;
import com.loopers.batch.job.ranking.step.ProductRankAggregationReader;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 주간 TOP 100 랭킹 MV({@code mv_product_rank_weekly}) 적재 Job. Chunk Step 이며
 * {@code chunkSize = RankingPolicy.TOP_N} 이라 이번 실행분 전체가 정확히 1 chunk = 1 트랜잭션이다.
 *
 * <p>Reader/Processor/Writer 는 {@code @Component} 로 두지 않고 이 JobConfig 가 {@code @StepScope} 로
 * 직접 조립한다 — {@code @ConditionalOnProperty} 는 값을 하나만 가져 weekly/monthly Job 이 같은
 * {@code @Component} 를 공유할 수 없기 때문이다.</p>
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyProductRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyProductRankingJobConfig {

    public static final String JOB_NAME = "weeklyProductRankingJob";
    private static final String STEP_NAME = "weeklyProductRankingStep";
    private static final RankingPeriodType PERIOD_TYPE = RankingPeriodType.WEEKLY;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductMetricsAggregationJdbcRepository aggregationRepository;
    private final PeriodRankMvJdbcRepository periodRankMvJdbcRepository;

    @Bean(JOB_NAME)
    public Job weeklyProductRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyProductRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyProductRankingStep() {
        PeriodRankMvItemWriter writer = weeklyRankWriter(null);
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductRankAggregate, PeriodRankRow>chunk(RankingPolicy.TOP_N, transactionManager)
                .reader(weeklyRankReader(null))
                .processor(weeklyRankProcessor(null))
                .writer(writer)
                .listener(writer) // ChunkListener — 빈 청크에도 beforeChunk 삭제를 보장
                .listener(stepMonitorListener)
                .build();
    }

    // PeriodRange 는 record(= final) 라 @StepScope 의 CGLIB 스코프 프록시 대상이 될 수 없다.
    // 그래서 별도 @Bean 으로 캐지 않고 Reader/Processor/Writer 각자가 targetDate 로부터 계산한다.
    private static PeriodRange rangeOf(String targetDate) {
        return PERIOD_TYPE.rangeOf(TargetDate.parse(targetDate).value());
    }

    @StepScope
    @Bean
    public ProductRankAggregationReader weeklyRankReader(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new ProductRankAggregationReader(aggregationRepository, rangeOf(targetDate), RankingPolicy.TOP_N);
    }

    @StepScope
    @Bean
    public PeriodRankItemProcessor weeklyRankProcessor(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new PeriodRankItemProcessor(rangeOf(targetDate));
    }

    @StepScope
    @Bean
    public PeriodRankMvItemWriter weeklyRankWriter(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new PeriodRankMvItemWriter(periodRankMvJdbcRepository, PERIOD_TYPE, rangeOf(targetDate));
    }
}
