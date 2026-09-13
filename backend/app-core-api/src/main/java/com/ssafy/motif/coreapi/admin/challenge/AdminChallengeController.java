package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 챌린지 API (어드민 명세 §3.7). */
@Tag(name = "Admin Challenge", description = "챌린지 등록·관리 (어드민 명세 §3.7)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/challenges")
public class AdminChallengeController {

    private final AdminChallengeService adminChallengeService;

    @Operation(summary = "챌린지 등록",
            description = "원본·슬롯·편집본을 한 트랜잭션에 all-or-nothing 으로 등록한다. "
                    + "슬롯 n 개면 편집본 2ⁿ−1 개 전조합이 필수다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChallengeSlotsResponse> create(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                      @Valid @RequestBody ChallengeCreateRequest request) {
        return ApiResponse.success(adminChallengeService.create(admin, request));
    }
}
