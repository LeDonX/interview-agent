package com.cloud.alibaba.ai.example.claw.skillsagentexample;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class ClawAgentExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClawAgentExampleApplication.class, args);
    }

    /*@Bean
    public DashScopeApi dashScopeApi(DashScopeConnectionProperties connectionProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(60 * 1000);  // 连接超时 60 秒
        requestFactory.setReadTimeout(10 * 60 * 1000); // 读取超时 5 分钟
        return DashScopeApi.builder()
                .apiKey(connectionProperties.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
    }*/
}
