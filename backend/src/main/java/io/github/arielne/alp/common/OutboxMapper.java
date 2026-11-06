package io.github.arielne.alp.common;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Mapper MyBatis của bảng app.outbox_event (Outbox pattern, spec phiếu mục 9). Câu SQL nằm trong OutboxMapper.xml. */
@Mapper
public interface OutboxMapper {

    /** Thêm 1 sự kiện chưa gửi; payload là chuỗi JSON. */
    int insert(@Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
               @Param("eventType") String eventType, @Param("payload") String payload);
}