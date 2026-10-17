package com.schoolsphere.messaging.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    Optional<Channel> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Channel> findBySchoolIdAndIdInAndDeletedAtIsNullOrderByNameAsc(UUID schoolId, Collection<UUID> ids);

    boolean existsBySchoolIdAndClassGroupIdAndChannelTypeAndDeletedAtIsNull(UUID schoolId, UUID classGroupId,
                                                                            String channelType);
}
