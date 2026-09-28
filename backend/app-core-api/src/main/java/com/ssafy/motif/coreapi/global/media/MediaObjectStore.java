package com.ssafy.motif.coreapi.global.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

/**
 * S3 오브젝트 원시 연산 — <b>서명 발급과 존재 확인</b>.
 *
 * <p><b>정책이 없다.</b> 무엇을 어디에 올릴 수 있는지(종류·한도·키 규약)와 실패를 어느
 * 코드로 되돌릴지는 부르는 쪽이 정한다 — 어드민(어드민 §1.4)과 팬 프로필(API §4.3)이
 * 같은 버킷에 쓰면서 실패 코드가 다르기 때문이다. 여기 정책을 넣으면 두 계약이 한
 * 클래스 안에서 섞인다.
 *
 * <p><b>바이트를 다루지 않는다</b>(설계 원칙 ①). 서명은 로컬 계산이고 HEAD 는 메타데이터
 * 조회다. 오브젝트 본문 입출력은 app-media 의 몫이고, core-api 에서 유일한 예외가
 * {@link MediaPlaylistService} 의 재생목록 읽기(수 KB 텍스트)다.
 */
@Slf4j
@Component
public class MediaObjectStore {

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final String bucket;

    public MediaObjectStore(S3Presigner presigner, S3Client s3Client,
                            @Value("${motif.media.s3.bucket}") String bucket) {
        this.presigner = presigner;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * presigned PUT URL 발급 — 바이트는 클라이언트 → S3 로 직행한다(Valet Key).
     *
     * <p><b>허용 조건을 서명에 박는다.</b> {@code contentType}·{@code contentLength} 를
     * {@link PutObjectRequest} 에 넣으면 <b>서명 대상 헤더</b>가 되어, 클라이언트가 다른
     * 값으로 PUT 하면 S3 가 거부한다. 서버는 바이트를 보지 못하므로 이게 유일한 강제
     * 수단이다 — 빼면 발급된 URL 로 아무거나 아무 크기로 올릴 수 있고, 사후에 확인할
     * 방법도 없다.
     *
     * <p><b>발급됐다고 권한이 있는 것이 아니다.</b> 서명은 권한을 검사하지 않는다 —
     * 권한이 0이어도 URL 은 정상 발급되고 로그도 정상으로 보인다. 실제 PUT 에서
     * {@code AccessDenied} 로 드러난다({@code backend/CLAUDE.md} "IMDS 를 믿지 마라").
     */
    public String presignPut(String key, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        return presigner.presignPutObject(b -> b
                        .signatureDuration(ttl)
                        .putObjectRequest(put))
                .url()
                .toString();
    }

    /**
     * 키 하나의 존재 여부 (HEAD).
     *
     * <p><b>403 을 "없음"으로 삼키지 않는다.</b> 버킷에 {@code s3:ListBucket} 이 없으면
     * S3 가 없는 키에 404 대신 403 을 준다. 예전에는 그것까지 {@code false} 로 접었는데,
     * 그러면 <b>권한 설정이 틀린 것이 "파일이 안 올라왔다"로 위장한다</b> — 올린 사람은
     * 멀쩡히 올린 파일을 두고 업로드를 반복하게 되고 로그에도 단서가 없다. 정상적인
     * 미업로드와 응답이 완전히 같기 때문이다(2026-09-14 에 반나절을 태웠다).
     *
     * <p>그래서 404 만 "없음"이고 나머지는 그대로 던진다 → 500 + S3 메시지가 로그에 남는다.
     * <b>전제는 자격증명에 버킷 단위 {@code s3:ListBucket} 이 있다는 것</b>이다
     * ({@code arn:aws:s3:::motif-media} — 뒤에 {@code /*} 를 붙이면 조용히 무효다).
     * 그게 빠지면 정상적인 미업로드까지 500 이 되는데, <b>조용히 틀리는 것보다 시끄럽게
     * 틀리는 것이 고칠 수 있다.</b>
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(request -> request.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("S3 HEAD 실패 — 권한 설정을 의심해라: key={} status={} code={}",
                    key, e.statusCode(), e.awsErrorDetails() == null ? null : e.awsErrorDetails().errorCode());
            throw e;
        }
    }
}
