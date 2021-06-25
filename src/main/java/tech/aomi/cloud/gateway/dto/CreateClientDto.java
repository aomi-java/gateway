package tech.aomi.cloud.gateway.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * @author Sean createAt 2021/6/25
 */
@Getter
@Setter
public class CreateClientDto implements java.io.Serializable {

    private static final long serialVersionUID = 5714089793031687051L;

    @NotEmpty
    private String code;

    @NotEmpty
    private String name;

    private Map<String, String> requestHeaders;
    private Map<String, String> responseHeaders;

    /**
     * 客户端公钥
     */
    private String clientPublicKey;

    private String publicKey;
    private String privateKey;

    @NotEmpty
    private String platform;
}
