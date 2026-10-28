package com.hangajiman.auth.social;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SocialClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // 카카오·Google API 호출용. 응답이 5초 넘게 없으면 실패로 처리합니다.
    @Bean
    public RestClient socialRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return builder.requestFactory(factory).build();
    }
}
