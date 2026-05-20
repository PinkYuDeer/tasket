package com.pinkyudeer.tasket.db;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.pinkyudeer.tasket.task.entity.Player;
import com.pinkyudeer.tasket.task.entity.Tag;
import com.pinkyudeer.tasket.task.entity.Task;
import com.pinkyudeer.tasket.task.entity.Team;
import com.pinkyudeer.tasket.task.entity.record.Notification;
import com.pinkyudeer.tasket.task.entity.record.PlayerInteraction;
import com.pinkyudeer.tasket.task.entity.record.StatusChangeRecord;
import com.pinkyudeer.tasket.task.entity.record.TagLink;
import com.pinkyudeer.tasket.task.entity.record.TaskInteraction;
import com.pinkyudeer.tasket.task.entity.record.TeamMember;
import com.pinkyudeer.tasket.task.entity.record.TeamRequest;

public class EntityConstructorTest {

    @Test
    public void tableEntitiesCanBeCreatedForResultSetMapping() {
        List<Class<?>> entityClasses = Arrays.asList(
            Player.class,
            Tag.class,
            Task.class,
            Team.class,
            Notification.class,
            PlayerInteraction.class,
            StatusChangeRecord.class,
            TagLink.class,
            TaskInteraction.class,
            TeamMember.class,
            TeamRequest.class);

        for (Class<?> entityClass : entityClasses) {
            assertNotNull(newEntity(entityClass));
        }
    }

    private static Object newEntity(Class<?> entityClass) {
        try {
            return entityClass.getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(entityClass.getName() + " must expose a no-arg constructor for EntityHandler", e);
        }
    }
}
