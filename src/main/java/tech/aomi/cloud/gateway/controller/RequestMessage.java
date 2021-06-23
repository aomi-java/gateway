package tech.aomi.cloud.gateway.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 请求报文体
 *
 * @author Sean createAt 2021/6/22
 */
@Getter
@Setter
@ToString
public class RequestMessage implements java.io.Serializable {

    private static final long serialVersionUID = -6173282298420535916L;

    /**
     * 请求唯一标识
     */
    private String requestId;

    /**
     * 分配给客户端的唯一ID
     */
    private String clientId;

    /**
     * 使用公钥加密后的传输秘钥、Base64编码
     * 请求时,使用服务端的公钥加密
     * 响应时,使用客户端的公钥加密
     */
    private String trk;

    /**
     * 发送请求的时间
     */
    private String timestamp;

    /**
     * 随机字符串
     */
    private String randomString;

    /**
     * 报文数据
     * 数据原始格式: JSON 格式字符串
     * 使用传输秘钥对原始数据(JSON 格式字符串)加密
     * Base64编码
     */
    private String payload;

    /**
     * 请求参数编码格式
     */
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * 签名方式
     */
    private SignType signType = SignType.RSA;

    /**
     * 签名原始数据=发送请求时间+随机字符串+报文数据(加密后)
     * 报文签名、Base64编码
     * 请求时、客户端使用自身秘钥计算签名
     * 响应时、服务端使用自身秘钥计算签名
     */
    private String sign;

}
