package com.pcdd.sonovel.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InviteCode {
    private Integer id;
    private String code;
    private Integer maxUses;
    private Integer usedCount;
    private Long createdAt;
    private String createdBy;
    public boolean isExhausted() { return maxUses > 0 && usedCount >= maxUses; }
}
