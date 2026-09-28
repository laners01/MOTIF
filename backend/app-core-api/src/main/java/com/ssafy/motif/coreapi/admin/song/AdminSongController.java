package com.ssafy.motif.coreapi.admin.song;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.song.dto.SongCreateRequest;
import com.ssafy.motif.coreapi.admin.song.dto.SongCreateResponse;
import com.ssafy.motif.coreapi.admin.song.dto.SongResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 어드민 곡 API (어드민 명세 §3.6) — 목록·등록 둘뿐이다.
 *
 * <p>수정(3.6.3)은 명세에서 삭제됐다(R1) — 곡은 불변이라 오류는 새 곡 등록 +
 * 챌린지 재연결(3.7.4)로 고친다. 삭제도 없다({@code songs} 에 {@code is_deleted} 없음).
 */
@Tag(name = "Admin Song", description = "곡 목록·등록 (어드민 명세 §3.6)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/songs")
public class AdminSongController {

    private final AdminSongService adminSongService;

    @Operation(summary = "곡 목록",
            description = "q 는 곡명·가수명 부분 일치(대소문자 무시). 최신 등록순 고정이며 "
                    + "size 는 기본 20·최대 100 으로 잘린다.")
    @GetMapping
    public ApiResponse<PageResponse<SongResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminSongService.list(q, page, size));
    }

    @Operation(summary = "곡 등록",
            description = "중복 곡명을 막지 않는다 — 리믹스·재녹음이 같은 제목을 쓴다. "
                    + "화면이 등록 전 동일 곡명 존재를 경고만 한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SongCreateResponse> create(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                  @Valid @RequestBody SongCreateRequest request) {
        return ApiResponse.success(adminSongService.create(admin, request));
    }
}
