package com.colla.platform.shared.storage;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    MinioClient minioClient(StorageProperties properties) {
        return MinioClient.builder()
            .endpoint(properties.getEndpoint())
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .build();
    }

    @Bean
    @ConditionalOnRuntimeRole({RuntimeRole.MAINTENANCE, RuntimeRole.COMBINED})
    ApplicationRunner minioBucketInitializer(MinioClient minioClient, StorageProperties properties) {
        return args -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("maintenance_command command=minio-bucket-initialize result=created bucket={}", properties.getBucket());
                return;
            }
            log.info("maintenance_command command=minio-bucket-initialize result=skipped reason=bucket-exists bucket={}", properties.getBucket());
        };
    }
}
