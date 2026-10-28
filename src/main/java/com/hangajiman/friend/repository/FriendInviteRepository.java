package com.hangajiman.friend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.friend.domain.FriendInvite;

public interface FriendInviteRepository extends JpaRepository<FriendInvite, Long> {

    Optional<FriendInvite> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);
}
