package com.hangajiman.friend.domain;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 응원 메시지 (ERD cheers). 작성자가 탈퇴하면 같이 삭제됩니다 (ON DELETE CASCADE).
 * is_anonymous는 익명 소원지기(FRIEND-05, 보류) 응원용이라 지금은 항상 false입니다.
 */
@Entity
@Table(name = "cheers")
public class Cheer extends BaseEntity {

    public static final int MAX_MESSAGE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wish_id", nullable = false)
    private Long wishId;

    @Column(name = "writer_id", nullable = false)
    private Long writerId;

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    protected Cheer() {
    }

    private Cheer(Long wishId, Long writerId, String message, boolean anonymous) {
        this.wishId = wishId;
        this.writerId = writerId;
        this.message = message;
        this.anonymous = anonymous;
    }

    // 친구가 쓰는 응원 (실명)
    public static Cheer byFriend(Long wishId, Long writerId, String message) {
        return new Cheer(wishId, writerId, message, false);
    }

    public Long getId() {
        return id;
    }

    public Long getWishId() {
        return wishId;
    }

    public Long getWriterId() {
        return writerId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAnonymous() {
        return anonymous;
    }
}
