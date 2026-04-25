package com.pcdd.sonovel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 公告模型
 *
 * @author pcdd
 */
@Data
@Builder
public class Announcement {

    private Integer id;
    private String title;
    private String content;   // Markdown 格式
    private Integer pinned;   // 1=置顶, 0=普通
    private Long createdAt;
    private Long updatedAt;

    public boolean isPinned() {
        return pinned != null && pinned == 1;
    }

}
