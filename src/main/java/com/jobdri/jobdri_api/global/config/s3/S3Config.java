package com.jobdri.jobdri_api.global.config.s3;


import io.lettuce.core.StaticCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.swing.plaf.synth.Region;

@Configuration
public class S3Config {
    // application.yml (또는 .properties) 에서 설정한 값들을 가져옵니다.
    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    // AmazonS3Client 객체를 생성하여 Bean으로 등록합니다.
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }
}
