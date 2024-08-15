package xyz.dowob.stockweb.Dto.Common;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yuan
 * 用於進度條的資料
 * 1. progressCount: 目前進度
 * 2. totalTask: 總任務數
 * 3. taskName: 任務名稱
 */
@Data
public class Progress {
    private final AtomicInteger progressCount = new AtomicInteger(1);

    private final int totalTask;

    private final String taskName;

    /**
     * 進度條的構造函數
     *
     * @param totalTask 總任務數
     * @param taskName  任務名稱
     */
    public Progress(int totalTask, String taskName) {
        this.totalTask = totalTask;
        this.taskName = taskName;
    }

    /**
     * 取得目前進度
     *
     * @return 目前進度
     */
    public int getProgress() {
        return progressCount.get();
    }

    /**
     * 增加進度
     */
    public void incrementProgress() {
        progressCount.incrementAndGet();
    }

    /**
     * 取得進度百分比
     *
     * @return 進度百分比
     */
    public float getProgressPercentage() {
        if (totalTask == 0) {
            return 100;
        }
        return (float) progressCount.get() / totalTask;
    }


    /**
     * 用於傳遞進度條的資料
     * 1. progressCount: 目前進度
     * 2. totalTask: 總任務數
     * 3. taskName: 任務名稱
     * 4. progressPercentage: 進度百分比
     */
    @Data
    public static class ProgressDto {
        private final String taskName;

        private final AtomicInteger progressCount;

        private final int totalTask;

        private final float progressPercentage;
    }
}


