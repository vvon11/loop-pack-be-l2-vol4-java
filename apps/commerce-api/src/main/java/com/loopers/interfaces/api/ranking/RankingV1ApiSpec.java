package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "일간·주간·월간 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "기간별 랭킹 페이지 조회",
            description = "date(yyyyMMdd, 생략 시 오늘)가 속한 기간(period: DAILY/WEEKLY/MONTHLY, 생략 시 DAILY)의 "
                    + "랭킹을 점수 내림차순으로 페이징 반환합니다. 일간은 Redis, 주간·월간은 MySQL MV 에서 조회합니다. "
                    + "상품 정보가 조립되어 반환되며, 삭제된 상품은 제외되어 페이지가 size 미만일 수 있습니다(순위 gap 유지)."
    )
    ApiResponse<RankingV1Dto.PageResponse> getRankings(String date, String period, int page, int size);
}
