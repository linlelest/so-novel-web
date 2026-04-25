package com.pcdd.sonovel.model;

import lombok.Builder;
import lombok.Data;

/**
 * API Token 模型
 *
 * @author pcdd
 */
@Data
@Builder
public class ApiToken {

    private Integer id;
    private Integer userId;
    private String token;
    private String note;
    private Long createdAt;
    private Long lastUsedAt;

}
