package tech.aomi.cloud.gateway.filter.transform;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.aomi.cloud.gateway.api.ClientService;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.constant.Header;
import tech.aomi.cloud.gateway.constant.MessageVersion;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.filter.v1.SignDataUtils;
import tech.aomi.common.constant.HttpHeader;
import tech.aomi.common.exception.ResourceNonExistException;
import tech.aomi.common.utils.json.Json;

import java.net.URI;
import java.util.Optional;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 转换内部的请求参数为网关标准的请求参数，并转发到目标地址
 *
 * @author Sean createAt 2021/7/5
 */
@Slf4j
@Component
public class TransformGatewayFilterFactory extends AbstractGatewayFilterFactory<TransformGatewayFilterFactory.Config> {

    @Autowired
    private ClientService clientService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ServerCodecConfigurer codecConfigurer;

    public TransformGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            //请求头
            HttpHeaders headers = request.getHeaders();
            // 报文版本
            String messageVersion = Optional.ofNullable(headers.getFirst(Header.MESSAGE_VERSION)).orElse(MessageVersion.LATEST.getVersion());
            MessageVersion version = Optional.ofNullable(MessageVersion.of(messageVersion)).orElse(MessageVersion.LATEST);

            switch (version) {
                case V1_0_0:
                    return version100(exchange, chain);
                default:
                    return versionLatest(exchange, chain);
            }

        };
    }

    private Mono<Void> versionLatest(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        //请求头
        HttpHeaders headers = request.getHeaders();
        //请求方法
        HttpMethod method = Optional.ofNullable(request.getMethod()).orElse(HttpMethod.GET);
        //请求参数
        MultiValueMap<String, String> queryParams = request.getQueryParams();

        // 目标地址
        String uriStr = Optional.ofNullable(headers.getFirst(Header.TARGET_HEADER_KEY)).orElseThrow(() -> new IllegalArgumentException("目标地址不能为空"));

        // 目标客户端ID
        String clientId = Optional.ofNullable(headers.getFirst(Header.CLIENT_ID)).orElseThrow(() -> new IllegalArgumentException("目标客户端ID不能为空"));

        // 目标客户端
        Client client = Optional.ofNullable(clientService.getClient(clientId)).orElseThrow(() -> new ResourceNonExistException("目标客户端不存在"));

        // 当route
        Route route = (Route) Optional.ofNullable(exchange.getAttribute(GATEWAY_ROUTE_ATTR)).orElseThrow(() -> new RuntimeException("无法获取路由信息"));


        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.putAll(headers);
        newHeaders.remove(Header.TARGET_HEADER_KEY);
        newHeaders.remove(Header.CLIENT_ID);


        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uriStr);
        uriBuilder.queryParams(queryParams);

        URI uri;
        if (method == HttpMethod.GET) {
            String payload = Json.toJson(queryParams).toString();
            RequestMessage body = messageService.createRequestMessage(client, payload);
            uriBuilder.queryParam("requestId", body.getRequestId());
            uriBuilder.queryParam("clientId", body.getClientId());
            uriBuilder.queryParam("trk", body.getTrk());
            uriBuilder.queryParam("timestamp", body.getTimestamp());
            uriBuilder.queryParam("randomString", body.getRandomString());
            uriBuilder.queryParam("payload", body.getPayload());
            uriBuilder.queryParam("charset", body.getCharset());
            uriBuilder.queryParam("signType", body.getSignType());
            uriBuilder.queryParam("sign", body.getSign());
            uri = uriBuilder.build().toUri();

            //替换新的url地址
            ServerHttpRequest serverHttpRequest = request.mutate()
                    .uri(uri)
                    .method(method)
                    .headers(httpHeaders -> httpHeaders.addAll(newHeaders)).build();

            //从新设置Route地址
            Route newRoute = Route.async()
                    .asyncPredicate(route.getPredicate())
                    .filters(route.getFilters())
                    .id(route.getId())
                    .order(route.getOrder()).uri(uri).build();
            exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, newRoute);

            return chain.filter(exchange.mutate().request(serverHttpRequest).build());

        } else {
            uri = uriBuilder.build().toUri();
            ServerRequest serverRequest = ServerRequest.create(exchange, codecConfigurer.getReaders());
            // TODO: flux or mono
            Mono<String> modifiedBody = serverRequest.bodyToMono(String.class)
                    .flatMap(body -> {
                        try {
                            RequestMessage requestMessage = messageService.createRequestMessage(client, body);
                            return Mono.just(Json.toJson(requestMessage).toString());
                        } catch (Exception e) {
                            LOGGER.error("请求参数处理失败: {}", e.getMessage());
                            return Mono.error(e);
                        }
                    });
            BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);

            // the new content type will be computed by bodyInserter
            // and then set in the request decorator
            newHeaders.remove(HttpHeaders.CONTENT_LENGTH);

            CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, newHeaders);

            //从新设置Route地址
            Route newRoute = Route.async()
                    .asyncPredicate(route.getPredicate())
                    .filters(route.getFilters())
                    .id(route.getId())
                    .order(route.getOrder()).uri(uri).build();
            exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, newRoute);

            return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
                ServerHttpRequest decorator = decorate(exchange, newHeaders, outputMessage);
                // 替换新地址
                decorator = decorator.mutate().uri(uri).method(method).build();
                return chain.filter(
                        exchange.mutate()
                                .request(decorator)
                                .build()
                );
            }));
        }
    }

    private Mono<Void> version100(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        //请求头
        HttpHeaders headers = request.getHeaders();
        //请求方法
        HttpMethod method = Optional.ofNullable(request.getMethod()).orElse(HttpMethod.GET);
        //请求参数
        MultiValueMap<String, String> queryParams = request.getQueryParams();

        // 目标地址
        String uriStr = Optional.ofNullable(headers.getFirst(Header.TARGET_HEADER_KEY)).orElseThrow(() -> new IllegalArgumentException("目标地址不能为空"));

        // 目标客户端ID
        String clientId = Optional.ofNullable(headers.getFirst(Header.CLIENT_ID)).orElseThrow(() -> new IllegalArgumentException("目标客户端ID不能为空"));

        // 目标客户端
        Client client = Optional.ofNullable(clientService.getClient(clientId)).orElseThrow(() -> new ResourceNonExistException("目标客户端不存在"));

        // 当route
        Route route = (Route) Optional.ofNullable(exchange.getAttribute(GATEWAY_ROUTE_ATTR)).orElseThrow(() -> new RuntimeException("无法获取路由信息"));

        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.putAll(headers);
        newHeaders.remove(Header.TARGET_HEADER_KEY);
        newHeaders.remove(Header.CLIENT_ID);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uriStr);
        uriBuilder.queryParams(queryParams);


        URI uri = uriBuilder.build().toUri();
        ServerRequest serverRequest = ServerRequest.create(exchange, codecConfigurer.getReaders());
        // TODO: flux or mono
        Mono<byte[]> modifiedBody = serverRequest.bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        JsonNode data = Json.parse(body);
                        String sign = SignDataUtils.getSign(data, client.getPrivateKey());
                        newHeaders.add(HttpHeader.SIGNATURE, sign);
                        return Mono.just(SignDataUtils.getSignText(data));
                    } catch (Exception e) {
                        LOGGER.error("请求参数处理失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
        BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);

        // the new content type will be computed by bodyInserter
        // and then set in the request decorator
        newHeaders.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, newHeaders);

        //从新设置Route地址
        Route newRoute = Route.async()
                .asyncPredicate(route.getPredicate())
                .filters(route.getFilters())
                .id(route.getId())
                .order(route.getOrder()).uri(uri).build();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, newRoute);

        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            ServerHttpRequest decorator = decorate(exchange, newHeaders, outputMessage);
            // 替换新地址
            decorator = decorator.mutate().uri(uri).method(method).build();
            return chain.filter(
                    exchange.mutate()
                            .request(decorator)
                            .build()
            );
        }));
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

    @Getter
    @Setter
    public static class Config {

    }
}
