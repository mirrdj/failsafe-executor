package os.failsafe.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import os.failsafe.executor.schedule.OneTimeSchedule;
import os.failsafe.executor.task.TaskId;
import os.failsafe.executor.utils.TestSystemClock;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerPoolShould {

    private static final TestSystemClock systemClock = new TestSystemClock();

    private int threadCount = 2;
    private int queueSize = threadCount * 2;
    private WorkerPool workerPool;

    @BeforeEach
    void init() {
        workerPool = new WorkerPool(threadCount, queueSize);
    }

    @AfterEach
    void stop() {
        workerPool.stop();
    }

    @Test
    void accept_more_tasks_if_workers_are_idle() {
        assertFalse(workerPool.allWorkersBusy());
    }

    @Test
    void not_accept_more_tasks_if_all_workers_are_busy() throws InterruptedException, ExecutionException {
        BlockingExecution firstBlockingExecution = new BlockingExecution();
        Future<TaskId> execution = workerPool.execute(firstBlockingExecution);

        List<BlockingExecution> blockingExecutions = IntStream.range(1, queueSize)
                .mapToObj(i -> new BlockingExecution())
                .peek(workerPool::execute)
                .collect(Collectors.toList());

        assertTrue(workerPool.allWorkersBusy());

        firstBlockingExecution.release();
        execution.get();

        assertFalse(workerPool.allWorkersBusy());

        BlockingExecution nextBlockingExecution = new BlockingExecution();
        workerPool.execute(nextBlockingExecution);

        assertTrue(workerPool.allWorkersBusy());

        nextBlockingExecution.release();
        blockingExecutions.forEach(BlockingExecution::release);
    }

    static class BlockingExecution extends Execution {
        Phaser phaser;

        BlockingExecution() {
            super(null, null, Collections.emptyList(), new OneTimeSchedule(), systemClock, null);
            phaser = new Phaser(2);
        }

        @Override
        public TaskId perform() {
            phaser.arriveAndAwaitAdvance();
            return new TaskId("id");
        }

        public void release() {
            phaser.arrive();
        }
    }
}
