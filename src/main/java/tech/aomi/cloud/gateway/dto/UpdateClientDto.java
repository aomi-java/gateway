package tech.aomi.cloud.gateway.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author Sean createAt 2021/6/25
 */
@Getter
@Setter
public class UpdateClientDto implements java.io.Serializable {

    private static final long serialVersionUID = 1811085515771997070L;

    private String id;

    private String code;

    private String name;

    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;

    /**
     * 客户端公钥
     */
    private String clientPublicKey;

    private String publicKey;
    private String privateKey;

}
