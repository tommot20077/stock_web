package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;

import java.util.List;

/**
 * @author yuan
 * 伺服器任務與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 透過任務狀態尋找任務
     *
     * @param taskStatus 任務狀態
     *
     * @return 任務列表
     */
    List<Task> findAllByTaskStatus(TaskStatusType taskStatus);
}
