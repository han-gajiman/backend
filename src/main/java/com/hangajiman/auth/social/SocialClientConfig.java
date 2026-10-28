package com.hangajiman.auth.social;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SocialClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // 외부 API(카카오·Google·Expo 푸시) 호출용. 응답이 5초 넘게 없으면 실패로 처리합니다.
    // 본문을 버퍼링해서 Content-Length를 붙여 보냅니다 (나눠 보내기(chunked)를 안 받는 서버 대비, 본문이 작아 부담 없음).
    @Bean
    public RestClient socialRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return builder.requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }
}
