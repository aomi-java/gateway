package tech.aomi.cloud.gateway.api;

import tech.aomi.cloud.gateway.dto.CreateClientDto;
import tech.aomi.cloud.gateway.dto.UpdateClientDto;
import tech.aomi.cloud.gateway.entity.Client;

/**
 * 秘钥服务
 *
 * @author Sean createAt 2021/6/22
 */
public interface ClientService {

    /**
     * 获取
     *
     * @param clientId 客户端ID
     * @return 客户端公钥
     */
    Client getClient(String clientId);

    /**
     * 获取公钥通过code
     *
     * @param code code
     * @return 客户端公钥
     */
    Client getClientByCode(String code);

    Client save(CreateClientDto dto);

    Client update(UpdateClientDto dto);
}
