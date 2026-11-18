package io.github.arielne.alp.common;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Mapper MyBatis của bảng app.outbox_event (Outbox pattern, spec phiếu mục 9). Câu SQL nằm trong OutboxMapper.xml. */
@Mapper
public interface OutboxMapper {

    /** Thêm 1 sự kiện chưa gửi; payload là chuỗi JSON. */
    int insert(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
               @Param("eventType") String eventType, @Param("payload") String payload);

    /**
     * Tối đa limit sự kiện chưa gửi, cũ nhất trước, khóa các dòng này tới hết transaction; dòng đang bị transaction
     * khác khóa thì bỏ qua (OutboxPublisher).
     */
    List<OutboxEvent> lockUnpublished(int limit);

    /** Đánh dấu sự kiện đã được Kafka nhận. */
    int markPublished(Long id);
}