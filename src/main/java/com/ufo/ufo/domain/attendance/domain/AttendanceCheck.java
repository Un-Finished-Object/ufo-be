package com.ufo.ufo.domain.attendance.domain;

import com.ufo.ufo.domain.user.domain.User;
import com.ufo.ufo.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "attendance_checks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_attendance_user_date",
                        columnNames = {"user_id", "attendance_date"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCheck extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDate attendanceDate;

    @Builder
    public AttendanceCheck(User user, LocalDate attendanceDate) {
        this.user = user;
        this.attendanceDate = attendanceDate;
    }
}
