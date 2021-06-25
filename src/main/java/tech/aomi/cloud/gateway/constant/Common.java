package tech.aomi.cloud.gateway.constant;

import tech.aomi.common.utils.crypto.AesUtils;
import tech.aomi.common.utils.crypto.RSAUtil;

/**
 * @author Sean createAt 2021/6/24
 */
public class Common {

    /**
     * AES 加密算法、模式、补位方式
     */
    public static final String AES_TRANSFORMATION = AesUtils.AES_CBC_PKCS5Padding;

    /**
     * AES 加密密钥长度
     */
    public static final int AES_KEY_LENGTH = 128;

    /**
     * RSA 签名算法
     */
    public static final String RSA_SIGN_ALGORITHM = RSAUtil.SIGN_ALGORITHMS_SHA512;

}
