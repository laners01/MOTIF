package com.ssafy.motif.notification.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FCM HTTP v1 자격증명 (§14).
 *
 * <p><b>레거시 서버 키는 쓸 수 없다</b> — 2024 에 종료됐다. 서비스 계정 JSON 으로
 * OAuth 토큰을 받아 보내는 방식만 남아 있고, firebase-admin 이 그 갱신까지 한다.
 *
 * <p><b>키가 없으면 기동에 실패한다.</b> 조용히 뜨게 두면 발송이 전부 실패하는데 그
 * 사실이 런타임 로그로만 드러난다 — 배포는 성공했다고 표시되고 알림만 안 간다.
 * 설정 누락은 기동 시점에 크게 실패하는 편이 낫다(ADR-0004 placeholder 규율).
 *
 * <p><b>{@code local} 프로파일은 예외다.</b> 이 키는 운영 k8s Secret 에만 있어야 하는데,
 * 없으면 기동이 막히니 알림 쪽을 건드리는 사람마다 운영 키를 노트북에 받아두게 된다 —
 * 그러면 fail-fast 로 얻은 것보다 잃는 것이 크다. 격리 모드(ADR-0003)에서는 이 설정을
 * 통째로 건너뛰고 {@link com.ssafy.motif.notification.push.FcmSender} 가 발송만 생략한다.
 * Liquibase·리스너·팬아웃 조회는 그대로 돌아 로컬 테스트가 막히지 않는다.
 *
 * <p>{@code local-remote} 는 제외하지 않는다 — 실제 인프라에 붙여 보는 모드라 발송까지
 * 확인하려는 의도이고, 그때는 키를 갖고 있는 것이 맞다.
 */
@Slf4j
@Configuration
@Profile("!local")
public class FcmConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(NotificationProperties properties) throws IOException {
        Path path = credentialsPath(properties);
        try (InputStream in = Files.newInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    // projectId 를 명시한다. 빼면 FirebaseOptions.getProjectId() 가 null 이고
                    // 발송 경로(/v1/projects/{projectId}/messages:send)는 자격증명에서 다시
                    // 유추한다 — 동작은 하지만 로그에 project=null 이 찍혀, 발송이 실패했을 때
                    // "자격증명을 못 읽었나" 로 헛다리를 짚게 된다
                    .setProjectId(projectId(credentials, path))
                    .build();
            // 같은 이름으로 두 번 초기화하면 던진다. 컨텍스트가 두 번 뜨는 테스트를
            // 위해 이미 있으면 재사용한다
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("FCM 자격증명 로드: project={}", app.getOptions().getProjectId());
            return FirebaseMessaging.getInstance(app);
        }
    }

    /**
     * 서비스 계정 JSON 의 {@code project_id}.
     *
     * <p>파일이 서비스 계정이 아니면(사용자 자격증명·ADC 등) 여기서 막는다. 그런 파일로도
     * {@code FirebaseApp} 은 만들어지지만 <b>첫 발송에서</b> 실패한다 — 기동 때 크게
     * 실패하는 편이 낫다.
     */
    private static String projectId(GoogleCredentials credentials, Path path) {
        if (credentials instanceof ServiceAccountCredentials serviceAccount
                && serviceAccount.getProjectId() != null) {
            return serviceAccount.getProjectId();
        }
        throw new IllegalStateException(
                "서비스 계정 JSON 이 아니다 — project_id 를 읽지 못했다: " + path.toAbsolutePath());
    }

    private static Path credentialsPath(NotificationProperties properties) {
        String configured = properties.fcmCredentialsPath();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "motif.notification.fcm-credentials-path 가 비어 있다 — 서비스 계정 JSON 이 없으면 FCM 발송이 전부 실패한다");
        }
        Path path = Path.of(configured);
        if (!Files.isReadable(path)) {
            // Secret 을 마운트했다고 믿지 말 것 — envFrom·volume 은 파드 생성 시점에
            // 한 번 해석되므로 Secret 을 patch 했으면 rollout restart 까지 해야 한다
            throw new IllegalStateException("FCM 서비스 계정 JSON 을 읽을 수 없다: " + path.toAbsolutePath());
        }
        return path;
    }
}
