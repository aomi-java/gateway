package tech.aomi.cloud.gateway.api;

/**
 * 秘钥服务
 *
 * @author Sean createAt 2021/6/22
 */
public interface KeyService {

    /**
     * 获取
     *
     * @param clientId 客户端ID
     * @return 客户端公钥
     */
    String getPublicKey(String clientId);

}
