package tech.aomi.cloud.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Sean createAt 2021/6/23
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /**
     * 服务端公钥
     */
    private String publicKey;
    /**
     * 服务端私钥
     */
    private String privateKey;

}
