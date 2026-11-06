package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/** Một giấy tờ đang dùng của phiếu (removed_at rỗng), đọc từ bảng app.attachment. storageKey không trả cho React. */
@Getter
@Setter
public class AttachmentRow {

    private Long id;
    private String originalName;
    private String storageKey;
    private String contentType;
    private long sizeBytes;
}