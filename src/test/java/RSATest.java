import org.junit.jupiter.api.Test;
import tech.aomi.common.utils.crypto.RSA;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * @author Sean createAt 2021/6/23
 */
public class RSATest {

    @Test
    public void test() throws NoSuchAlgorithmException {

        RSA.Key key = RSA.generateKey(2048);

        System.out.println(Base64.getEncoder().encodeToString(key.getPublicKey().getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(key.getPrivateKey().getEncoded()));
    }
}
