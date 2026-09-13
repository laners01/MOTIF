package com.ssafy.motif.coreapi.admin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 업로드 완료 확인 — {@code HEAD s3://{bucket}/{key}} (어드민 명세 §1.4 검증 1항).
 *
 * <p>바이트는 브라우저 → S3 로 직행하므로(원칙 ①) 서버는 키만 받는다. 어드민이
 * presign 만 받고 PUT 을 끝내지 않은 채 폼을 제출하면 <b>없는 파일을 가리키는 행</b>이
 * 남으므로, 등록 트랜잭션 진입 전에 존재만 확인한다. 메타데이터만 읽고 본문은 받지
 * 않아 원칙 ① 과 어긋나지 않는다.
 *
 * <p>실패 코드는 부르는 쪽이 정한다 — 영상은 {@code MEDIA_NOT_UPLOADED}, 이미지는
 * {@code IMAGE_NOT_UPLOADED} 로 갈린다(§5). 여기서는 존재 여부만 답한다.
 *
 * <p>{@link NoSuchKeyException} 외의 S3 오류(권한·네트워크)는 <b>삼키지 않는다</b> —
 * 그 경우 "업로드되지 않았다"가 아니라 서버 장애이고, 409 로 바꿔 보내면 어드민이
 * 멀쩡한 파일을 다시 올리며 헤맨다.
 */
@Component
public class MediaObjectChecker {

    private final S3Client s3Client;
    private final String bucket;

    public MediaObjectChecker(S3Client s3Client, @Value("${motif.media.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public boolean exists(String key) {
        try {
            s3Client.headObject(request -> request.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
