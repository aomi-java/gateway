package tech.aomi.cloud.gateway.filter.sign;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Sean createAt 2021/5/7
 */
@Getter
@Setter
public class Key implements java.io.Serializable {

    private static final long serialVersionUID = 5286498312494209961L;

    /**
     * 密钥类型
     */
    private Type type;

    /**
     * 密钥
     */
    private String value;

    public enum Type {
        RSA
    }
}
