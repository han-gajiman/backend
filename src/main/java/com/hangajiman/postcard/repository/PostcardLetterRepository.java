package com.hangajiman.postcard.repository;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.postcard.domain.PostcardLetter;

public interface PostcardLetterRepository extends JpaRepository<PostcardLetter, Long> {

    Slice<PostcardLetter> findByReceiverId(Long receiverId, Pageable pageable);

    Optional<PostcardLetter> findByIdAndReceiverId(Long id, Long receiverId);

    // 우체통 깃발: 안 읽은 받은 엽서가 있는지
    boolean existsByReceiverIdAndReadAtIsNull(Long receiverId);
}
