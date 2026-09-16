package com.ssafy.motif.notification.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class FcmConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(NotificationProperties properties) throws IOException {
        Path path = credentialsPath(properties);
        try (InputStream credentials = Files.newInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
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
