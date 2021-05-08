package tech.aomi.cloud.gateway.filter.sign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author Sean createAt 2021/5/8
 */
@Slf4j
@Component
public class ResponseBodySignFilter implements GlobalFilter, Ordered, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean needSign = exchange.getAttributeOrDefault(Common.NEED_SIGN, false);
        if (!needSign) {
            LOGGER.debug("接口不需要生成签名数据");
            return chain.filter(exchange);
        }
        String signServiceBeanName = exchange.getAttributeOrDefault(Common.SIGN_SERVICE_BEAN_NAME, "");
        if (!StringUtils.hasLength(signServiceBeanName)) {
            LOGGER.warn("没有配置SignService实例Bean名称,无法进行签名验证,请提供tech.aomi.cloud.gateway.sign.SignService实现");
            return chain.filter(exchange);
        }
        LOGGER.debug("响应结果签名");
        return chain.filter(exchange.mutate().build());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
