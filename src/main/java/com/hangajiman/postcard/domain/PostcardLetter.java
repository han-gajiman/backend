package com.hangajiman.postcard.domain;

import java.time.Instant;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 보낸 엽서 (ERD postcard_letters). 가진 엽서 뒷면에 글을 써서 친구나 나에게 보냅니다. 글은 고칠 수 없습니다.
 */
@Entity
@Table(name = "postcard_letters")
public class PostcardLetter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(nullable = false)
    private short style;

    @Column(nullable = false, length = 100)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    protected PostcardLetter() {
    }

    public PostcardLetter(Long senderId, Long receiverId, Long spotId, int style, String message) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.spotId = spotId;
        this.style = (short) style;
        this.message = message;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public Long getSpotId() {
        return spotId;
    }

    public int getStyle() {
        return style;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
