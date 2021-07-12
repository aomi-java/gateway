package tech.aomi.cloud.gateway.filter.v1;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.aomi.cloud.gateway.api.ClientService;
import tech.aomi.cloud.gateway.constant.Header;
import tech.aomi.cloud.gateway.constant.MessageVersion;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.common.constant.Common;
import tech.aomi.common.constant.HttpHeader;
import tech.aomi.common.exception.ResourceNonExistException;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.exception.SignatureException;
import tech.aomi.common.utils.crypto.RSA;
import tech.aomi.common.utils.crypto.RSAUtil;
import tech.aomi.common.utils.json.Json;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.*;

/**
 * @author Sean createAt 2021/6/24
 */
@Slf4j
public class SignGatewayFilter implements GatewayFilter, Ordered {

    private final List<HttpMessageReader<?>> messageReaders;

    private final Set<MessageBodyDecoder> messageBodyDecoders;

    private final Set<MessageBodyEncoder> messageBodyEncoders;

    private final ClientService clientService;

    public SignGatewayFilter(
            List<HttpMessageReader<?>> messageReaders,
            Set<MessageBodyDecoder> messageBodyDecoders,
            Set<MessageBodyEncoder> messageBodyEncoders,
            ClientService clientService
    ) {
        this.messageReaders = messageReaders;
        this.messageBodyDecoders = messageBodyDecoders;
        this.messageBodyEncoders = messageBodyEncoders;
        this.clientService = clientService;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String agencyCode = request.getHeaders().getFirst("X-AGENCY-CODE");
        String merchantCode = request.getHeaders().getFirst("X-MERCHANT-NO");
        String sign = request.getHeaders().getFirst(HttpHeader.SIGNATURE);
        LOGGER.debug("请求用户标识: AGENCY_CODE: {}, MERCHANT_NO: {}", agencyCode, merchantCode);

        String code = "";
        if (StringUtils.isNotEmpty(agencyCode)) {
            code = agencyCode;
        } else if (StringUtils.isNotEmpty(merchantCode)) {
            code = merchantCode;
        }
        if (StringUtils.isEmpty(code)) {
            return Mono.error(new ResourceNonExistException("客户端不存在: " + code));
        }

        Client client = clientService.getClientByCode(code);
        if (null == client || StringUtils.isEmpty(client.getClientPublicKey())) {
            LOGGER.error("请求方没有配置密钥信息");
            return Mono.error(new ServiceException("请求方没有配置密钥信息"));
        }
        exchange.getAttributes().put("client", client);

        if (exchange.getRequest().getMethod() == HttpMethod.GET) {
            return handleGet(exchange, chain, client, sign);
        }

        return handleOther(exchange, chain, client, sign);
    }


    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    private Mono<Void> handleOther(ServerWebExchange exchange, GatewayFilterChain chain, Client client, String sign) {
        ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
        // TODO: flux or mono
        Mono<byte[]> modifiedBody = serverRequest.bodyToMono(byte[].class)
                .flatMap(body -> {
                    try {
                        verify(client, body, sign);
                    } catch (Exception e) {
                        LOGGER.error("签名验证失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                    return Mono.just(body);
                });


        BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.add(Header.MESSAGE_VERSION, MessageVersion.V1_0_0.getVersion());
        headers.add(Header.CLIENT_ID, client.getId());

        // the new content type will be computed by bodyInserter
        // and then set in the request decorator
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            ServerHttpRequest decorator = decorate(exchange, headers, outputMessage);
            return chain.filter(
                    exchange.mutate()
                            .request(decorator)
                            .response(new SignServerHttpResponse(
                                    exchange,
                                    messageReaders,
                                    messageBodyDecoders,
                                    messageBodyEncoders
                            ))
                            .build()
            );
        }));
    }

    private Mono<Void> handleGet(ServerWebExchange exchange, GatewayFilterChain chain, Client client, String sign) {
        ServerHttpRequest request = exchange.getRequest();

        Map<String, String> args = new HashMap<>();
        request.getQueryParams().forEach((k, v) -> {
            if (null != v && v.size() > 0) {
                try {
                    args.put(k, URLDecoder.decode(v.get(0), "UTF-8"));
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        });
        try {
            verify(client, Json.toJson(args).toString().getBytes(StandardCharsets.UTF_8), sign);
        } catch (Exception e) {
            LOGGER.error("签名校验失败: {}", e.getMessage());
            return Mono.error(e);
        }
        return chain.filter(
                exchange.mutate()
                        .request(request.mutate()
                                .header(Header.MESSAGE_VERSION, MessageVersion.V1_0_0.getVersion())
                                .header(Header.CLIENT_ID, client.getId())
                                .build())
                        .response(new SignServerHttpResponse(
                                exchange,
                                messageReaders,
                                messageBodyDecoders,
                                messageBodyEncoders
                        ))
                        .build()
        );
    }

    public void verify(Client client, byte[] body, String sign) {
        if (StringUtils.isEmpty(sign)) {
            LOGGER.error("签名信息为空");
            throw new SignatureException("签名信息为空");
        }


        String bodyStr = new String(body, StandardCharsets.UTF_8);
        LOGGER.debug("请求原始数据: [{}]", bodyStr);

        byte[] signData = SignDataUtils.strToSignText(body);
        LOGGER.debug("请求验签数据: [{}]", new String(signData));

        byte[] signBytes = Base64.getDecoder().decode(sign);

        try {
            PublicKey publicKey = RSA.parsePublicKeyWithBase64(client.getClientPublicKey());
            boolean isOk = RSAUtil.signVerify(publicKey, Common.SIGN_ALGORITHMS, signData, signBytes);
            if (isOk) {
                return;
            }
            LOGGER.error("签名错误:{}", sign);
            throw new SignatureException("签名校验失败");
        } catch (Exception e) {
            LOGGER.error("签名执行失败: {}", e.getMessage());
            throw new SignatureException("签名校验异常", e);
        }

    }

    private ServerHttpRequestDecorator decorate(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                long contentLength = headers.getContentLength();
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.putAll(headers);
                if (contentLength > 0) {
                    httpHeaders.setContentLength(contentLength);
                } else {
                    // TODO: this causes a 'HTTP/1.1 411 Length Required' // on
                    // httpbin.org
                    httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                }
                return httpHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }

}
