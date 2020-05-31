package os.failsafe.executor;

import os.failsafe.executor.schedule.OneTimeSchedule;
import os.failsafe.executor.schedule.Schedule;
import os.failsafe.executor.task.FailedTask;
import os.failsafe.executor.task.Task;
import os.failsafe.executor.task.TaskExecutionListener;
import os.failsafe.executor.task.TaskId;
import os.failsafe.executor.utils.Database;
import os.failsafe.executor.utils.DefaultSystemClock;
import os.failsafe.executor.utils.NamedThreadFactory;
import os.failsafe.executor.utils.SystemClock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FailsafeExecutor {

    public static final int DEFAULT_WORKER_THREAD_COUNT = 5;
    public static final int DEFAULT_QUEUE_SIZE = DEFAULT_WORKER_THREAD_COUNT * 4;
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(10);
    public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(5);
    public static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofMinutes(12);

    private final Map<String, Task> tasksByIdentifier = new ConcurrentHashMap<>();
    private final Map<String, Schedule> scheduleByIdentifier = new ConcurrentHashMap<>();
    private final List<TaskExecutionListener> listeners = new CopyOnWriteArrayList<>();

    private final OneTimeSchedule oneTimeSchedule = new OneTimeSchedule();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Failsafe-Executor-"));
    private final PersistentQueue persistentQueue;
    private final WorkerPool workerPool;
    private final PersistentTasks persistentTasks;
    private final Duration initialDelay;
    private final Duration pollingInterval;
    private final Database database;
    private final SystemClock systemClock;

    private volatile Exception lastRunException;
    private AtomicBoolean running = new AtomicBoolean();

    public FailsafeExecutor(DataSource dataSource) {
        this(new DefaultSystemClock(), dataSource, DEFAULT_WORKER_THREAD_COUNT, DEFAULT_QUEUE_SIZE, DEFAULT_INITIAL_DELAY, DEFAULT_POLLING_INTERVAL, DEFAULT_LOCK_TIMEOUT);
    }

    public FailsafeExecutor(SystemClock systemClock, DataSource dataSource, int workerThreadCount, int queueSize, Duration initialDelay, Duration pollingInterval, Duration lockTimeout) {
        if (queueSize < workerThreadCount) {
            throw new IllegalArgumentException("QueueSize must be >= workerThreadCount");
        }

        this.database = new Database(dataSource);
        this.systemClock = systemClock;
        this.persistentQueue = new PersistentQueue(database, systemClock, lockTimeout);
        this.workerPool = new WorkerPool(workerThreadCount, queueSize);
        this.initialDelay = initialDelay;
        this.pollingInterval = pollingInterval;
        this.persistentTasks = new PersistentTasks(database, systemClock);
    }

    public void start() {
        boolean shouldStart = running.compareAndSet(false, true);
        if (!shouldStart) {
            return;
        }

        executor.scheduleWithFixedDelay(
                this::executeNextTasks,
                initialDelay.toMillis(), pollingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void executeNextTasks() {
        for (;;) if (executeNextTask() == null) break;
    }

    public void stop() {
        this.workerPool.stop();
        executor.shutdown();
        running.set(false);
    }

    public TaskId execute(Task task) {
        return execute(task, null);
    }

    public TaskId execute(Task task, String parameter) {
        return database.connect(connection -> execute(connection, task, parameter));
    }

    public TaskId execute(Connection connection, Task task) {
        return execute(connection, task, null);
    }

    public TaskId execute(Connection connection, Task task, String parameter) {
        TaskInstance taskInstance = new TaskInstance(task.getName(), parameter, systemClock.now());
        return enqueue(connection, task, taskInstance);
    }

    public TaskId schedule(Task task, Schedule schedule) {
        return database.connect(connection -> schedule(connection, task, schedule));
    }

    public TaskId schedule(Connection connection, Task task, Schedule schedule) {
        scheduleByIdentifier.put(task.getName(), schedule);
        LocalDateTime plannedExecutionTime = schedule.nextExecutionTime(systemClock.now())
                .orElseThrow(() -> new IllegalArgumentException("Schedule must return at least one execution time"));

        TaskInstance taskInstance = new TaskInstance(task.getName(), task.getName(), null, plannedExecutionTime);
        return enqueue(connection, task, taskInstance);
    }

    public List<FailedTask> failedTasks() {
        return persistentTasks.failedTasks();
    }

    public Optional<FailedTask> failedTask(TaskId taskId) {
        return persistentTasks.failedTask(taskId);
    }

    public void subscribe(TaskExecutionListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(TaskExecutionListener listener) {
        listeners.remove(listener);
    }

    public boolean isLastRunFailed() {
        return lastRunException != null;
    }

    public Exception lastRunException() {
        return lastRunException;
    }

    private TaskId enqueue(Connection connection, Task task, TaskInstance taskInstance) {
        if (!tasksByIdentifier.containsKey(task.getName())) {
            tasksByIdentifier.put(task.getName(), task);
        }

        return persistentQueue.add(connection, taskInstance);
    }

    private Future<TaskId> executeNextTask() {
        try {
            if (workerPool.allWorkersBusy()) {
                return null;
            }

            PersistentTask toExecute = persistentQueue.peekAndLock();

            if (toExecute == null) {
                return null;
            }

            Task task = tasksByIdentifier.get(toExecute.getName());
            Schedule schedule = scheduleByIdentifier.getOrDefault(task.getName(), oneTimeSchedule);
            Execution execution = new Execution(task, toExecute, listeners, schedule, systemClock);

            Future<TaskId> taskIdFuture = workerPool.execute(execution);

            clearException();

            return taskIdFuture;
        } catch (Exception e) {
            storeException(e);
        }

        return null;
    }

    private void storeException(Exception e) {
        lastRunException = e;
    }

    private void clearException() {
        lastRunException = null;
    }
}
