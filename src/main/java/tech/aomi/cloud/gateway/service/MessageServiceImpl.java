package tech.aomi.cloud.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import tech.aomi.cloud.gateway.GatewayProperties;
import tech.aomi.cloud.gateway.api.KeyService;
import tech.aomi.cloud.gateway.api.MessageService;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
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
    private KeyService keyService;

    @Autowired
    private GatewayProperties properties;

    @Override
    public void verify(ServerHttpRequest request, RequestMessage body) throws ServiceException {
        LOGGER.debug("请求参数签名验证: {}", body);
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
        String signDataStr = body.getTimestamp() + body.getRandomString() + StringUtils.trimToEmpty(body.getPayload());
        LOGGER.debug("请求验签数据: [{}], 客户端计算的签名: [{}]", signDataStr, body.getSign());
        byte[] signData = signDataStr.getBytes();
        byte[] signBytes = Base64.getDecoder().decode(body.getSign());

        switch (body.getSignType()) {
            case RSA:
                rsaSignVerify(body, signData, signBytes);
                break;
        }

    }

    @Override
    public byte[] modifyRequestBody(ServerHttpRequest request, MessageContext context) {
        RequestMessage message = context.getRequestMessage();
        LOGGER.debug("解密传输秘钥: {}", message.getTrk());
        byte[] trk;
        try {
            trk = RSAUtil.privateKeyDecryptWithBase64(properties.getPrivateKey(), message.getTrk());
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
        byte[] payload = aes(false, trk, payloadCiphertext.getBytes(StandardCharsets.UTF_8));
        context.setPayload(payload);
        return payload;
    }

    @Override
    public ResponseMessage modifyResponseBody(ServerHttpResponse response, MessageContext context, Result.Entity body) {
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
    public void sign(ServerHttpResponse response, MessageContext context, ResponseMessage body) {
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
        PrivateKey privateKey = null;
        try {
            privateKey = RSA.parsePrivateKeyWithBase64(properties.getPrivateKey());
        } catch (Exception e) {
            LOGGER.error("解析服务端私钥失败: {}", e.getMessage(), e);
            throw new ServiceException("解析服务端私钥失败", e);
        }

        try {
            byte[] signArr = RSAUtil.sign(privateKey, RSAUtil.SIGN_ALGORITHMS_SHA512, signData);
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
    private void rsaSignVerify(RequestMessage body, byte[] signData, byte[] signBytes) {
        String publicKeyStr = keyService.getPublicKey(body.getClientId());
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
            boolean isOk = RSAUtil.signVerify(publicKey, RSAUtil.SIGN_ALGORITHMS_SHA512, signData, signBytes);
            if (isOk) {
                LOGGER.debug("签名校验通过: RequestId: {}", body.getRequestId());
                return;
            }
            LOGGER.error("签名错误:{}", body.getSign());
        } catch (Exception e) {
            LOGGER.error("签名执行失败: {}", e.getMessage());
            throw new SignatureException("签名校验失败:" + e.getMessage(), e);
        }
    }

    private byte[] aes(boolean encrypt, byte[] key, byte[] data) {
        AesUtils.setTransformation(AesUtils.AES_CBC_PKCS5Padding);
        AesUtils.setKeyLength(256);
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
}
