package com.ssafy.motif.coreapi.admin.challenge;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeCreateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeDetailResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeListItem;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeSlotsResponse;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeUpdateRequest;
import com.ssafy.motif.coreapi.admin.challenge.dto.ChallengeValidateResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "등록 전 사전 검증(dry-run)",
            description = "등록과 같은 검증을 INSERT 없이 수행한다. 검증에 걸려도 200 이며 "
                    + "issues 에 사유가 담긴다 — 화면이 제출 전 매트릭스 완결성을 표시하는 용도다.")
    @PostMapping("/validate")
    public ApiResponse<ChallengeValidateResponse> validate(
            @Valid @RequestBody ChallengeCreateRequest request) {
        return ApiResponse.success(adminChallengeService.validate(request));
    }

    @Operation(summary = "챌린지 목록",
            description = "아티스트·곡으로 거르고 offset 페이지로 준다(§1.5 — size 기본 20·최대 100). "
                    + "삭제된 챌린지는 기본으로 빠지며 includeDeleted=true 로만 보인다.")
    @GetMapping
    public ApiResponse<PageResponse<ChallengeListItem>> list(
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(required = false) Integer songSeq,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminChallengeService.list(
                artistSeq, songSeq, includeDeleted, PageResponse.pageable(page, size)));
    }

    @Operation(summary = "챌린지 상세",
            description = "슬롯·편집본 매트릭스와 합성 잡 참조 수를 함께 준다.")
    @GetMapping("/{challengeSeq}")
    public ApiResponse<ChallengeDetailResponse> detail(@PathVariable Integer challengeSeq) {
        return ApiResponse.success(adminChallengeService.detail(challengeSeq));
    }

    @Operation(summary = "챌린지 수정",
            description = "소개글·곡만 바꾼다. videoKey·artistSeq 가 포함되면 400 이다 — "
                    + "원본을 바꾸면 bbox·편집본이 전부 무효라 삭제 후 재등록해야 한다.")
    @PatchMapping("/{challengeSeq}")
    public ApiResponse<ChallengeDetailResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                       @PathVariable Integer challengeSeq,
                                                       @Valid @RequestBody ChallengeUpdateRequest request) {
        return ApiResponse.success(adminChallengeService.update(admin, challengeSeq, request));
    }

    @Operation(summary = "슬롯 구성 전체 교체",
            description = "기존 슬롯·편집본을 하드 삭제하고 새 전조합으로 재생성한다. "
                    + "합성 잡이 참조하는 편집본이 하나라도 있으면 409 EDITED_IN_USE 다.")
    @PutMapping("/{challengeSeq}/participants")
    public ApiResponse<ChallengeSlotsResponse> replaceSlots(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable Integer challengeSeq,
            @Valid @RequestBody ChallengeSlotsRequest request) {
        return ApiResponse.success(adminChallengeService.replaceSlots(admin, challengeSeq, request));
    }
}
