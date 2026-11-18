package io.github.arielne.alp.common;

import lombok.Getter;
import lombok.Setter;

/** Một dòng chưa gửi của bảng app.outbox_event, đọc bằng OutboxMapper.lockUnpublished. payload là chuỗi JSON. */
@Getter
@Setter
public class OutboxEvent {

    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
}