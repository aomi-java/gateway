package tech.aomi.cloud.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tech.aomi.common.exception.ErrorCode;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.web.controller.Result;

/**
 * 全局异常处理
 *
 * @author Sean createAt 2021/5/8
 */
@Slf4j
@Order(-1)
@Configuration
@ConditionalOnMissingBean(value = ErrorWebExceptionHandler.class)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 设置返回JSON
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (ex instanceof ResponseStatusException) {
            response.setStatusCode(((ResponseStatusException) ex).getStatus());
        }

        Result result;
        if (ex instanceof ServiceException) {
            ServiceException e = (ServiceException) ex;
            result = Result.create(e.getErrorCode(), e.getMessage(), e.getPayload());
        } else {
            result = Result.create(ErrorCode.EXCEPTION, ex.getMessage(), null);
        }


        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory bufferFactory = response.bufferFactory();
            //返回响应结果
            try {
                return bufferFactory.wrap(mapper.writeValueAsBytes(result.getBody()));
            } catch (JsonProcessingException e) {
                LOGGER.error("JSON输出异常: {}", e.getMessage(), e);
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }
}
