package tech.aomi.cloud.gateway.sign;

import org.springframework.http.server.reactive.ServerHttpRequest;
import tech.aomi.common.exception.ServiceException;

/**
 * 签名服务接口
 *
 * @author Sean createAt 2021/5/8
 */
public interface SignService {

    /**
     * 签名验证
     *
     * @param request http request
     * @param body    请求body参数
     * @throws ServiceException 签名出现任何异常则认为签名验证失败
     */
    void verify(ServerHttpRequest request, byte[] body) throws ServiceException;

    String sign();
}
