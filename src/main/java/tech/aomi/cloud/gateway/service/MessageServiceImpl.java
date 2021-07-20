package tech.aomi.cloud.gateway.service;

import lombok.SneakyThrows;
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
import tech.aomi.cloud.gateway.constant.Common;
import tech.aomi.cloud.gateway.constant.Header;
import tech.aomi.cloud.gateway.constant.MessageVersion;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.cloud.gateway.controller.SignType;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.filter.message.MessageContext;
import tech.aomi.common.exception.ErrorCode;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.exception.SignatureException;
import tech.aomi.common.utils.crypto.AesUtils;
import tech.aomi.common.utils.crypto.RSA;
import tech.aomi.common.utils.crypto.RSAUtil;
import tech.aomi.common.utils.json.Json;
import tech.aomi.common.web.controller.Result;

import java.nio.charset.Charset;
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
    public String requestId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public String randomString() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public String timestamp() {
        return DateFormatUtils.format(new Date(), Common.TIMESTAMP_FORMAT);
    }

    @Override
    @SneakyThrows
    public byte[] trk() {
        return AesUtils.generateKey(Common.AES_KEY_LENGTH);
    }

    @Override
    public Charset charset() {
        return StandardCharsets.UTF_8;
    }

    @Override
    public SignType signType() {
        return SignType.RSA;
    }

    @Override
    public byte[] getSignData(RequestMessage body) {
        String signData = body.getTimestamp() + body.getRandomString() + StringUtils.trimToEmpty(body.getPayload());
        LOGGER.debug("请求签名数据: [{}]", signData);
        return signData.getBytes(body.charset());
    }

    @Override
    public byte[] getSignData(ResponseMessage body) {
        String signData = body.getTimestamp() + body.getRandomString() + StringUtils.trimToEmpty(body.getPayload());
        LOGGER.debug("响应签名数据: [{}]", signData);
        return signData.getBytes(body.charset());
    }

    @Override
    @SneakyThrows
    public RequestMessage createRequestMessage(Client client, String payload) {

        byte[] trk = trk();
        String ciphertextTrk = RSAUtil.publicKeyEncryptWithBase64(client.getClientPublicKey(), trk);

        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setRequestId(requestId());
        requestMessage.setClientId(client.getId());
        requestMessage.setTrk(ciphertextTrk);
        requestMessage.setTimestamp(timestamp());
        requestMessage.setRandomString(randomString());
//        requestMessage.setCharset(charset());
        requestMessage.setSignType(signType());

        if (StringUtils.isNotEmpty(payload)) {
            byte[] ciphertextPayload = aes(true, trk, payload.getBytes(requestMessage.getCharset()));
            String ciphertextPayloadStr = Base64.getEncoder().encodeToString(ciphertextPayload);
            requestMessage.setPayload(ciphertextPayloadStr);
        }
        byte[] signData = getSignData(requestMessage);
        String sign = sign(requestMessage.getSignType(), client.getPrivateKey(), signData);
        requestMessage.setSign(sign);

        return requestMessage;
    }


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
        headers.add(Header.CLIENT_ID, context.getClient().getId());
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

        RequestMessage requestMessage = context.getRequestMessage();

        ResponseMessage message = new ResponseMessage();
        message.setTimestamp(timestamp());
        message.setRandomString(randomString());
        message.setSuccess(body.getSuccess());
        message.setStatus(body.getStatus());

        message.setRequestId(requestMessage.getRequestId());
        message.setCharset(requestMessage.getCharset());
        message.setSignType(requestMessage.getSignType());

        if (null != body.getPayload()) {
            String payloadStr = Json.toJson(body.getPayload()).toString();
            LOGGER.debug("加密响应参数: {}", payloadStr);
            byte[] payload = aes(true, context.getTrk(), payloadStr.getBytes(message.charset()));
            message.setPayload(Base64.getEncoder().encodeToString(payload));
        }

        message.setDescribe(body.getDescribe());
        return message;
    }

    @Override
    public void sign(ServerWebExchange exchange, MessageContext context, ResponseMessage body) {
        LOGGER.debug("响应参数签名计算: {}", body);
        body.setSignType(context.getRequestMessage().getSignType());

        byte[] signData = getSignData(body);

        Client client = context.getClient();
        String privateKeyStr = client.getPrivateKey();

        String sign = sign(body.getSignType(), privateKeyStr, signData);
        LOGGER.debug("签名方式: [{}], 签名: [{}]", body.getSignType(), sign);
        body.setSign(sign);
    }

    @Override
    public String sign(SignType signType, String baseKey, byte[] signData) {
        String sign = "";
        switch (signType) {
            case RSA:
                sign = rsaSign(baseKey, signData);
                break;
        }
        return sign;
    }

    @Override
    public void verify(ServerWebExchange exchange, MessageContext context) throws ServiceException {
        RequestMessage body = context.getRequestMessage();
        LOGGER.debug("请求参数签名验证: {}", body);
        byte[] signData = getSignData(body);

        boolean isOk = false;
        switch (body.getSignType()) {
            case RSA:
                String pk = context.getClient().getClientPublicKey();
                isOk = rsaVerify(pk, signData, body.getSign());
                break;
        }
        if (isOk) {
            LOGGER.debug("签名校验通过: RequestId: {}", body.getRequestId());
            return;
        }
        LOGGER.error("签名校验失败: {}", body.getSign());
        throw new SignatureException("签名校验失败");
    }

    @Override
    public boolean verify(SignType signType, String baseKey, byte[] signData, String sign) {
        boolean isOk = false;
        switch (signType) {
            case RSA:
                isOk = rsaVerify(baseKey, signData, sign);
                break;
        }
        return isOk;
    }


    private String rsaSign(String key, byte[] signData) {
        PrivateKey privateKey;
        try {
            privateKey = RSA.parsePrivateKeyWithBase64(key);
        } catch (Exception e) {
            LOGGER.error("解析私钥失败: {}", e.getMessage(), e);
            throw new ServiceException("解析私钥失败", e);
        }

        try {
            byte[] signArr = RSAUtil.sign(
                    privateKey,
                    Common.RSA_SIGN_ALGORITHM,
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
     * @return 签名是否正确
     */
    private boolean rsaVerify(String publicKeyStr, byte[] signData, String sign) {
        PublicKey publicKey;
        try {
            publicKey = RSA.parsePublicKeyWithBase64(publicKeyStr);
        } catch (Exception e) {
            LOGGER.error("公钥格式不正确,无法解析:" + e.getMessage(), e);
            return false;
        }

        LOGGER.debug("待验证的签名: [{}]", sign);
        try {
            byte[] signBytes = Base64.getDecoder().decode(sign);
            return RSAUtil.signVerify(
                    publicKey,
                    Common.RSA_SIGN_ALGORITHM,
                    signData,
                    signBytes
            );
        } catch (Exception e) {
            LOGGER.error("签名执行失败: {}", e.getMessage(), e);
        }
        return false;
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
