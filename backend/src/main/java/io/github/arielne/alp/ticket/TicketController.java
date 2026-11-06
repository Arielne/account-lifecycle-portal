package io.github.arielne.alp.ticket;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API phiếu yêu cầu: tạo phiếu (CN-11), kiểm trước khi tạo, màn Chờ duyệt (CN-14), xem chi tiết và tải giấy tờ
 * (CN-15), duyệt (CN-16), từ chối (CN-17).
 * Phân quyền theo role nằm ở SecurityConfig (QT-07); Admin không gọi được API nào ở đây.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;
    private final TicketReviewService ticketReviewService;

    /**
     * CN-11: multipart gồm phần "data" (JSON, TicketCreateRequest) và phần "files" (1–5 giấy tờ).
     * files không bắt buộc ở đây để thiếu file vẫn nhận câu báo rõ "Cần đính kèm từ 1 đến 5 giấy tờ" từ Service.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketCreated create(@Valid @RequestPart("data") TicketCreateRequest request,
                                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        List<MultipartFile> uploadedFiles = List.of();
        if (files != null) {
            uploadedFiles = files;
        }
        return ticketService.create(request, uploadedFiles);
    }

    /** CN-11, bước 2 của màn Tạo phiếu: tài khoản có tạo được phiếu loại này không. */
    @GetMapping("/eligibility")
    public TicketEligibility eligibility(@Valid TicketEligibilityQuery query) {
        return ticketService.checkEligibility(query.type(), query.accountNumber());
    }

    /** CN-11: kiểm số CCCD cho phiếu Mở tài khoản; POST để số CCCD nằm trong thân request, không lên URL. */
    @PostMapping("/customer-check")
    public CustomerCheckResult customerCheck(@Valid @RequestBody CustomerCheckRequest request) {
        return ticketService.checkCustomer(request.cccd());
    }

    /** CN-14, màn Chờ duyệt: phiếu chờ người xem duyệt; lọc theo phần đầu mã phiếu và loại phiếu. */
    @GetMapping("/pending-review")
    public List<PendingTicketItem> pendingReview(@RequestParam(required = false) String code,
                                                 @RequestParam(required = false) TicketType type) {
        return ticketReviewService.findPendingReview(code, type);
    }

    /** CN-14, D9: số phiếu chờ duyệt cho badge của menu. */
    @GetMapping("/pending-review/summary")
    public PendingReviewSummary pendingReviewSummary() {
        return ticketReviewService.summarizePendingReview();
    }

    /** CN-15: chi tiết phiếu, kèm cờ nút Duyệt, Từ chối tính cho người đang xem. */
    @GetMapping("/{id}")
    public TicketDetail findById(@PathVariable Long id) {
        return ticketQueryService.findDetail(id);
    }

    /** CN-16: duyệt phiếu; không có thân request. */
    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable Long id) {
        ticketReviewService.approve(id);
    }

    /** CN-17: từ chối phiếu kèm lý do. */
    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id, @Valid @RequestBody TicketRejectRequest request) {
        ticketReviewService.reject(id, request.reason());
    }

    /**
     * CN-15: một giấy tờ, chỉ qua backend (QT-10). inline để xem ngay trong tab mới, download=true để tải về;
     * tên gốc mã hóa UTF-8 nên giữ được dấu tiếng Việt. Header X-Content-Type-Options: nosniff do Spring Security
     * gắn sẵn cho mọi response, trình duyệt không tự đoán lại loại file.
     */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                             @RequestParam(defaultValue = "false") boolean download) {
        TicketQueryService.AttachmentContent attachment = ticketQueryService.readAttachment(id, attachmentId);
        String dispositionType = "inline";
        if (download) {
            dispositionType = "attachment";
        }
        ContentDisposition disposition = ContentDisposition.builder(dispositionType)
                .filename(attachment.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(attachment.content());
    }
}