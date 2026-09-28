package com.ssafy.motif.media.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bucket 산출물 버킷
 * @param region S3 리전
 */
@ConfigurationProperties(prefix = "motif.media.s3")
public record MediaStorageProperties(String bucket, String region) {
}
