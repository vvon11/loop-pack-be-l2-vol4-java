package com.loopers.batch.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Job Parameter {@code targetDate} 파서. yyyyMMdd 이외 형식/누락은 Step 을 FAILED 로 만든다. */
public record TargetDate(LocalDate value) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    public static TargetDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("targetDate 파라미터가 필요합니다.");
        }

        try {
            return new TargetDate(LocalDate.parse(raw, FORMAT));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("targetDate 형식이 올바르지 않습니다(yyyyMMdd): " + raw, e);
        }
    }
}
