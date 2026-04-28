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
    private Integer showOnLogin; // 1=在登录/注册页显示
    private Integer dismissable; // 1=可点击"不再显示"
    private Long createdAt;
    private Long updatedAt;

    public boolean isPinned() { return pinned != null && pinned == 1; }
    public boolean isShowOnLogin() { return showOnLogin != null && showOnLogin == 1; }
    public boolean isDismissable() { return dismissable != null && dismissable == 1; }

}
