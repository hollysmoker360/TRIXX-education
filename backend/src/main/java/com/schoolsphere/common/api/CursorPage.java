package com.schoolsphere.common.api;

import java.util.List;

/** Cursor-based envelope for large and synced collections (spec 9.2). */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
