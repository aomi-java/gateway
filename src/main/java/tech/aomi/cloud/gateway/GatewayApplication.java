package tech.aomi.cloud.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tech.aomi.cloud.gateway.sign.SignService;

/**
 * @author Sean createAt 2021/5/7
 */
@SpringBootApplication
public class GatewayApplication {

    @Bean
    @ConditionalOnMissingBean
    public SignService signService() {
        return new SignService() {
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}