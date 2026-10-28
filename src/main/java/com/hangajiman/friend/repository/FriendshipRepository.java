package com.hangajiman.friend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.friend.domain.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);
}
