package com.pcdd.sonovel.model;

import lombok.Builder;
import lombok.Data;

/**
 * 下载历史记录模型
 *
 * @author pcdd
 */
@Data
@Builder
public class DownloadHistory {

    private Integer id;
    private Integer userId;
    private String bookName;
    private String author;
    private String sourceName;
    private String format;
    private String fileName;
    private Long fileSize;
    private Long createdAt;

}
