package com.schoolsphere.learning.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only view of learning items and a learner's progress on them, for modules that build on them. */
public interface LearningProgressApi {

    enum ItemType { RESOURCE, ASSIGNMENT, QUIZ }

    record ItemRef(ItemType type, UUID id) {
    }

    /** {@code state} is the item's own lifecycle state (draft, published, ...). */
    record ItemInfo(ItemType type, UUID id, String title, String state) {
    }

    /**
     * {@code completed}: resource marked complete, assignment submitted, or quiz attempt submitted.
     * {@code scorePercent}: the published grade or quiz score as a percentage, or null when there is none.
     */
    record ItemOutcome(ItemType type, UUID id, boolean completed, Double scorePercent) {
    }

    Optional<ItemInfo> findItem(UUID schoolId, ItemType type, UUID id);

    List<ItemOutcome> outcomes(UUID schoolId, UUID studentId, Collection<ItemRef> items);
}
