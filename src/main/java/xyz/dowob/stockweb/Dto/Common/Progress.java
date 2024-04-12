package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class Progress {

    private final AtomicInteger progressCount = new AtomicInteger(1);
    private final int totalTask;
    private final String taskName;

    public Progress(int totalTask, String taskName) {
        this.totalTask = totalTask;
        this.taskName = taskName;
    }
    public int getProgress() {
        return progressCount.get();
    }

    public void incrementProgress() {
        progressCount.incrementAndGet();
    }

    public float getProgressPercentage() {
        if (totalTask == 0) {
            return 0;
        }
        return (float) progressCount.get() / totalTask;
    }


    @Data
    public static class ProgressDto {
        private String taskName;
        private AtomicInteger progressCount;
        private int totalTask;
        private float progressPercentage;
    }
}


