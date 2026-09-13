package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.dto.PresignRequest;
import com.ssafy.motif.coreapi.admin.media.dto.PresignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Map;
import java.util.UUID;

/**
 * presigned PUT 발급 (어드민 명세 §3.2) — S15P21D104-588.
 *
 * <p>바이트는 브라우저 → S3 로 직행하고 서버는 키만 받는다(Valet Key · Claim-Check).
 * 어드민 업로드 전 과정(챌린지 원본·편집본·에셋·프로필·공지·배너)이 이 한 API 를 쓴다.
 *
 * <p><b>허용 조건은 서명에 박는다.</b> {@code Content-Type} 과 {@code Content-Length} 를
 * {@link PutObjectRequest} 에 넣으면 서명 대상 헤더가 되어, 클라이언트가 다른 값으로
 * PUT 하면 S3 가 거부한다 — 앱에서 한 번 더 확인할 필요가 없고 확인할 방법도 없다
 * (바이트가 서버를 통과하지 않는다).
 */
@Slf4j
@Service
public class AdminMediaService {

    private final S3Presigner presigner;
    private final AdminMediaProperties properties;
    private final String bucket;

    public AdminMediaService(S3Presigner presigner, AdminMediaProperties properties,
                             @Value("${motif.media.s3.bucket}") String bucket) {
        this.presigner = presigner;
        this.properties = properties;
        this.bucket = bucket;
    }

    public PresignResponse presign(PresignRequest request) {
        AdminMediaProperties.Upload limits = limitsFor(request.kind());
        verify(request, limits);

        String objectKey = request.kind().objectKey(UUID.randomUUID().toString(), request.challengeSeq());
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(request.contentType())
                .contentLength(request.contentLength())
                .build();
        String uploadUrl = presigner.presignPutObject(b -> b
                        .signatureDuration(limits.presignTtl())
                        .putObjectRequest(put))
                .url()
                .toString();

        log.info("presign 발급: kind={} key={} size={}", request.kind(), objectKey, request.contentLength());
        return new PresignResponse(uploadUrl, objectKey, limits.presignTtl().toSeconds());
    }

    private AdminMediaProperties.Upload limitsFor(AdminMediaKind kind) {
        return kind.category() == AdminMediaKind.Category.VIDEO ? properties.video() : properties.image();
    }

    /** 형식·크기 위반은 한 코드로 묶는다 — 명세 §3.2 실패 표가 그렇게 적혀 있다. */
    private void verify(PresignRequest request, AdminMediaProperties.Upload limits) {
        long maxBytes = limits.maxSize().toBytes();
        if (!limits.contentTypes().contains(request.contentType())) {
            throw new BusinessException(AdminErrorCode.MEDIA_TYPE_NOT_ALLOWED,
                    "허용되지 않는 Content-Type: kind=%s contentType=%s"
                            .formatted(request.kind(), request.contentType()),
                    Map.of("allowedContentTypes", limits.contentTypes()));
        }
        if (request.contentLength() > maxBytes) {
            throw new BusinessException(AdminErrorCode.MEDIA_TYPE_NOT_ALLOWED,
                    "크기 상한 초과: kind=%s contentLength=%d max=%d"
                            .formatted(request.kind(), request.contentLength(), maxBytes),
                    Map.of("maxBytes", maxBytes));
        }
    }
}
