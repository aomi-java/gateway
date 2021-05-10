package tech.aomi.cloud.gateway.filter.sign;

import lombok.extern.slf4j.Slf4j;
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
import tech.aomi.cloud.gateway.sign.SignService;

import java.util.List;
import java.util.Set;

/**
 * @author Sean createAt 2021/5/10
 */
@Slf4j
public class SignGatewayFilter implements GatewayFilter, Ordered {

    private final List<HttpMessageReader<?>> messageReaders;

    private final Set<MessageBodyDecoder> messageBodyDecoders;

    private final Set<MessageBodyEncoder> messageBodyEncoders;

    private final SignService signService;

    public SignGatewayFilter(
            SignService signService,
            List<HttpMessageReader<?>> messageReaders,
            Set<MessageBodyDecoder> messageBodyDecoders,
            Set<MessageBodyEncoder> messageBodyEncoders
    ) {
        this.signService = signService;
        this.messageReaders = messageReaders;
        this.messageBodyDecoders = messageBodyDecoders;
        this.messageBodyEncoders = messageBodyEncoders;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (request.getMethod() == HttpMethod.GET) {
            try {
                signService.verify(request, null);
            } catch (Exception e) {
                LOGGER.error("签名校验失败: {}", e.getMessage(), e);
                return Mono.error(e);
            }
            return chain.filter(
                    exchange.mutate()
                            .request(request)
                            .response(new ResponseSignServerHttpResponse(
                                    signService,
                                    exchange,
                                    messageReaders,
                                    messageBodyDecoders,
                                    messageBodyEncoders
                            ))
                            .build()
            );
        }

        ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
        // TODO: flux or mono
        Mono<byte[]> modifiedBody = serverRequest.bodyToMono(byte[].class)
                .flatMap(body -> {
                    try {
                        signService.verify(request, null);
                    } catch (Exception e) {
                        LOGGER.error("签名校验失败: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                    return Mono.just(body);
                });


        BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());

        // the new content type will be computed by bodyInserter
        // and then set in the request decorator
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            ServerHttpRequest decorator = decorate(exchange, headers, outputMessage);
            return chain.filter(
                    exchange.mutate()
                            .request(decorator)
                            .response(new ResponseSignServerHttpResponse(
                                    signService,
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
