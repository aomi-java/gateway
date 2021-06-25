package tech.aomi.cloud.gateway.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tech.aomi.cloud.gateway.entity.Client;

/**
 * @author Sean createAt 2021/6/25
 */
@Repository
public interface ClientRepository extends MongoRepository<Client, String> {

    Client findByCode(String code);

}
