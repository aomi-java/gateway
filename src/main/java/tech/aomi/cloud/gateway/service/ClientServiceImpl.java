package tech.aomi.cloud.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tech.aomi.cloud.gateway.api.ClientService;
import tech.aomi.cloud.gateway.constant.CacheKey;
import tech.aomi.cloud.gateway.dto.CreateClientDto;
import tech.aomi.cloud.gateway.dto.UpdateClientDto;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.repository.ClientRepository;
import tech.aomi.common.exception.ResourceNonExistException;

/**
 * @author Sean createAt 2021/6/23
 */
@Slf4j
@Service
@CacheConfig(cacheNames = CacheKey.CLIENT)
public class ClientServiceImpl implements ClientService {

    @Autowired
    private ClientRepository clientRepository;

    @Override
    @Cacheable(key = "#clientId", unless = "#result == null")
    public Client getClient(String clientId) {
        return clientRepository.findById(clientId).orElse(null);
    }

    @Override
    @Cacheable(key = "#result.id", unless = "#result == null")
    public Client getClientByCode(String code) {
        return clientRepository.findByCode(code);
    }

    @Override
    public Client save(CreateClientDto dto) {
        Client client = new Client();
        client.setCode(dto.getCode());
        client.setName(dto.getName());
        client.setRequestHeaders(dto.getRequestHeaders());
        client.setResponseHeaders(dto.getResponseHeaders());
        client.setClientPublicKey(dto.getClientPublicKey());
        client.setPublicKey(dto.getPublicKey());
        client.setPrivateKey(dto.getPrivateKey());
        client.setPlatform(dto.getPlatform());
        return clientRepository.save(client);
    }

    @Override
    @CacheEvict(key = "#result.id")
    public Client update(UpdateClientDto dto) {
        if (StringUtils.isEmpty(dto.getId()) && StringUtils.isEmpty(dto.getCode())) {
            throw new IllegalArgumentException("ID 或者 Code 必填其一");
        }

        Client client = null;
        if (StringUtils.isNotEmpty(dto.getId())) {
            client = clientRepository.findById(dto.getId()).orElse(null);
        } else if (StringUtils.isNotEmpty(dto.getCode())) {
            client = clientRepository.findByCode(dto.getCode());
        }
        if (null == client) {
            throw new ResourceNonExistException("客户端不存在");
        }
        client.setName(dto.getName());
        client.setRequestHeaders(dto.getRequestHeaders());
        client.setResponseHeaders(dto.getResponseHeaders());
        client.setClientPublicKey(dto.getClientPublicKey());
        client.setPublicKey(dto.getPublicKey());
        client.setPrivateKey(dto.getPrivateKey());
        return clientRepository.save(client);
    }
}
