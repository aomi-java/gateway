package tech.aomi.cloud.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 全局跟踪ID过滤器
 *
 * @author Sean createAt 2021/7/21
 */
@Slf4j
@Component
public class CommonGlobalFilter implements GlobalFilter, Ordered {

    private static final String ID = "logId";

    private static final String START_AT = "START_AT";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        URI uri = exchange.getRequest().getURI();
        LOGGER.debug("请求处理开始: {}, {}", start, uri);
        return chain.filter(exchange).doFinally((un) -> {
            long end = System.currentTimeMillis();
            LOGGER.debug("请求处理完成:{}, start: {}, end: {}, end - start = {}", uri, start, end, end - start);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
