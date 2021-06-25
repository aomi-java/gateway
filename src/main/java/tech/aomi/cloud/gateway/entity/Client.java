package tech.aomi.cloud.gateway.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;
import java.util.Map;

/**
 * @author Sean createAt 2021/6/24
 */
@Getter
@Setter
public class Client implements java.io.Serializable {
    private static final long serialVersionUID = -3384408008530642865L;

    private String id;

    /**
     * 必须唯一
     */
    @Indexed(unique = true)
    private String code;

    private String name;

    /**
     * 请求时添加的固定头
     */
    private Map<String, String> requestHeaders;

    /**
     * 响应时添加的固定头
     */
    private Map<String, String> responseHeaders;

    /**
     * 客户端公钥
     */
    private String clientPublicKey;

    /**
     * 服务端公钥
     */
    private String publicKey;

    /**
     * 客户端私钥
     */
    private String privateKey;

    /**
     * 所属平台
     */
    private String platform;

    /**
     * 创建时间
     */
    private Date createAt;
}
