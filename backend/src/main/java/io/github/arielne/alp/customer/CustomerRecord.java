package io.github.arielne.alp.customer;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng app.customer để INSERT hoặc UPDATE (CustomerMapper.insert, updateProfile).
 * Là class có setter (không phải record) để MyBatis gán lại id do database sinh sau câu INSERT.
 */
@Getter
@Setter
public class CustomerRecord {

    private Long id;
    private String cccd;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private LocalDate idIssueDate;
    private String idIssuePlace;
    private String phone;
    private String email;
    private String address;
    private Long branchId;
}