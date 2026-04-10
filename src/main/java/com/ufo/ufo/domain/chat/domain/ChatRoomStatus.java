package com.ufo.ufo.domain.chat.domain;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "chat_room_statuses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_status_user_room", columnNames = {"user_id", "chat_room_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomStatus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_status_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom room;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Builder
    public ChatRoomStatus(User user, ChatRoom room, boolean favorite, boolean hidden) {
        this.user = user;
        this.room = room;
        this.favorite = favorite;
        this.hidden = hidden;
    }

    public void update(Boolean favorite, Boolean hidden) {
        if (favorite != null) {
            this.favorite = favorite;
        }
        if (hidden != null) {
            this.hidden = hidden;
        }
    }

    public Long getChatId() {
        return room.getId();
    }
}
