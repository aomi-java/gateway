package tech.aomi.cloud.gateway.filter.message;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import tech.aomi.cloud.gateway.api.MessageService;

import java.util.Set;

/**
 * 报文服务网关过滤器工厂
 *
 * @author Sean createAt 2021/5/7
 */
@Slf4j
@Component
public class MessageServiceGatewayFilterFactory extends AbstractGatewayFilterFactory<MessageServiceGatewayFilterFactory.Config> {

    @Autowired
    private ServerCodecConfigurer codecConfigurer;

    @Autowired
    private Set<MessageBodyDecoder> bodyDecoders;

    @Autowired
    private Set<MessageBodyEncoder> bodyEncoders;

    @Autowired
    private MessageService messageService;

    public MessageServiceGatewayFilterFactory() {
        super(Config.class);
    }

//    @Override
//    public List<String> shortcutFieldOrder() {
//        return Collections.singletonList(KEY);
//    }

    @Override
    public GatewayFilter apply(MessageServiceGatewayFilterFactory.Config config) {
        return new MessageServiceGatewayFilter(
                messageService,
                codecConfigurer.getReaders(),
                bodyDecoders,
                bodyEncoders
        );
    }

    @Getter
    @Setter
    public static class Config {

    }
}
