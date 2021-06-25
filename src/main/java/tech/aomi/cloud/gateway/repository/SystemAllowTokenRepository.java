package tech.aomi.cloud.gateway.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tech.aomi.cloud.gateway.entity.SystemAllowToken;

/**
 * @author Sean createAt 2021/6/25
 */
@Repository
public interface SystemAllowTokenRepository extends MongoRepository<SystemAllowToken, String> {

    boolean existsByToken(String token);

}
