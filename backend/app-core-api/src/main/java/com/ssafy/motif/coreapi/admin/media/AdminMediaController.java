package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.media.dto.PresignRequest;
import com.ssafy.motif.coreapi.admin.media.dto.PresignResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 미디어 API (어드민 명세 §3.2) — 업로드 URL 발급 하나. */
@Tag(name = "Admin Media", description = "어드민 업로드 presign (어드민 명세 §3.2)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/media")
public class AdminMediaController {

    private final AdminMediaService adminMediaService;

    @Operation(summary = "업로드 URL 발급",
            description = "kind 별 S3 프리픽스·허용 Content-Type·크기 상한을 서명에 박아 발급한다.")
    @PostMapping("/presign")
    public ApiResponse<PresignResponse> presign(@Valid @RequestBody PresignRequest request) {
        return ApiResponse.success(adminMediaService.presign(request));
    }
}
