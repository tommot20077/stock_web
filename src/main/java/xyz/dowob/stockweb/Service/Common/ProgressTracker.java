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

@Service
public class ProgressTracker implements DisposableBean {
    private final TaskRepository taskRepository;
    private final DynamicThreadPoolManager dynamicThreadPoolManager;


    //todo 方法來刪除已完成任務的進度數據或確定任務數據何時刪除可能也是必要的
    //todo 獲得所有任務id
    private final ConcurrentHashMap<String, Progress> progressMap = new ConcurrentHashMap<>();

    public ProgressTracker(TaskRepository taskRepository, DynamicThreadPoolManager dynamicThreadPoolManager) {this.taskRepository = taskRepository;
        this.dynamicThreadPoolManager = dynamicThreadPoolManager;
    }

    public String createAndTrackNewTask(int totalTask, String name) {
        String taskId = UUID.randomUUID().toString();
        progressMap.put(taskId, new Progress(totalTask, name));
        return taskId;
    }

    public void resetProgress(String taskId, int totalTask, String name) {
        Progress progress = new Progress(totalTask, name);
        progressMap.put(taskId, progress);
    }

    public void incrementProgress(String taskId) {
        Progress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.incrementProgress();
        }
    }


    public void updateProgress(String taskId, int progress, String name) {
        progressMap.put(taskId, new Progress(progress, name));
    }

    public void deleteProgress(String taskId) {
        progressMap.remove(taskId);
    }

    public List<Progress> getAllProgressInfo() {
        return new ArrayList<>(progressMap.values());
    }

    @Override
    public void destroy() throws Exception {
        List<Task> tasks = taskRepository.findAllByTaskStatus(TaskStatusType.IN_PROGRESS);
        for (Task task : tasks) {
            task.completeTask(TaskStatusType.FAILED, "程式被終止，任務失敗");
            taskRepository.save(task);
        }
        dynamicThreadPoolManager.shutdown(5, TimeUnit.SECONDS);
    }


}
