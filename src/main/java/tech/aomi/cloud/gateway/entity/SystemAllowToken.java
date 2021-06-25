package tech.aomi.cloud.gateway.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

/**
 * @author Sean createAt 2021/6/25
 */
@Getter
@Setter
public class SystemAllowToken implements java.io.Serializable {

    private static final long serialVersionUID = 4649527161610792169L;

    private String id;

    /**
     * 允许token值
     */
    @Indexed(unique = true)
    private String token;

    /**
     * 备注
     */
    private String remark;
}
