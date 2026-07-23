package com.loopers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// application.yml 의 spring.batch.job.name 기본값("NONE")은 실행용(bootRun --args=job.name=...) 기본값이라
// 실제 Job 이름이 아니다 — 그대로 두면 JobLauncherApplicationRunner 가 "No job found with name 'NONE'" 로
// 기동에 실패한다. 특정 Job 을 다루지 않는 컨텍스트 로딩 테스트이므로 빈 값으로 덮어써 회피한다.
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.name=")
public class CommerceBatchApplicationTest {
    @Test
    void contextLoads() {}
}
