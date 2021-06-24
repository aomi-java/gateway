package tech.aomi.cloud.gateway.filter.v1;

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
import tech.aomi.cloud.gateway.api.ClientService;

import java.util.Set;

/**
 * @author Sean createAt 2021/6/24
 */
@Slf4j
@Component
public class SignGatewayFilterFactory extends AbstractGatewayFilterFactory<SignGatewayFilterFactory.Config> {

    @Autowired
    private ServerCodecConfigurer codecConfigurer;

    @Autowired
    private Set<MessageBodyDecoder> bodyDecoders;

    @Autowired
    private Set<MessageBodyEncoder> bodyEncoders;

    @Autowired
    private ClientService clientService;

    public SignGatewayFilterFactory() {
        super(SignGatewayFilterFactory.Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new SignGatewayFilter(
                codecConfigurer.getReaders(),
                bodyDecoders,
                bodyEncoders,
                clientService
        );
    }

    @Getter
    @Setter
    public static class Config {

    }


}
