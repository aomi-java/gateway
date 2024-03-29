package tech.aomi.cloud.gateway.filter.message;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.common.web.controller.Result;

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
public class MessageServiceServerHttpResponse extends ServerHttpResponseDecorator {

    private final List<HttpMessageReader<?>> messageReaders;

    private final Map<String, MessageBodyDecoder> messageBodyDecoders;

    private final Map<String, MessageBodyEncoder> messageBodyEncoders;

    private final ServerWebExchange exchange;

    private final MessageService messageService;

    public MessageServiceServerHttpResponse(
            MessageService messageService,
            ServerWebExchange exchange,
            List<HttpMessageReader<?>> messageReaders,
            Set<MessageBodyDecoder> messageBodyDecoders,
            Set<MessageBodyEncoder> messageBodyEncoders
    ) {
        super(exchange.getResponse());
        this.messageService = messageService;
        this.exchange = exchange;
        this.messageReaders = messageReaders;

        this.messageBodyDecoders = messageBodyDecoders.stream().collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
        this.messageBodyEncoders = messageBodyEncoders.stream().collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));

    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {

        ServerHttpResponse response = exchange.getResponse();
        HttpStatus httpStatus = response.getStatusCode();

        if (null == httpStatus ||
                !httpStatus.is2xxSuccessful() ||
                httpStatus.is3xxRedirection() ||
                response.getHeaders().containsValue(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        ) {
            return super.writeWith(body);
        }

        MessageContext messageContext = exchange.getAttribute(MessageContext.MESSAGE_CONTEXT);

        String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);

        HttpHeaders httpHeaders = new HttpHeaders();
        // explicitly add it in this way instead of
        // 'httpHeaders.setContentType(originalResponseContentType)'
        // this will prevent exception in case of using non-standard media
        // types like "Content-Type: image"
        httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);

        ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

        // TODO: flux or mono
        Mono<ResponseMessage> modifiedBody = extractBody(clientResponse, Result.Entity.class)
                .flatMap(originalBody -> {
                    try {
                        ResponseMessage message = messageService.modifyResponseBody(exchange, messageContext, originalBody);
                        messageService.sign(exchange, messageContext, message);
                        return Mono.just(message);
                    } catch (Exception e) {
                        LOGGER.error("响应结果处理失败: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                });

        BodyInserter<Mono<ResponseMessage>, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromPublisher(modifiedBody, ResponseMessage.class);

        httpHeaders = getHeaders();
        httpHeaders.addAll(messageService.getResponseHeaders(messageContext));

        CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, httpHeaders);
        return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
            Mono<DataBuffer> messageBody = writeBody(outputMessage, ResponseMessage.class);
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
