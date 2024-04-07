package xyz.dowob.stockweb.Repository.Common;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Enum.TaskStatusType;
import xyz.dowob.stockweb.Model.Common.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByTaskStatus(TaskStatusType taskStatus);
}
