package com.pinkyudeer.tasket.task.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.pinkyudeer.tasket.db.annotation.Column;
import com.pinkyudeer.tasket.db.annotation.FieldCheck;
import com.pinkyudeer.tasket.db.annotation.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Table(name = "entity_events")
public class EntityEvent {

    @Nonnull
    @FieldCheck(type = FieldCheck.Type.UUID, dataType = UUID.class)
    @Column(name = "id", isPrimaryKey = true)
    private UUID id = UUID.randomUUID();
    @Nonnull
    @Column(name = "entity_type", index = { "idx_entity_events_entity_type_version" })
    private String entityType;
    @Nonnull
    @Column(name = "entity_id", index = { "idx_entity_events_entity_id" })
    private String entityId;
    @Nonnull
    @Column(name = "event_type")
    private EventType eventType;
    @Nullable
    @Column(name = "old_version")
    private Integer oldVersion;
    @Nullable
    @Column(name = "new_version", index = { "idx_entity_events_entity_type_version" })
    private Integer newVersion;
    @Nullable
    @Column(name = "changed_fields")
    private String changedFields;
    @Nullable
    @Column(name = "actor_id")
    private UUID actorId;
    @Nonnull
    @Column(name = "timestamp", defaultValue = "CURRENT_TIMESTAMP")
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum EventType {
        INSERT,
        UPDATE,
        DELETE
    }
}
