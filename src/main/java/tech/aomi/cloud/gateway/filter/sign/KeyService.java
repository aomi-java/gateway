package tech.aomi.cloud.gateway.filter.sign;

import java.util.Map;

/**
 * 密钥服务
 *
 * @author Sean createAt 2021/5/7
 */
public interface KeyService {


    /**
     * 查找密钥
     *
     * @param headers http 请求头数据
     * @return 密钥信息
     */
    Key findKey(Map<String, String> headers);

}
