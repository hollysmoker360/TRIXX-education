package com.schoolsphere.messaging.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Optional<Message> findBySchoolIdAndId(UUID schoolId, UUID id);

    Optional<Message> findByChannelIdAndClientId(UUID channelId, UUID clientId);

    List<Message> findByChannelIdAndSeqGreaterThanOrderBySeqAsc(UUID channelId, long seq, Pageable pageable);

    List<Message> findByChannelIdAndSeqLessThanOrderBySeqDesc(UUID channelId, long seq, Pageable pageable);

    List<Message> findBySchoolIdAndReportedAtIsNotNullOrderByReportedAtDesc(UUID schoolId, Pageable pageable);

    @Query(value = """
            SELECT m.channel_id AS "channelId", m.id AS "id", m.body AS "body", m.seq AS "seq",
                   m.sent_at AS "sentAt", m.sender_membership_id AS "senderMembershipId"
            FROM message m
            WHERE m.channel_id IN (:channelIds) AND m.deleted_at IS NULL
            ORDER BY m.channel_id, m.seq DESC
            """, nativeQuery = true)
    List<LastMessageRow> findLatestPerChannel(@Param("channelIds") Collection<UUID> channelIds);

    @Query(value = """
            SELECT m.channel_id AS "channelId", COUNT(*) AS "unread"
            FROM message m
            JOIN channel_member cm ON cm.channel_id = m.channel_id AND cm.membership_id = :membershipId
            WHERE m.channel_id IN (:channelIds) AND m.deleted_at IS NULL AND m.seq > cm.last_read_seq
            GROUP BY m.channel_id
            """, nativeQuery = true)
    List<UnreadRow> countUnreadPerChannel(@Param("membershipId") UUID membershipId,
                                          @Param("channelIds") Collection<UUID> channelIds);

    @Query(value = """
            SELECT m.* FROM message m
            WHERE m.school_id = :schoolId
              AND m.deleted_at IS NULL
              AND (:channelId IS NULL OR m.channel_id = :channelId)
              AND m.search_vector @@ plainto_tsquery('english', :query)
              AND m.channel_id IN (SELECT cm.channel_id FROM channel_member cm
                                   WHERE cm.membership_id = :membershipId)
            ORDER BY m.seq DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Message> search(@Param("schoolId") UUID schoolId,
                         @Param("channelId") UUID channelId,
                         @Param("query") String query,
                         @Param("membershipId") UUID membershipId,
                         @Param("limit") int limit);

    interface LastMessageRow {

        UUID getChannelId();

        UUID getId();

        String getBody();

        Long getSeq();

        Instant getSentAt();

        UUID getSenderMembershipId();
    }

    interface UnreadRow {

        UUID getChannelId();

        long getUnread();
    }
}
