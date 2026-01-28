package com.verlake.dam.batch;

import com.verlake.dam.entity.firebase.NotificationTask;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
public class NotificationTaskReader implements ItemReader<NotificationTask> {
    private NotificationTask task;
    private boolean taskRead = false;

    public void setTask(NotificationTask task) {
        this.task = task;
        this.taskRead = false;
    }

    @Override
    public NotificationTask read() {
        if (!taskRead && task != null) {
            taskRead = true;
            return task;
        }
        return null;
    }
} 