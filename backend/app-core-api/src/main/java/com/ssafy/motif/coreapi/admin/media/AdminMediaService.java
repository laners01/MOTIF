package com.ssafy.motif.coreapi.admin.media;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.dto.PresignRequest;
import com.ssafy.motif.coreapi.admin.media.dto.PresignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Collection;
import java.util.List;
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
 *
 * <p>업로드가 실제로 끝났는지는 {@link #requireUploaded} 가 HEAD 로 확인한다 —
 * presign 은 "올려도 된다"는 허가일 뿐 "올렸다"는 증거가 아니다.
 */
@Slf4j
@Service
public class AdminMediaService {

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final AdminMediaProperties properties;
    private final String bucket;

    public AdminMediaService(S3Presigner presigner, S3Client s3Client, AdminMediaProperties properties,
                             @Value("${motif.media.s3.bucket}") String bucket) {
        this.presigner = presigner;
        this.s3Client = s3Client;
        this.properties = properties;
        this.bucket = bucket;
    }

    /**
     * 키가 전부 S3 에 올라와 있는지 확인한다 (명세 §1.4 검증 1항 · §3.7.2 검증 4항).
     *
     * <p>부재 키를 <b>전부 모아</b> 한 번에 던진다 — 편집본 매트릭스는 키가 여러 개라
     * 하나씩 알려 주면 어드민이 같은 폼을 여러 번 제출하게 된다. {@code data} 의
     * {@code missingKeys} 를 화면이 그대로 표시한다.
     *
     * <p>이미지 키도 여기로 보낸다. 명세 §5 는 이미지 부재에 {@code IMAGE_NOT_UPLOADED}
     * 를 배정했지만 그것은 공지·프로필 등 기존 이미지 경로 얘기고, §3.7 실패표는
     * 편집본 첫 장면을 포함해 {@code MEDIA_NOT_UPLOADED} 하나로 적었다.
     */
    public void requireUploaded(Collection<String> keys) {
        // ponytail: 키마다 순차 HEAD. n=4 면 1 + 2*15 = 31 회 — 어드민 등록은 초당 1건도
        // 아니라 충분하다. 체감될 만큼 느려지면 그때 병렬로 돌린다
        List<String> missing = keys.stream().distinct().filter(key -> !exists(key)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(AdminErrorCode.MEDIA_NOT_UPLOADED,
                    "업로드되지 않은 키: " + missing, Map.of("missingKeys", missing));
        }
    }

    /**
     * 키 하나의 존재 여부. 실패 코드가 경로마다 다를 때 쓴다 — 공지 이미지는 §5 가
     * {@code IMAGE_NOT_UPLOADED} 를 배정했고, 챌린지 등록은 §3.7 실패표대로
     * {@code MEDIA_NOT_UPLOADED} 하나로 모은다({@link #requireUploaded}).
     *
     * <p>403 도 "없음"으로 본다 — 버킷에 {@code s3:ListBucket} 이 없으면 S3 는 존재하지 않는
     * 키에 404 대신 403 을 준다. 어느 쪽이든 등록을 진행할 수 없는 상태는 같다.
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(request -> request.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                return false;
            }
            throw e;
        }
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
