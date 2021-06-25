package tech.aomi.cloud.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import tech.aomi.cloud.gateway.api.ClientService;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.constant.Header;
import tech.aomi.cloud.gateway.constant.MessageVersion;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.filter.message.MessageContext;
import tech.aomi.common.constant.Common;
import tech.aomi.common.exception.ErrorCode;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.exception.SignatureException;
import tech.aomi.common.utils.crypto.AesUtils;
import tech.aomi.common.utils.crypto.RSA;
import tech.aomi.common.utils.crypto.RSAUtil;
import tech.aomi.common.utils.json.Json;
import tech.aomi.common.web.controller.Result;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * 签名验证服务
 *
 * @author Sean createAt 2021/6/22
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private ClientService clientService;

    @Override
    public void init(MessageContext context, RequestMessage body) {
        if (StringUtils.isAnyEmpty(
                body.getClientId(),
                body.getTrk(),
                body.getTimestamp(),
                body.getRandomString(),
                body.getSign())) {

            ServiceException e = new ServiceException("请求参数不正确,必填参数有NULL值. 请检查: clientId、trk、timestamp、randomString、sign是否已经正确填写");
            e.setErrorCode(ErrorCode.PARAMS_ERROR);
            throw e;
        }

        Client client = clientService.getClient(body.getClientId());
        if (null == client) {
            LOGGER.error("客户端没有配置秘钥相关信息: {}", body.getClientId());
            throw new ServiceException("客户端没有配置秘钥相关信息: " + body.getClientId());
        }

        if (StringUtils.isEmpty(client.getClientPublicKey())) {
            LOGGER.error("客户端没有配置公钥: {}, {}", client.getId(), client.getCode());
            throw new ServiceException("客户端公钥未配置: " + client.getCode());
        }

        context.setClient(client);
        context.setRequestMessage(body);
    }

    @Override
    public HttpHeaders getRequestHeaders(MessageContext context) {
        HttpHeaders headers = new HttpHeaders();
        setCommonHeader(headers);
        if (!CollectionUtils.isEmpty(context.getClient().getRequestHeaders())) {
            context.getClient().getRequestHeaders().forEach(headers::add);
        }
        return headers;
    }

    @Override
    public HttpHeaders getResponseHeaders(MessageContext context) {
        HttpHeaders headers = new HttpHeaders();
        setCommonHeader(headers);
        if (!CollectionUtils.isEmpty(context.getClient().getResponseHeaders())) {
            context.getClient().getResponseHeaders().forEach(headers::add);
        }
        return headers;
    }

    @Override
    public void verify(ServerWebExchange exchange, MessageContext context) throws ServiceException {
        RequestMessage body = context.getRequestMessage();
        LOGGER.debug("请求参数签名验证: {}", body);
        String signDataStr = body.getTimestamp() + body.getRandomString() + StringUtils.trimToEmpty(body.getPayload());
        LOGGER.debug("请求验签数据: [{}], 客户端计算的签名: [{}]", signDataStr, body.getSign());
        byte[] signData = signDataStr.getBytes();
        byte[] signBytes = Base64.getDecoder().decode(body.getSign());

        switch (body.getSignType()) {
            case RSA:
                rsaSignVerify(context.getClient(), body, signData, signBytes);
                break;
        }

    }

    @Override
    public byte[] modifyRequestBody(ServerWebExchange exchange, MessageContext context) {
        Client client = context.getClient();
        RequestMessage message = context.getRequestMessage();
        LOGGER.debug("解密传输秘钥: {}", message.getTrk());
        byte[] trk;
        try {
            trk = RSAUtil.privateKeyDecryptWithBase64(client.getPrivateKey(), message.getTrk());
            context.setTrk(trk);
        } catch (Exception e) {
            LOGGER.error("解密传输秘钥失败: {}", e.getMessage(), e);
            ServiceException se = new ServiceException("使用服务端私钥解密传输秘钥失败", e);
            se.setErrorCode(ErrorCode.PARAMS_ERROR);
            throw se;
        }

        String payloadCiphertext = message.getPayload();
        LOGGER.debug("解密请求参数: {}", payloadCiphertext);
        if (StringUtils.isEmpty(payloadCiphertext)) {
            LOGGER.info("明文为空,不需要解密");
            return new byte[0];
        }
        byte[] payload = aes(false, trk, Base64.getDecoder().decode(payloadCiphertext));
        context.setPayload(payload);
        return payload;
    }

    @Override
    public ResponseMessage modifyResponseBody(ServerWebExchange exchange, MessageContext context, Result.Entity body) {
        ResponseMessage message = new ResponseMessage();
        message.setTimestamp(DateFormatUtils.format(new Date(), Common.DATETIME_FORMAT));
        message.setRandomString(UUID.randomUUID().toString().replaceAll("-", ""));
        message.setSuccess(body.getSuccess());
        message.setStatus(body.getStatus());

        if (null != body.getPayload()) {
            String payloadStr = Json.toJson(body.getPayload()).toString();
            LOGGER.debug("加密响应参数: {}", payloadStr);
            byte[] payload = aes(true, context.getTrk(), payloadStr.getBytes(StandardCharsets.UTF_8));
            message.setPayload(Base64.getEncoder().encodeToString(payload));
        }

        message.setDescribe(body.getDescribe());
        return message;
    }

    @Override
    public void sign(ServerWebExchange exchange, MessageContext context, ResponseMessage body) {
        LOGGER.debug("响应参数签名计算: {}", body);
        body.setSignType(context.getRequestMessage().getSignType());

        String signDataStr = body.getTimestamp() + body.getRandomString() + StringUtils.trimToEmpty(body.getPayload());
        byte[] signData = signDataStr.getBytes();

        String sign = "";
        switch (body.getSignType()) {
            case RSA:
                sign = rsaSign(context, signData);
                break;
        }
        LOGGER.debug("响应签名数据: [{}], 签名: [{}]", signDataStr, sign);
        body.setSign(sign);
    }

    private String rsaSign(MessageContext context, byte[] signData) {
        Client client = context.getClient();
        String privateKeyStr = client.getPrivateKey();
        PrivateKey privateKey = null;
        try {
            privateKey = RSA.parsePrivateKeyWithBase64(privateKeyStr);
        } catch (Exception e) {
            LOGGER.error("解析服务端私钥失败: {}", e.getMessage(), e);
            throw new ServiceException("解析服务端私钥失败", e);
        }

        try {
            byte[] signArr = RSAUtil.sign(
                    privateKey,
                    tech.aomi.cloud.gateway.constant.Common.RSA_SIGN_ALGORITHM,
                    signData
            );
            return Base64.getEncoder().encodeToString(signArr);
        } catch (Exception e) {
            LOGGER.error("响应参数计算签名失败: {}", e.getMessage(), e);
            throw new ServiceException("响应参数计算签名失败", e);
        }
    }

    /**
     * RSA 签名验证
     *
     * @param body      请求参数
     * @param signData  验签数据
     * @param signBytes 客户端计算的签名
     */
    private void rsaSignVerify(Client client, RequestMessage body, byte[] signData, byte[] signBytes) {
        String publicKeyStr = client.getClientPublicKey();
        if (StringUtils.isEmpty(publicKeyStr)) {
            LOGGER.error("获取客户端公钥失败: {}", body.getClientId());
            throw new SignatureException("获取客户端公钥失败:" + body.getClientId());
        }

        PublicKey publicKey;
        try {
            publicKey = RSA.parsePublicKeyWithBase64(publicKeyStr);
        } catch (Exception e) {
            throw new SignatureException("客户端公钥格式不正确,无法解析:" + body.getClientId());
        }

        try {
            boolean isOk = RSAUtil.signVerify(
                    publicKey,
                    tech.aomi.cloud.gateway.constant.Common.RSA_SIGN_ALGORITHM,
                    signData,
                    signBytes
            );
            if (isOk) {
                LOGGER.debug("签名校验通过: RequestId: {}", body.getRequestId());
                return;
            }
            LOGGER.error("签名错误:{}", body.getSign());
            throw new SignatureException("签名错误");
        } catch (Exception e) {
            LOGGER.error("签名执行失败: {}", e.getMessage());
            throw new SignatureException("签名校验异常:" + e.getMessage(), e);
        }
    }

    private byte[] aes(boolean encrypt, byte[] key, byte[] data) {
        AesUtils.setTransformation(tech.aomi.cloud.gateway.constant.Common.AES_TRANSFORMATION);
        AesUtils.setKeyLength(tech.aomi.cloud.gateway.constant.Common.AES_KEY_LENGTH);
        try {
            if (encrypt) {
                return AesUtils.encrypt(key, data);
            } else {
                return AesUtils.decrypt(key, data);
            }
        } catch (Exception e) {
            LOGGER.error("使用传输秘钥加解密失败: {}", e.getMessage());
            ServiceException se = new ServiceException("使用传输秘钥加解密失败", e);
            se.setErrorCode(ErrorCode.PARAMS_ERROR);
            throw se;
        }
    }

    private void setCommonHeader(HttpHeaders headers) {
        headers.add(Header.MESSAGE_VERSION, MessageVersion.LATEST.getVersion());
    }
}
