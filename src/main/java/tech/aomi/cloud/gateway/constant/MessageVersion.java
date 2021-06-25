package tech.aomi.cloud.gateway.constant;

import java.util.Arrays;

/**
 * 报文版本定义
 *
 * @author Sean createAt 2021/6/25
 */
public enum MessageVersion {

    V1_0_0("1.0.0"),
    V2_0_0("2.0.0"),
    LATEST("2.0.0");

    String version;

    MessageVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public static MessageVersion of(String version) {
        return Arrays.stream(MessageVersion.values())
                .filter(item -> item.version.equals(version))
                .findFirst().orElse(null);
    }
}
