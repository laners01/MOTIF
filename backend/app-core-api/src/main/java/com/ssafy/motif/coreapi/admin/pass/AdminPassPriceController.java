package com.ssafy.motif.coreapi.admin.pass;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceListResponse;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceRequest;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 패스 가격 API (어드민 명세 §3.9) — 아티스트×국가×개월. */
@Tag(name = "Admin Pass Price", description = "모티프 패스 가격 (어드민 명세 §3.9)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/artists/{artistSeq}/pass-prices")
public class AdminPassPriceController {

    private final AdminPassPriceService adminPassPriceService;

    @Operation(summary = "아티스트 가격표", description = "설정된 행만 준다 — 없는 조합은 화면의 빈 칸이다.")
    @GetMapping
    public ApiResponse<PassPriceListResponse> list(@PathVariable int artistSeq) {
        return ApiResponse.success(adminPassPriceService.list(artistSeq));
    }

    @Operation(summary = "가격 upsert",
            description = "갱신 이후 주문부터 적용된다. 기존 ACTIVE 패스의 금액은 바뀌지 않는다.")
    @PutMapping("/{countryCode}/{durationMonths}")
    public ApiResponse<PassPriceResponse> upsert(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int artistSeq,
            @PathVariable String countryCode,
            @PathVariable short durationMonths,
            @Valid @RequestBody PassPriceRequest request) {
        return ApiResponse.success(
                adminPassPriceService.upsert(admin, artistSeq, countryCode, durationMonths, request));
    }
}
