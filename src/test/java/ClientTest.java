import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tech.aomi.cloud.gateway.controller.RequestMessage;
import tech.aomi.cloud.gateway.controller.ResponseMessage;
import tech.aomi.common.constant.Common;
import tech.aomi.common.utils.MapBuilder;
import tech.aomi.common.utils.crypto.AesUtils;
import tech.aomi.common.utils.crypto.RSAUtil;
import tech.aomi.common.utils.json.Json;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * @author Sean createAt 2021/6/23
 */
public class ClientTest {


    @Test
    public void test() throws Exception {

        String channelPk = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAylGKjhqXjh8tKD2yTqBXnBzQBJShyFJhfo9lnl3OySLM4zAJ64gZHA8dLw6tJFtxL3aVE605PGN/TB9ghtWUMCkLFva+6IoyokiAIMkJcSpspXOLzNuIPVotQb5JJ7DeL2pqIR/INKcDgUmmVfh3iR0dLCVOznmew1ooUnaf+p55emFnS5ebKWKuhps1H2eldGWwKNoQweqJuk8G1WxnfZqRgCTW/mQ9umKX2kkv92hPWoTppeaRFwqAe2OMP/O0RrmMwV7l24LcvSjNazCY2JK36dW7ALX/2h7oWR7L6i93UxQgwb0Zze7Rm880XHYz/U9DGoGcbXhRy7kLh10KQQIDAQAB";
        String prk = "MIIEwAIBADANBgkqhkiG9w0BAQEFAASCBKowggSmAgEAAoIBAQDKUYqOGpeOHy0oPbJOoFecHNAElKHIUmF+j2WeXc7JIszjMAnriBkcDx0vDq0kW3EvdpUTrTk8Y39MH2CG1ZQwKQsW9r7oijKiSIAgyQlxKmylc4vM24g9Wi1BvkknsN4vamohH8g0pwOBSaZV+HeJHR0sJU7OeZ7DWihSdp/6nnl6YWdLl5spYq6GmzUfZ6V0ZbAo2hDB6om6TwbVbGd9mpGAJNb+ZD26YpfaSS/3aE9ahOml5pEXCoB7Y4w/87RGuYzBXuXbgty9KM1rMJjYkrfp1bsAtf/aHuhZHsvqL3dTFCDBvRnN7tGbzzRcdjP9T0MagZxteFHLuQuHXQpBAgMBAAECggEBAMRw0fhSR48+JClrZkLDmu1AaJXZ/w+zNWieMQvIh6xx9sAsd6VSmxbMcgir1l9zzf1IxUy6p9VDwmkWGjIxFFaCs3rTj9/Xt3wsqwOqT1mq2Jz5COeazLjNYx3vdbZtG/6r82pAIrNE6rlQ2omk2+Os+hNQEimWmxmQ44/WEFVUaFrTw4yJR+B3lE1ZqpobCr4pEJT01Ds9pDAI/Cl7uGMwfd7bmUJ+sU9wFEjZtZE/UNcQQgqTxb4WgCU7ZDRFrvZIqCx5buACLZR0FtQGEksXg43/x2PMEhLvlcv0XShoi6fIKFdeTgDGbnd+YFrK9TpVOmxVN0tkTBRwZrslVcECgYEA+SPP4GaX6QS6bTKVfMb21XkqzoKvxlQFW56NDP034iiQzc3jGaRm6pvZbQDecEVgcd8C22vJMKZbmBTl3/hjKkjVMdgyKlwi7vKBAcoZVmvPfxFLkkT91uMKWO14EtuWHG/6Mpz8JAN4VAUVQsS0L9Ubkpjx56Ew/Fkn56ZdYRkCgYEAz+Ovaa2DaNX44vBxhcCUSuCWA1ea5U0UXnaHkHAwbQVo9kwNhxyzyauq5NYJol4Pyk6WBpJfpLhjsxmLI/SFVn4ZyaY6eUvRoWpFpskYvynbDl9LiuC1q5YOq+nrLtlyi1HNAz8ZJfmQLlGJyL7zCYnqmnUZbfMCqHf53jaVz2kCgYEAsHKDlEs0xWx62EGeC7wiLvhcr9twwAbbsJKvFQb1oC/YtlldwNhlpzzvlTqrT1pjPuKR9HL3D4SSlDggwin5mYXxsBaNGOEeQJrxcSIAJeu/DiBipFpGaP1tY6PziW+JdeR8j4INNThb7S2YbCxB7SqCF6ZIlSLdPaurDm4N7mkCgYEAi2AY4H7mFUkvXebaFVQxl6nOqVr4jDcLKvHInXu5272+yzHd9/G0T8b6AgXF28e4Smg5iRplaSf+H7tGX8q2AnD0lQ8PMPc2CkQXgmRcZP2I0a/uE6Pn6KvoFjXz6Sr78o/bJQwOrjkNAyDDgYUTqBeA5CER9XbxF0WojeSGt9ECgYEAzl8d3PKMOly74bqd0eoQNnLUWrgAYV2w9kNCtWkETtRz82j0CVSkbFTOsMTuhGW0gj0AUTGCZjDyNGEOTnLYggIRUOTSkGjlv21+rM5xtYRAg5slec2VuNYdnlByquKwXXiHpH5mNstkiE9jWVdH+aNwq5GEOQ5OrmD/O/CQtlE=";

        RequestMessage message = new RequestMessage();
        message.setRequestId(UUID.randomUUID().toString());
        message.setClientId("123");

        AesUtils.setTransformation(AesUtils.AES_CBC_PKCS5Padding);
        AesUtils.setKeyLength(128);
        byte[] key = AesUtils.generateKey();

        message.setTrk(RSAUtil.publicKeyEncryptWithBase64(channelPk, key));
        message.setTimestamp(DateFormatUtils.format(new Date(), Common.DATETIME_FORMAT));
        message.setRandomString(UUID.randomUUID().toString().replaceAll("-", ""));

//        Map<String, String> payload = MapBuilder.of(
//                "auditNumber", "22222222222"
//        );
//        String payloadStr = Json.toJson(payload).toString();
//        byte[] payloadCi = AesUtils.encrypt(key, payloadStr.getBytes(StandardCharsets.UTF_8));
//        message.setPayload(Base64.getEncoder().encodeToString(payloadCi));

        String signData = message.getTimestamp() + message.getRandomString() + StringUtils.trimToEmpty(message.getPayload());
        System.out.println(signData);

        String sign = RSAUtil.signWithBase64(prk, RSAUtil.SIGN_ALGORITHMS_SHA512, signData.getBytes(message.getCharset()));

        message.setSign(sign);

        RestTemplate restTemplate = new RestTemplate();

        System.out.println(message);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("http://localhost/v2/pay/record?a=1")
                .queryParam("requestId", message.getRequestId())
                .queryParam("clientId", message.getClientId())
                .queryParam("trk", URLEncoder.encode(message.getTrk()))
                .queryParam("timestamp", message.getTimestamp())
                .queryParam("randomString", message.getRandomString())
//                .queryParam("payload", URLEncoder.encode(message.getPayload()))
                .queryParam("sign", URLEncoder.encode(message.getSign()));
//        ResponseMessage res = restTemplate.getForObject(builder.build().toString(), ResponseMessage.class);

        HttpEntity<ResponseMessage> resBody = restTemplate.exchange(builder.build().toString(), HttpMethod.GET,null,ResponseMessage.class);

//        ResponseMessage res = restTemplate.postForObject(builder.build().toString(), message, ResponseMessage.class);
        System.out.println(resBody.getBody());
        System.out.println(resBody.getHeaders());
    }


}
