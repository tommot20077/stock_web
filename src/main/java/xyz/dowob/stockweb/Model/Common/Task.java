package xyz.dowob.stockweb.Model.Common;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.dowob.stockweb.Enum.TaskStatusType;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author yuan
 */

@Data
@Entity
@Table(name = "server_task_records")
@NoArgsConstructor
public class Task implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "task_start_time")
    private LocalDateTime taskStartTime;

    @Column(name = "task_end_time")
    private LocalDateTime taskEndTime;

    @Column(name = "total_task", nullable = false)
    private int totalTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false)
    private TaskStatusType taskStatus;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;


    @PrePersist
    protected void onCreate() {
        taskStartTime = LocalDateTime.now();
    }

    public Task(String taskId, String taskName, int totalTask) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.totalTask = totalTask;
        this.taskStatus = TaskStatusType.IN_PROGRESS;
    }

    public String getTaskUsageTime() {
        Duration duration;
        if (taskEndTime == null) {
            duration = Duration.between(taskStartTime, LocalDateTime.now());
        } else {
            duration = Duration.between(taskStartTime, taskEndTime);
        }
        Long hours = duration.toHours();
        Long minutes = duration.toMinutes();
        Long seconds = duration.getSeconds();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void completeTask(TaskStatusType status, String message) {
        this.taskEndTime = LocalDateTime.now();
        this.taskStatus = status;
        this.message = message;
    }


}
