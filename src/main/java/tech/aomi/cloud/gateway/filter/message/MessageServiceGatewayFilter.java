package tech.aomi.cloud.gateway.filter.message;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.common.utils.json.Json;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报文服务网关过滤器
 *
 * @author Sean createAt 2021/5/10
 */
@Slf4j
public class MessageServiceGatewayFilter implements GatewayFilter, Ordered {

    private final List<HttpMessageReader<?>> messageReaders;

    private final Set<MessageBodyDecoder> messageBodyDecoders;

    private final Set<MessageBodyEncoder> messageBodyEncoders;

    private final MessageService messageService;

    public MessageServiceGatewayFilter(
            MessageService messageService,
            List<HttpMessageReader<?>> messageReaders,
            Set<MessageBodyDecoder> messageBodyDecoders,
            Set<MessageBodyEncoder> messageBodyEncoders
    ) {
        this.messageService = messageService;
        this.messageReaders = messageReaders;
        this.messageBodyDecoders = messageBodyDecoders;
        this.messageBodyEncoders = messageBodyEncoders;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        MessageContext context = new MessageContext();
        exchange.getAttributes().put(MessageContext.MESSAGE_CONTEXT, context);

        ServerHttpRequest request = exchange.getRequest();

        if (request.getMethod() == HttpMethod.GET) {
            return get(exchange, chain, context);
        }

        ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
        // TODO: flux or mono
        Mono<byte[]> modifiedBody = serverRequest.bodyToMono(RequestMessage.class)
                .flatMap(body -> {
                    try {
                        messageService.init(context, body);
                        messageService.verify(exchange, context);
                    } catch (Exception e) {
                        LOGGER.error("签名验证失败: {}", e.getMessage());
                        return Mono.error(e);
                    }
                    context.setRequestMessage(body);
                    byte[] newBody = messageService.modifyRequestBody(exchange, context);
                    return Mono.just(newBody);
                }).switchIfEmpty(Mono.error(new IllegalArgumentException("Required request body is missing")));


        BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());

        // the new content type will be computed by bodyInserter
        // and then set in the request decorator
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            ServerHttpRequest decorator = decorate(exchange, headers, outputMessage, context);
            return chain.filter(
                    exchange.mutate()
                            .request(decorator)
                            .response(new MessageServiceServerHttpResponse(
                                    messageService,
                                    exchange,
                                    messageReaders,
                                    messageBodyDecoders,
                                    messageBodyEncoders
                            ))
                            .build()
            );
        }));
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }


    private Mono<Void> get(ServerWebExchange exchange, GatewayFilterChain chain, MessageContext context) {
        ServerHttpRequest request = exchange.getRequest();
        RequestMessage body = new RequestMessage(request.getQueryParams());
        messageService.init(context, body);

        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.headers(httpHeaders -> httpHeaders.putAll(messageService.getRequestHeaders(context)));

        try {
            messageService.verify(exchange, context);
        } catch (Exception e) {
            LOGGER.error("签名校验失败: {}", e.getMessage());
            return Mono.error(e);
        }
        byte[] newBody = messageService.modifyRequestBody(exchange, context);
        String newBodyStr = new String(newBody, body.charset());

        if (StringUtils.isNotEmpty(newBodyStr)) {
            URI uri = exchange.getRequest().getURI();

            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
            Map<String, String> urlArgs = Json.fromJson(newBodyStr, new TypeReference<Map<String, String>>() {
            });
            urlArgs.forEach(builder::queryParam);
            URI newUri = builder.build(true).toUri();
            requestBuilder.uri(newUri);
        }

        return chain.filter(
                exchange.mutate()
                        .request(requestBuilder.build())
                        .response(new MessageServiceServerHttpResponse(
                                messageService,
                                exchange,
                                messageReaders,
                                messageBodyDecoders,
                                messageBodyEncoders
                        ))
                        .build()
        );
    }

    private ServerHttpRequestDecorator decorate(ServerWebExchange exchange, HttpHeaders headers, CachedBodyOutputMessage outputMessage, MessageContext context) {
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
                httpHeaders.addAll(messageService.getRequestHeaders(context));
                return httpHeaders;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return outputMessage.getBody();
            }
        };
    }
}
