package io.github.arielne.alp.ticket;

/**
 * Một giấy tờ đã lưu lên Storage, chờ ghi vào bảng app.attachment: tên gốc (chỉ để hiển thị), tên lưu là UUID
 * (QT-10), loại đã kiểm bằng byte đầu file, dung lượng.
 */
public record StoredAttachment(String originalName, String storageKey, String contentType, long sizeBytes) {
}