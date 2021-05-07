package tech.aomi.cloud.gateway.filter.sign;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Sean createAt 2021/5/7
 */
@Slf4j
@Component
public class SignGatewayFilterFactory extends AbstractGatewayFilterFactory<SignGatewayFilterFactory.Config> implements ApplicationContextAware {

    private static final String KEY = "keyServiceBeanName";

    private ApplicationContext applicationContext;

    private final List<HttpMessageReader<?>> messageReaders;


    public SignGatewayFilterFactory() {
        super(Config.class);
        ModifyRequestBodyGatewayFilterFactory a;
        this.messageReaders = HandlerStrategies.withDefaults().messageReaders();

    }

    public SignGatewayFilterFactory(List<HttpMessageReader<?>> messageReaders) {
        super(Config.class);
        this.messageReaders = messageReaders;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.singletonList(KEY);
    }

    @Override
    public GatewayFilter apply(SignGatewayFilterFactory.Config config) {
        return (exchange, chain) -> {
            ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
            // TODO: flux or mono
            Mono<String> modifiedBody = serverRequest.bodyToMono(String.class)
                    .flatMap(body -> {
                        LOGGER.debug("请求参数: [{}]", body);
                        return Mono.just(body);
                    });


            BodyInserter<Mono<String>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, String.class);
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(exchange.getRequest().getHeaders());

            // the new content type will be computed by bodyInserter
            // and then set in the request decorator
            headers.remove(HttpHeaders.CONTENT_LENGTH);

            CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

            return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
                ServerHttpRequest decorator = decorate(exchange, headers, outputMessage);
                return chain.filter(exchange.mutate().request(decorator).build()).then(Mono.fromRunnable(() -> {
                    LOGGER.debug("请求处理完成");
                }));
            }));
        };
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void verify(String body) {

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

        private String keyServiceBeanName;

    }
}
