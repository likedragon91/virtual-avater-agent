package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class MessagePageResponseDTO {
    /** 按时间升序返回，直接渲染 */
    private List<ChatMessageResponseDTO> records;

    /** 继续向前翻页时传回的游标；为空表示没有更早的消息 */
    private Long nextCursor;

    private boolean hasMore;
}
