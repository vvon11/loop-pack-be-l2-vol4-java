package com.loopers.application.coupon;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderCriteria;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("쿠폰 사용 동시성")
@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 유저가 같은 쿠폰으로 동시에 여러 주문을 넣어도, 쿠폰은 정확히 1건의 주문에만 사용되고 나머지는 거부된다.")
    @Test
    void concurrentOrders_doNotReuseSameCoupon() throws InterruptedException {
        Long userId = 1L;
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();

        int attempts = 30;
        // 재고는 쿠폰이 병목이 되도록 넉넉히 — 모든 주문이 재고로는 성공할 수 있어야 한다.
        Long productId = productJpaRepository.save(
                Product.create(brandId, "상품", Money.of(1_000L), Stock.of(attempts))).getId();

        CouponTemplate template = couponTemplateJpaRepository.save(
                CouponTemplate.create("정액 500원", DiscountPolicy.of(DiscountType.FIXED, 500L), 30));
        Long couponId = userCouponJpaRepository.save(
                UserCoupon.issue(userId, template, ZonedDateTime.now())).getId();

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderApplicationService.place(new OrderCriteria.Place(
                            userId, couponId, List.of(new OrderCriteria.Line(productId, 1))));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 도메인 거부(CoreException)·낙관적 락 충돌 등 모두 실패로 집계
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 주문 시도가 30초 내에 끝나야 한다").isTrue();

        UserCoupon coupon = userCouponJpaRepository.findById(couponId).orElseThrow();

        assertThat(successCount.get()).as("성공한 주문 수").isEqualTo(1);
        assertThat(failureCount.get()).as("실패한 주문 수").isEqualTo(attempts - 1);
        assertThat(coupon.getStatus()).as("쿠폰 최종 상태").isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).as("쿠폰이 사용된 주문은 정확히 하나여야 한다").isNotNull();
    }
}
