package tech.aomi.cloud.gateway.exception;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.common.exception.ErrorCode;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.utils.json.Json;
import tech.aomi.common.web.controller.Result;

import java.util.Map;
import java.util.Optional;

/**
 * 全局异常处理
 *
 * @author Sean createAt 2021/5/8
 */
@Slf4j
@Setter
public class GlobalErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler {

    private MessageService messageService;

    public GlobalErrorWebExceptionHandler(ErrorAttributes errorAttributes, WebProperties.Resources resources, ErrorProperties errorProperties, ApplicationContext applicationContext) {
        super(errorAttributes, resources, errorProperties, applicationContext);
        messageService = applicationContext.getBean(MessageService.class);
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    @Override
    protected Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable t = getError(request);
        LOGGER.error("请求处理异常: {}", request.exchange().getRequest().getURI(), t);
        if (t instanceof ResponseStatusException) {
            return super.getErrorAttributes(request, options);
        }

        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setTimestamp(messageService.timestamp());
        responseMessage.setRandomString(messageService.randomString());
        responseMessage.setCharset(messageService.charset().name());
        responseMessage.setSuccess(false);
        ServiceException se;
        if (t instanceof ServiceException) {
            se = (ServiceException) t;
        } else if (t instanceof IllegalArgumentException) {
            se = new ServiceException(t.getMessage(), t);
            se.setErrorCode(ErrorCode.PARAMS_ERROR);
        } else {
            se = new ServiceException(t);
        }

        Result result = Result.create(se.getErrorCode(), se.getMessage(), se.getPayload());
        Result.Entity body = result.getBody();
        Optional.ofNullable(body).ifPresent((tmp) -> {
            responseMessage.setSuccess(tmp.getSuccess());
            responseMessage.setStatus(tmp.getStatus());
            responseMessage.setDescribe(tmp.getDescribe());
            Optional.ofNullable(tmp.getPayload()).ifPresent((p) -> responseMessage.setPayload(Json.toJson(p).toString()));
        });

        Map<String, Object> resultMap = responseMessage.toMap();
        resultMap.put("code", result.getStatusCode().value());
        resultMap.put("GlobalErrorWebExceptionHandler", true);
        return resultMap;
    }



    /**
     * 根据code获取对应的HttpStatus
     *
     * @param errorAttributes errorAttributes
     */
    @Override
    protected int getHttpStatus(Map<String, Object> errorAttributes) {
        if (null == errorAttributes.remove("GlobalErrorWebExceptionHandler")) {
            return super.getHttpStatus(errorAttributes);
        }
        return (int) errorAttributes.remove("code");
    }

}
