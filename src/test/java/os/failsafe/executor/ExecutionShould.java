package os.failsafe.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import os.failsafe.executor.schedule.OneTimeSchedule;
import os.failsafe.executor.task.Task;
import os.failsafe.executor.task.TaskExecutionListener;
import os.failsafe.executor.task.TaskId;
import os.failsafe.executor.utils.TestSystemClock;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExecutionShould {

    private final TestSystemClock systemClock = new TestSystemClock();
    private Execution execution;
    private TaskExecutionListener listener;
    private Task task;
    private PersistentTask persistentTask;
    private OneTimeSchedule oneTimeSchedule;
    private final TaskId taskId = new TaskId("123");
    private final String parameter = "Hello world!";
    private final String taskName = "TestTask";

    @BeforeEach
    void init() {
        task = Mockito.mock(Task.class);

        oneTimeSchedule = Mockito.mock(OneTimeSchedule.class);
        when(oneTimeSchedule.nextExecutionTime(any())).thenReturn(Optional.empty());

        persistentTask = Mockito.mock(PersistentTask.class);
        when(persistentTask.getId()).thenReturn(taskId);
        when(persistentTask.getParameter()).thenReturn(parameter);
        when(persistentTask.getName()).thenReturn(taskName);

        listener = Mockito.mock(TaskExecutionListener.class);

        execution = new Execution(task, persistentTask, Collections.singletonList(listener), oneTimeSchedule, systemClock);
    }

    @Test
    void
    execute_task_with_parameter() {
        execution.perform();

        verify(task).run(parameter);
    }

    @Test
    void
    notify_listeners_after_successful_execution() {
        execution.perform();

        verify(listener).succeeded(taskName, taskId, parameter);
    }

    @Test void
    remove_task_after_successful_execution() {
        execution.perform();

        verify(persistentTask).remove();
    }

    @Test void
    notify_listeners_after_failed_execution() {
        RuntimeException exception = new RuntimeException();
        doThrow(exception).when(task).run(any());

        execution.perform();

        verify(listener).failed(taskName, taskId, parameter);
    }

    @Test void
    not_remove_but_set_next_execution_time_of_the_task_if_one_is_available() {
        LocalDateTime nextPlannedExecutionTime = systemClock.now().plusDays(1);
        when(oneTimeSchedule.nextExecutionTime(any())).thenReturn(Optional.of(nextPlannedExecutionTime));

        execution.perform();

        verify(persistentTask).nextExecution(nextPlannedExecutionTime);
        verify(persistentTask, never()).remove();
    }

}
