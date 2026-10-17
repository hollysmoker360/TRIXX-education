package com.schoolsphere.messaging.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, UUID> {

    Optional<ChannelMember> findBySchoolIdAndChannelIdAndMembershipId(UUID schoolId, UUID channelId, UUID membershipId);

    List<ChannelMember> findBySchoolIdAndMembershipId(UUID schoolId, UUID membershipId);

    List<ChannelMember> findBySchoolIdAndChannelId(UUID schoolId, UUID channelId);

    long countBySchoolIdAndChannelId(UUID schoolId, UUID channelId);
}
