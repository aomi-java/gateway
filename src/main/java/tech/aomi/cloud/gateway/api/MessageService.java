package tech.aomi.cloud.gateway.api;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.cloud.gateway.controller.SignType;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.filter.message.MessageContext;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.web.controller.Result;

import java.nio.charset.Charset;

/**
 * 报文服务接口
 *
 * @author Sean createAt 2021/5/8
 */
public interface MessageService {

    /**
     * 创建一个请求ID
     */
    String requestId();

    /**
     * 创建一个随机数
     */
    String randomString();

    /**
     * 创建一个当前时间戳
     */
    String timestamp();

    /**
     * 创建一个传输秘钥
     */
    byte[] trk();

    /**
     * 创建默认的charset
     */
    Charset charset();

    /**
     * 返回起那么类型
     */
    SignType signType();

    /**
     * 获取签名数据
     *
     * @param requestMessage 报文体
     * @return 用于签名的数据
     */
    byte[] getSignData(RequestMessage requestMessage);

    /**
     * 获取签名数据
     *
     * @param responseMessage 报文体
     * @return 用于签名的数据
     */
    byte[] getSignData(ResponseMessage responseMessage);

    /**
     * 创建请求参数
     *
     * @param client  客户端信息
     * @param payload 报文
     * @return 完成报文体
     */
    RequestMessage createRequestMessage(Client client, String payload);

    /**
     * 初始化报文上下文
     * 1. 参数校验
     * 2. 初始化数据
     */
    void init(MessageContext context, RequestMessage body);

    HttpHeaders getRequestHeaders(MessageContext context);

    HttpHeaders getResponseHeaders(MessageContext context);


    /**
     * 修改原始请求数据为后端应用需要的数据
     * 同时把明文传输秘钥保存到context中
     * 同时把明文请求报文保存到payload中
     *
     * @param request http request
     * @param context 报文上下文
     * @return 后端服务需要的数据
     */
    byte[] modifyRequestBody(ServerWebExchange exchange, MessageContext context);

    /**
     * 转换原始响应的数据为标准响应格式的数据
     *
     * @param exchange exchange
     * @param context  报文上下文
     * @return 响应给客户端的数据
     */
    ResponseMessage modifyResponseBody(ServerWebExchange exchange, MessageContext context, Result.Entity body);

    /**
     * 响应数据进行签名
     *
     * @param exchange     exchange
     * @param context      报文上下文
     * @param responseBody 响应参数
     */
    void sign(ServerWebExchange exchange, MessageContext context, ResponseMessage responseBody);

    /**
     * 签名
     *
     * @param signType 签名方式
     * @param baseKey  签名秘钥 Base64格式
     * @param signData 待签名数据
     * @return 签名结果
     */
    String sign(SignType signType, String baseKey, byte[] signData);

    /**
     * 请求参数签名验证
     *
     * @param exchange exchange
     * @param context  报文上下文
     * @throws ServiceException 签名出现任何异常则认为签名验证失败
     */
    void verify(ServerWebExchange exchange, MessageContext context) throws ServiceException;

    /**
     * 签名验证
     *
     * @param signType 签名方式
     * @param baseKey  签名秘钥 Base64格式
     * @param signData 验签数据
     * @param sign     签名
     * @return sign 是否正确
     */
    boolean verify(SignType signType, String baseKey, byte[] signData, String sign);

}
