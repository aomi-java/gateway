package tech.aomi.cloud.gateway.api;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.cloud.gateway.filter.sign.MessageContext;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.web.controller.Result;

/**
 * 报文服务接口
 *
 * @author Sean createAt 2021/5/8
 */
public interface MessageService {

    /**
     * 请求参数签名验证
     *
     * @param request http request
     * @param body    请求body参数
     * @throws ServiceException 签名出现任何异常则认为签名验证失败
     */
    void verify(ServerHttpRequest request, RequestMessage body) throws ServiceException;

    /**
     * 修改原始请求数据为后端应用需要的数据
     * 同时把明文传输秘钥保存到context中
     * 同时把明文请求报文保存到payload中
     *
     * @param request http request
     * @param context 报文上下文
     * @return 后端服务需要的数据
     */
    byte[] modifyRequestBody(ServerHttpRequest request, MessageContext context);

    /**
     * 转换原始响应的数据为标准响应格式的数据
     *
     * @param response http response
     * @param context  报文上下文
     * @return 响应给客户端的数据
     */
    ResponseMessage modifyResponseBody(ServerHttpResponse response, MessageContext context, Result.Entity body);

    /**
     * 响应数据进行签名
     *
     * @param response     http response
     * @param context      报文上下文
     * @param responseBody 响应参数
     */
    void sign(ServerHttpResponse response, MessageContext context, ResponseMessage responseBody);


}
