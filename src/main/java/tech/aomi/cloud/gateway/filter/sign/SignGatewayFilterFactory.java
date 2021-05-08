package tech.aomi.cloud.gateway.filter.sign;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.aomi.cloud.gateway.sign.SignService;
import tech.aomi.common.exception.ServiceException;

import java.util.Collections;
import java.util.List;

/**
 * @author Sean createAt 2021/5/7
 */
@Slf4j
@Component
public class SignGatewayFilterFactory extends AbstractGatewayFilterFactory<SignGatewayFilterFactory.Config> implements ApplicationContextAware {

    private static final String KEY = "signServiceBeanName";

    private ApplicationContext applicationContext;

    private final List<HttpMessageReader<?>> messageReaders;


    public SignGatewayFilterFactory() {
        super(Config.class);
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
            exchange.getAttributes().put(Common.NEED_SIGN, true);
            exchange.getAttributes().put(Common.SIGN_SERVICE_BEAN_NAME, config.getSignServiceBeanName());

            ServerHttpRequest request = exchange.getRequest();

            if (request.getMethod() == HttpMethod.GET) {
                verify(config.getSignServiceBeanName(), request, null);
                return chain.filter(exchange.mutate().build());
            }

            ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);
            // TODO: flux or mono
            Mono<byte[]> modifiedBody = serverRequest.bodyToMono(byte[].class)
                    .flatMap(body -> {
                        try {
                            verify(config.getSignServiceBeanName(), exchange.getRequest(), body);
                        } catch (Exception e) {
                            LOGGER.error("签名校验失败: {}", e.getMessage(), e);
                            return Mono.error(e);
                        }
                        return Mono.just(body);
                    });


            BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);
            HttpHeaders headers = new HttpHeaders();
            headers.add(Common.NEED_SIGN, "true");
            headers.putAll(exchange.getRequest().getHeaders());

            // the new content type will be computed by bodyInserter
            // and then set in the request decorator
            headers.remove(HttpHeaders.CONTENT_LENGTH);

            CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);

            return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
                ServerHttpRequest decorator = decorate(exchange, headers, outputMessage);
                return chain.filter(exchange.mutate().request(decorator).build());
            }));
        };
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void verify(String signServiceBeanName, ServerHttpRequest request, byte[] body) throws ServiceException {
        if (!StringUtils.hasLength(signServiceBeanName)) {
            LOGGER.warn("没有配置SignService实例Bean名称,无法进行签名验证,请提供tech.aomi.cloud.gateway.sign.SignService实现");
            throw new ServiceException("没有配置SignService实例");
        }
//        try {
//            SignService signService = applicationContext.getBean(signServiceBeanName, SignService.class);
//            signService.verify(request, body);
//        } catch (ServiceException e) {
//            throw e;
//        } catch (Throwable t) {
//            throw new ServiceException(t.getMessage(), t);
//        }
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

        private String signServiceBeanName;

    }
}
