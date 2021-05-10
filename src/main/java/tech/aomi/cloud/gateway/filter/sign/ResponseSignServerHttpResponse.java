package tech.aomi.cloud.gateway.filter.sign;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tech.aomi.cloud.gateway.sign.SignService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * @author Sean createAt 2021/5/9
 */
@Slf4j
public class ResponseSignServerHttpResponse extends ServerHttpResponseDecorator {

    private final List<HttpMessageReader<?>> messageReaders;

    private final Map<String, MessageBodyDecoder> messageBodyDecoders;

    private final Map<String, MessageBodyEncoder> messageBodyEncoders;

    private final ServerWebExchange exchange;

    private final SignService signService;

    public ResponseSignServerHttpResponse(
            SignService signService,
            ServerWebExchange exchange,
            List<HttpMessageReader<?>> messageReaders,
            Set<MessageBodyDecoder> messageBodyDecoders,
            Set<MessageBodyEncoder> messageBodyEncoders
    ) {
        super(exchange.getResponse());
        this.signService = signService;
        this.exchange = exchange;
        this.messageReaders = messageReaders;

        this.messageBodyDecoders = messageBodyDecoders.stream().collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
        this.messageBodyEncoders = messageBodyEncoders.stream().collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));

    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);

        HttpHeaders httpHeaders = new HttpHeaders();
        // explicitly add it in this way instead of
        // 'httpHeaders.setContentType(originalResponseContentType)'
        // this will prevent exception in case of using non-standard media
        // types like "Content-Type: image"
        httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);

        ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

        // TODO: flux or mono
        Mono<byte[]> modifiedBody = extractBody(clientResponse, byte[].class)
                .flatMap(originalBody -> {
                    try {
                        return Mono.just(signService.sign(this, originalBody));
                    } catch (Exception e) {
                        LOGGER.error("响应结果签名失败: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                });

        BodyInserter<Mono<byte[]>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, byte[].class);
        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, getHeaders());
        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            Mono<DataBuffer> messageBody = writeBody(outputMessage, byte[].class);
            HttpHeaders headers = getHeaders();
            if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING) || headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
                messageBody = messageBody.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
            }
            // TODO: fail if isStreamingMediaType?
            return getDelegate().writeWith(messageBody);
        }));
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return writeWith(Flux.from(body).flatMapSequential(p -> p));
    }

    private ClientResponse prepareClientResponse(Publisher<? extends DataBuffer> body, HttpHeaders httpHeaders) {
        ClientResponse.Builder builder;
        builder = ClientResponse.create(getStatusCode(), messageReaders);
        return builder.headers(headers -> headers.putAll(httpHeaders)).body(Flux.from(body)).build();
    }

    private <T> Mono<T> extractBody(ClientResponse clientResponse, Class<T> inClass) {
        // if inClass is byte[] then just return body, otherwise check if
        // decoding required
//        if (byte[].class.isAssignableFrom(inClass)) {
//            return clientResponse.bodyToMono(inClass);
//        }

        List<String> encodingHeaders = getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
        for (String encoding : encodingHeaders) {
            MessageBodyDecoder decoder = messageBodyDecoders.get(encoding);
            if (decoder != null) {
                return clientResponse.bodyToMono(byte[].class)
                        .publishOn(Schedulers.parallel())
                        .map(decoder::decode)
                        .map(bytes -> bufferFactory().wrap(bytes))
                        .map(buffer -> prepareClientResponse(Mono.just(buffer), getHeaders()))
                        .flatMap(response -> response.bodyToMono(inClass));
            }
        }

        return clientResponse.bodyToMono(inClass);
    }

    private Mono<DataBuffer> writeBody(CachedBodyOutputMessage message, Class<?> outClass) {
        Mono<DataBuffer> response = DataBufferUtils.join(message.getBody());
//        if (byte[].class.isAssignableFrom(outClass)) {
//            return response;
//        }

        List<String> encodingHeaders = getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
        for (String encoding : encodingHeaders) {
            MessageBodyEncoder encoder = messageBodyEncoders.get(encoding);
            if (encoder != null) {
                DataBufferFactory dataBufferFactory = bufferFactory();
                response = response.publishOn(Schedulers.parallel()).map(buffer -> {
                    byte[] encodedResponse = encoder.encode(buffer);
                    DataBufferUtils.release(buffer);
                    return encodedResponse;
                }).map(dataBufferFactory::wrap);
                break;
            }
        }

        return response;
    }
}
