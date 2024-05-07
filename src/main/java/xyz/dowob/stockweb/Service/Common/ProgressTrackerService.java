package xyz.dowob.stockweb.Service.Common;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import xyz.dowob.stockweb.Dto.Common.Progress;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;
import xyz.dowob.stockweb.Repository.Common.TaskRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author yuan
 * 進度追踪器服務
 * 實現DisposableBean接口，用於關閉線程池
 */
@Service
public class ProgressTrackerService implements DisposableBean {
    private final TaskRepository taskRepository;

    private final DynamicThreadPoolService dynamicThreadPoolService;

    private final ConcurrentHashMap<String, Progress> progressMap = new ConcurrentHashMap<>();

    public ProgressTrackerService(TaskRepository taskRepository, DynamicThreadPoolService dynamicThreadPoolService) {
        this.taskRepository = taskRepository;
        this.dynamicThreadPoolService = dynamicThreadPoolService;
    }

    /**
     * 創建並追踪新任務
     *
     * @param totalTask 任務總數
     * @param name      任務名稱
     *
     * @return 任務ID
     */
    public String createAndTrackNewTask(int totalTask, String name) {
        String taskId = UUID.randomUUID().toString();
        progressMap.put(taskId, new Progress(totalTask, name));
        return taskId;
    }

    /**
     * 增加任務進度
     *
     * @param taskId 任務ID
     */
    public void incrementProgress(String taskId) {
        Progress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.incrementProgress();
        }
    }

    /**
     * 刪除任務進度
     *
     * @param taskId 任務ID
     */
    public void deleteProgress(String taskId) {
        progressMap.remove(taskId);
    }

    /**
     * 獲取所有進度信息
     *
     * @return 進度信息列表
     */
    public List<Progress> getAllProgressInfo() {
        return new ArrayList<>(progressMap.values());
    }


    /**
     * 重寫destroy方法，關閉線程池
     *
     * @throws Exception 關閉線程池時可能拋出的異常
     */
    @Override
    public void destroy() throws Exception {
        List<Task> tasks = taskRepository.findAllByTaskStatus(TaskStatusType.IN_PROGRESS);
        for (Task task : tasks) {
            task.completeTask(TaskStatusType.FAILED, "程式被終止，任務失敗");
            taskRepository.save(task);
        }
        dynamicThreadPoolService.shutdown(5, TimeUnit.SECONDS);
    }


}
