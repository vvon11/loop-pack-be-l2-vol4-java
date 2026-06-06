package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할인 정책 — 할인 종류(type)와 값(value)을 묶고 할인 계산 규칙을 캡슐화한 불변 값 객체.
 * 같은 VO 를 {@code CouponTemplate}(원형 정의)과 {@code UserCoupon}(발급 스냅샷)이 각각 보유한다.
 */
@Getter
@EqualsAndHashCode
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountPolicy {

    private static final long RATE_MAX = 100L;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false)
    private long value;

    private DiscountPolicy(DiscountType type, long value) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 종류는 비어있을 수 없습니다.");
        }
        if (value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 1 이상이어야 합니다.");
        }
        if (type == DiscountType.RATE && value > RATE_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인 값은 1~100 사이여야 합니다.");
        }
        this.type = type;
        this.value = value;
    }

    public static DiscountPolicy of(DiscountType type, long value) {
        return new DiscountPolicy(type, value);
    }

    /**
     * 적용 전 금액에 정책을 적용한 할인액을 계산한다.
     * 할인액은 0 이상이며 적용 전 금액을 초과하지 않는다.
     */
    public Money calculate(Money orderAmount) {
        if (orderAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "적용 전 금액은 비어있을 수 없습니다.");
        }
        try {
            return Money.of(type.discountOf(orderAmount.getAmount(), value));
        } catch (ArithmeticException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인액 계산 중 오버플로우가 발생했습니다.");
        }
    }
}
