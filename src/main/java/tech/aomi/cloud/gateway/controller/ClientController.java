package tech.aomi.cloud.gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import tech.aomi.cloud.gateway.api.ClientService;
import tech.aomi.cloud.gateway.dto.CreateClientDto;
import tech.aomi.cloud.gateway.dto.UpdateClientDto;
import tech.aomi.cloud.gateway.entity.Client;
import tech.aomi.cloud.gateway.repository.SystemAllowTokenRepository;
import tech.aomi.common.exception.ServiceException;
import tech.aomi.common.utils.StringUtils;
import tech.aomi.common.web.controller.AbstractController;
import tech.aomi.common.web.controller.Result;

import javax.validation.Valid;

/**
 * @author Sean createAt 2021/6/25
 */
@RestController
@RequestMapping("/clients")
public class ClientController extends AbstractController {

    @Autowired
    private ClientService clientService;

    @Autowired
    private SystemAllowTokenRepository systemAllowTokenRepository;

    @GetMapping("/{id}")
    public Result getOne(@PathVariable String id, ServerHttpRequest request) {
        check(request);
        return success(clientService.getClient(id));
    }

    @GetMapping("/code/{code}")
    public Result getOneByCode(@PathVariable String code, ServerHttpRequest request) {
        check(request);
        return success(clientService.getClientByCode(code));
    }

    @PostMapping
    public Result create(@RequestBody @Valid CreateClientDto dto, ServerHttpRequest request) {
        check(request);
        Client client = clientService.save(dto);
        return success(client);
    }

    @PutMapping
    public Result update(@RequestBody @Valid UpdateClientDto dto, ServerHttpRequest request) {
        check(request);
        Client client = clientService.update(dto);
        return success(client);
    }

    private void check(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("X-Token");
        if (StringUtils.isEmpty(token)) {
            throw new ServiceException("Hello, What's your name");
        }

        if (systemAllowTokenRepository.existsByToken(token)) {
            return;
        }
        throw new ServiceException("Hello, What's your name");
    }
}
