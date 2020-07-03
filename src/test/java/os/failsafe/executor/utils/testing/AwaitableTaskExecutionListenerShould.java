package os.failsafe.executor.utils.testing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwaitableTaskExecutionListenerShould {

    @Test
    void not_throw_an_exception_if_no_parties_have_registered() {
        assertDoesNotThrow(() -> new AwaitableTaskExecutionListener(1, TimeUnit.MINUTES).awaitAllTasks());
    }

    @Test
    void not_block_if_no_parties_have_registered() throws InterruptedException {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.MINUTES);

        CountDownLatch countDownLatch = new CountDownLatch(1);

        executeInThread(() -> {
            listener.awaitAllTasks();
            countDownLatch.countDown();
        });

        countDownLatch.await(100, TimeUnit.MILLISECONDS);
    }

    @Test
    void block_on_persisted_task_and_throw_timeout() {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.NANOSECONDS);
        listener.persisting("TaskName", "taskId", "parameter");

        RuntimeException thrown = assertThrows(RuntimeException.class, listener::awaitAllTasks);
        assertEquals("Only 0/1 tasks finished! Waiting for: {taskId=TaskName#parameter}", thrown.getMessage());
    }

    @Test
    void block_on_retrying_task_and_throw_timeout() {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.NANOSECONDS);
        listener.retrying("TaskName", "taskId", "parameter");

        assertThrows(RuntimeException.class, listener::awaitAllTasks);
    }

    @Test
    void release_block_when_task_fails() throws InterruptedException {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.SECONDS);
        listener.persisting("TaskName", "taskId", "parameter");

        CountDownLatch countDownLatch = new CountDownLatch(1);

        executeInThread(() -> {
            listener.awaitAllTasks();
            countDownLatch.countDown();
        });

        listener.failed("TaskName", "taskId", "parameter", new Exception());
        countDownLatch.await(1, TimeUnit.SECONDS);
    }

    @Test
    void release_block_when_retried_task_succeeds() throws InterruptedException {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.SECONDS);
        listener.retrying("TaskName", "taskId", "parameter");

        CountDownLatch countDownLatch = new CountDownLatch(1);

        executeInThread(() -> {
            listener.awaitAllTasks();
            countDownLatch.countDown();
        });

        listener.succeeded("TaskName", "taskId", "parameter");
        countDownLatch.await(1, TimeUnit.SECONDS);
    }

    @Test
    void return_all_failed_tasks_by_id() {
        AwaitableTaskExecutionListener listener = new AwaitableTaskExecutionListener(1, TimeUnit.SECONDS);
        listener.persisting("TaskName", "taskId", "parameter");
        listener.failed("TaskName", "taskId", "parameter", new Exception());

        assertTrue(listener.isAnyExecutionFailed());
        assertEquals(1, listener.failedTasksByIds().size());
        assertTrue(listener.failedTasksByIds().contains("taskId"));
    }


    private void executeInThread(Runnable runnable) {
        new Thread(runnable).start();
    }
}
