package tech.aomi.cloud.gateway.controller;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.MultiValueMap;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 请求报文体
 *
 * @author Sean createAt 2021/6/22
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
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
     * AES 秘钥、长度128位
     */
    private String trk;

    /**
     * 发送请求的时间
     * 格式: yyyy-MM-dd HH:mm:ss.SSS
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

    public RequestMessage(MultiValueMap<String, String> args) {
        Optional.ofNullable(args.getFirst("charset")).ifPresent(charset -> this.charset = Charset.forName(charset));
        Optional.ofNullable(args.getFirst("requestId")).ifPresent(requestId -> this.requestId = urlDecode(requestId));
        Optional.ofNullable(args.getFirst("clientId")).ifPresent(clientId -> this.clientId = urlDecode(clientId));
        Optional.ofNullable(args.getFirst("trk")).ifPresent(trk -> this.trk = urlDecode(trk));
        Optional.ofNullable(args.getFirst("timestamp")).ifPresent(timestamp -> this.timestamp = urlDecode(timestamp));
        Optional.ofNullable(args.getFirst("randomString")).ifPresent(randomString -> this.randomString = urlDecode(randomString));
        Optional.ofNullable(args.getFirst("payload")).ifPresent(payload -> this.payload = urlDecode(payload));
        Optional.ofNullable(args.getFirst("signType")).ifPresent(signType -> this.signType = SignType.valueOf(signType));
        Optional.ofNullable(args.getFirst("sign")).ifPresent(sign -> this.sign = urlDecode(sign));
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, Optional.ofNullable(getCharset()).orElse(StandardCharsets.UTF_8).name());
        } catch (Exception ignored) {
        }
        return value;
    }
}
