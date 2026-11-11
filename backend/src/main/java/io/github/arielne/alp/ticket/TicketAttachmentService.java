package io.github.arielne.alp.ticket;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.FileStorage;
import io.github.arielne.alp.common.UploadedFileType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Giấy tờ đính kèm của phiếu (QT-10, spec phiếu mục 11.1): kiểm số file và loại thật của từng file, lưu lên
 * Storage với tên UUID, đọc lại để tải qua backend. File lưu lên Storage trước câu INSERT, nên transaction bị hủy
 * thì xóa các file vừa lưu (TransactionSynchronization, như hoàn tác KeyCloak ở phần nhân viên).
 * Cỡ file (5 MB) do Spring chặn trước (spring.servlet.multipart), file quá cỡ không tới được đây.
 */
@Service
public class TicketAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAttachmentService.class);
    private static final int MAX_FILES = 5;
    private static final int MAX_NAME_LENGTH = 255;

    private final FileStorage fileStorage;
    private final String bucket;

    /** bucket: tên bucket giấy tờ (storage.s3.ticket-bucket trong application.yml). */
    public TicketAttachmentService(FileStorage fileStorage, @Value("${storage.s3.ticket-bucket}") String bucket) {
        this.fileStorage = fileStorage;
        this.bucket = bucket;
    }

    /** Tạo phiếu: kiểm hết các file trước (sai thì 400, chưa lưu file nào), rồi mới lưu từng file. */
    public List<StoredAttachment> storeAll(List<MultipartFile> files) {
        return storeAdditional(files, 0);
    }

    /**
     * Sửa phiếu (D3): keptCount là số giấy tờ cũ còn giữ. Tổng số giấy tờ đang dùng sau khi sửa phải từ 1 đến 5;
     * không thêm file mới cũng được nếu còn giữ file cũ.
     */
    public List<StoredAttachment> storeAdditional(List<MultipartFile> files, int keptCount) {
        int totalCount = keptCount + files.size();
        if (totalCount < 1 || totalCount > MAX_FILES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần đính kèm từ 1 đến 5 giấy tờ");
        }
        List<CheckedFile> checkedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalName = safeFileName(file.getOriginalFilename());
            byte[] content = readBytes(file);
            checkedFiles.add(new CheckedFile(originalName, content, UploadedFileType.check(originalName, content)));
        }
        List<StoredAttachment> storedFiles = new ArrayList<>();
        for (CheckedFile file : checkedFiles) {
            storedFiles.add(store(file));
        }
        return storedFiles;
    }

    /** Nội dung một giấy tờ để trả cho trình duyệt; chỉ gọi sau khi đã kiểm giấy tờ thuộc đúng phiếu. */
    public byte[] read(AttachmentRow attachment) {
        return fileStorage.read(bucket, attachment.getStorageKey());
    }

    private StoredAttachment store(CheckedFile file) {
        String storageKey = UUID.randomUUID().toString();
        String contentType = file.type().getContentType();
        fileStorage.save(bucket, storageKey, file.content(), contentType);
        deleteOnRollback(storageKey);
        return new StoredAttachment(file.originalName(), storageKey, contentType, file.content().length);
    }

    /** Commit được thì giữ file; transaction bị hủy thì xóa. Xóa lỗi chỉ ghi log: file thừa không ai tải được. */
    private void deleteOnRollback(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    fileStorage.delete(bucket, storageKey);
                } catch (RuntimeException exception) {
                    log.warn("Không xóa được file {} sau khi transaction bị hủy: {}", storageKey,
                            exception.getMessage());
                }
            }
        });
    }

    /**
     * Tên gốc chỉ để hiển thị: bỏ phần đường dẫn (một số trình duyệt gửi cả đường dẫn trên máy người dùng),
     * giữ 255 ký tự cuối cho vừa cột original_name mà vẫn còn đuôi file.
     */
    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "giay-to";
        }
        int lastSeparator = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        String name = originalFilename.substring(lastSeparator + 1).trim();
        if (name.isEmpty()) {
            return "giay-to";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return name.substring(name.length() - MAX_NAME_LENGTH);
        }
        return name;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Không đọc được file tải lên", exception);
        }
    }

    /** Một file đã kiểm xong: tên gốc, nội dung, loại thật. */
    private record CheckedFile(String originalName, byte[] content, UploadedFileType type) {
    }
}