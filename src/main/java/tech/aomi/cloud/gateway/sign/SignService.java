package tech.aomi.cloud.gateway.sign;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
    default void verify(ServerHttpRequest request, byte[] body) throws ServiceException {
        // TODO
    }

    /**
     * 对响应数据进行签名
     *
     * @param response http response
     * @param body     原始响应body
     * @return 签名完成后响应的body数据，如果没有变动，直接返回原始响应body
     */
    default byte[] sign(ServerHttpResponse response, byte[] body) {
        return body;
    }
}
