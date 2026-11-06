package io.github.arielne.alp.common;

/**
 * Lưu và đọc file (giấy tờ của phiếu, file import) mà không phụ thuộc nơi lưu cụ thể (QT-10).
 * Cài đặt thật là S3FileStorage (Supabase Storage qua API S3); test chậm dùng MinIO cùng API S3.
 * Tên file (key) do app đặt bằng UUID, không dùng tên người dùng gửi lên.
 */
public interface FileStorage {

    /** Lưu file vào bucket; key trùng thì ghi đè. */
    void save(String bucket, String key, byte[] content, String contentType);

    /** Đọc toàn bộ nội dung file (mỗi file tối đa 5 MB nên đọc hết vào bộ nhớ được). */
    byte[] read(String bucket, String key);

    /** Xóa file; không có file đó thì không báo lỗi. */
    void delete(String bucket, String key);
}