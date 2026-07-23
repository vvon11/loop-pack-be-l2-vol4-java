package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsId;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 상품이라도 occurredAt 의 KST 날짜가 다르면 서로 다른 product_metrics 행으로 귀속되는지를
 * 실제 Kafka(Testcontainer) 로 확인한다 — 복합키 전환의 핵심 목적(기간별 SUM 가능성)을 직접 검증한다.
 */
@SpringBootTest
class ProductMetricsDailyPartitionIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime DAY_1 = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, KST);
    private static final ZonedDateTime DAY_2 = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0, KST);

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 상품의 조회 이벤트가 서로 다른 날짜에 발생하면 metric_date 별로 별개의 행이 적재된다.")
    @Test
    void sameProduct_differentDates_createsSeparateRows() {
        long productId = 9401L;
        try (Producer<String, String> producer = newProducer()) {
            send(producer, viewedAt(productId, DAY_1));
            send(producer, viewedAt(productId, DAY_2));
            send(producer, viewedAt(productId, DAY_2));

            ProductMetrics day1Metrics = awaitViewCount(ProductMetricsId.of(DAY_1, productId), 1L);
            ProductMetrics day2Metrics = awaitViewCount(ProductMetricsId.of(DAY_2, productId), 2L);

            assertThat(day1Metrics.getMetricDate()).isEqualTo(DAY_1.withZoneSameInstant(KST).toLocalDate());
            assertThat(day2Metrics.getMetricDate()).isEqualTo(DAY_2.withZoneSameInstant(KST).toLocalDate());
            assertThat(day1Metrics.getViewCount()).isEqualTo(1L);
            assertThat(day2Metrics.getViewCount()).isEqualTo(2L);
        }
    }

    private CatalogEventMessage viewedAt(long productId, ZonedDateTime occurredAt) {
        return new CatalogEventMessage(UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED, productId, null, occurredAt);
    }

    private void send(Producer<String, String> producer, CatalogEventMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            producer.send(new ProducerRecord<>(
                    CatalogEventConsumer.CATALOG_EVENTS, String.valueOf(message.productId()), payload));
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("테스트 메시지 발행 실패", e);
        }
    }

    private Producer<String, String> newProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    private ProductMetrics awaitViewCount(ProductMetricsId id, long expected) {
        ProductMetrics metrics = null;
        for (int i = 0; i < 100; i++) {
            metrics = productMetricsJpaRepository.findById(id).orElse(null);
            if (metrics != null && metrics.getViewCount() == expected) {
                return metrics;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("product_metrics(id=" + id + ").view_count 가 " + expected
                + " 로 수렴하지 못했습니다. (현재=" + (metrics == null ? "없음" : metrics.getViewCount()) + ")");
    }
}
