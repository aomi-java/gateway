package tech.aomi.cloud.gateway.filter.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
import tech.aomi.common.constant.Common;
import tech.aomi.common.utils.crypto.RSA;
import tech.aomi.common.utils.crypto.RSAUtil;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;

/**
 * @author 田尘殇Sean(sean.snow @ live.com) createAt 2018/9/4
 */
@Slf4j
public class SignDataUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
                .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        ;
    }

    /**
     * 获取签名字符串
     *
     * @param data 数据
     * @return 数据转换的字符串
     */
    public static String getSignStr(Object data) {
        ObjectWriter writer = objectMapper.writerFor(data.getClass());
        try {
            return writer.writeValueAsString(data);
        } catch (IOException ignored) {
        }
        return "";
    }

    /**
     * 获取签名字符串
     * 以JSON格式转换
     *
     * @param data 数据
     * @return 数据转换的字符串
     */
    public static byte[] getSignText(Object data) {
        ObjectWriter writer = objectMapper.writerFor(data.getClass());
        try {
            return writer.writeValueAsBytes(data);
        } catch (IOException ignored) {
        }
        return new byte[0];
    }

    /**
     * 字符串转换为签名数据
     *
     * @param strData 签名数据
     * @return 签名信息
     */
    public static byte[] strToSignText(byte[] strData) {
        try {
            Map<String, Object> tmp = objectMapper.readValue(strData, new TypeReference<Map<String, Object>>() {
            });
            return getSignText(tmp);
        } catch (IOException e) {
            LOGGER.error("读取签名数据失败: {}", e.getMessage(), e);
        }
        return new byte[0];
    }

    /**
     * 签名计算
     *
     * @param data             签名数据
     * @param base64PrivateKey base64编码的私钥
     * @return 签名信息
     */
    public static String getSign(Object data, String base64PrivateKey) {
        byte[] signData = getSignText(data);
        try {
            PrivateKey privateKey = RSA.parsePrivateKeyWithBase64(base64PrivateKey);
            byte[] signArr = RSAUtil.sign(privateKey, Common.SIGN_ALGORITHMS, signData);
            String sign = Base64.getEncoder().encodeToString(signArr);
            LOGGER.debug("签名数据: [{}]", new String(signData));
            LOGGER.debug("签名: [{}]", sign);
            return sign;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException e) {
            LOGGER.error("签名失败", e);
            return "";
        }
    }
}
