# gateway

基于`Spring Cloud Gateway`封装的网关.

## 功能

- [x] 请求参数验签、响应结果加签

## 网关过滤器

### `SignGatewayFilterFactory` 验签加签网关过滤器

对请求参数验签、响应参数加签.

使用该功能需要自行实现签名服务接口`SignService`。

因为body只能读取一次的原因,验签接口中会传递请求体参数,其他参数请自行从request中获取。

响应结果签名成功后，如果签名值在body中,修改原始body,返回即可。如果是放在请求头中,直接通过response设置响应头即可.

```java
package tech.aomi.cloud.gateway.service;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import tech.aomi.common.exception.ServiceException;

/**
 * 签名服务接口
 *
 * @author Sean createAt 2021/5/8
 */
public interface SignService {

    /**
     * 签名验证
     *
     * @param request http request
     * @param body    请求body参数
     * @throws ServiceException 签名出现任何异常则认为签名验证失败
     */
    default void verify(ServerHttpRequest request, byte[] body) throws ServiceException {
        // TODO
    }

    /**
     * 对响应数据进行签名
     *
     * @param response http response
     * @param body     原始响应body
     * @return 签名完成后响应的body数据，如果没有变动，直接返回原始响应body
     */
    default byte[] sign(ServerHttpResponse response, byte[] body) {
        return body;
    }
}

```



#### Example application.yml

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: hello_sign
          uri: https://api.xxxx.com/v1
          predicates:
            - Path=/v1/**
          filters:
            - "Sign"

```