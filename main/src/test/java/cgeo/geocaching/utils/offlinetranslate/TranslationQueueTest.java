package cgeo.geocaching.utils.offlinetranslate;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class TranslationQueueTest {
    @Test
    public void waitsUntilFirstResumeAndUsesTheWorkerToContinue() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final List<String> starts = new ArrayList<>();
        submit(queue, new CompositeDisposable(), scheduler, "first", starts);
        scheduler.triggerActions();
        assertThat(starts).isEmpty();
        queue.resume();
        assertThat(starts).isEmpty();
        scheduler.triggerActions();
        assertThat(starts).containsExactly("first");
    }

    @Test
    public void pausesRequestsAlreadySubmittedToTheWorker() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final List<String> starts = new ArrayList<>();
        queue.resume();
        submit(queue, new CompositeDisposable(), scheduler, "first", starts);
        queue.pause();
        scheduler.triggerActions();
        assertThat(starts).isEmpty();
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).containsExactly("first");
    }

    @Test
    public void activityInstancesRemainIndependentEvenForIdenticalText() {
        final TranslationQueue firstActivity = new TranslationQueue();
        final TranslationQueue secondActivity = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final List<String> firstStarts = new ArrayList<>();
        final List<String> secondStarts = new ArrayList<>();
        // No geocode or text key participates in queue ownership.
        submit(firstActivity, new CompositeDisposable(), scheduler, "same cache text", firstStarts);
        submit(secondActivity, new CompositeDisposable(), scheduler, "same cache text", secondStarts);
        secondActivity.resume();
        scheduler.triggerActions();
        assertThat(firstStarts).isEmpty();
        assertThat(secondStarts).containsExactly("same cache text");
        secondActivity.pause();
        firstActivity.resume();
        scheduler.triggerActions();
        assertThat(firstStarts).containsExactly("same cache text");
        assertThat(secondStarts).containsExactly("same cache text");
    }

    @Test
    public void cancelledDeferredRequestIsNotResurrected() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> starts = new ArrayList<>();
        final Disposable task = submit(queue, owner, scheduler, "cancelled", starts);
        scheduler.triggerActions();
        task.dispose();
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).isEmpty();
        assertThat(owner.size()).isZero();
    }

    @Test
    public void sessionAbortDoesNotDisposeTheActivityQueue() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable session = new CompositeDisposable();
        final List<String> starts = new ArrayList<>();
        submit(queue, session, scheduler, "cancelled session", starts);
        scheduler.triggerActions();
        session.dispose();
        submit(queue, new CompositeDisposable(), scheduler, "new session", starts);
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).containsExactly("new session");
        assertThat(queue.isDisposed()).isFalse();
    }

    @Test
    public void destructionCancelsUnstartedRequestsAndRejectsNewOnes() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final CompositeDisposable owner = new CompositeDisposable();
        final List<String> starts = new ArrayList<>();
        queue.resume();
        submit(queue, owner, scheduler, "queued before destruction", starts);
        queue.dispose();
        final Disposable rejected = submit(queue, owner, scheduler, "after destruction", starts);
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).isEmpty();
        assertThat(owner.size()).isZero();
        assertThat(rejected.isDisposed()).isTrue();
    }

    @Test
    public void repeatedPauseAndResumeStartsEachRequestOnlyOnce() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final List<String> starts = new ArrayList<>();
        submit(queue, new CompositeDisposable(), scheduler, "first", starts);
        scheduler.triggerActions();
        queue.resume();
        queue.pause();
        scheduler.triggerActions();
        assertThat(starts).isEmpty();
        queue.resume();
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).containsExactly("first");
    }

    @Test
    public void queueRejectsRequestsCancelledBeforeRegistration() {
        final TestScheduler scheduler = new TestScheduler();
        final TranslationTask task = (TranslationTask) TranslationTask.schedule(new CompositeDisposable(), scheduler, scheduler,
                () -> "cancelled", result -> { }, error -> { });
        task.dispose();
        final TranslationQueue queue = new TranslationQueue();
        assertThat(queue.add(task)).isFalse();
        queue.resume();
        scheduler.triggerActions();
        assertThat(task.isDisposed()).isTrue();
    }

    @Test
    public void runningRequestCanCompleteAndDeliverItsResultWhilePaused() {
        final TranslationQueue queue = new TranslationQueue();
        final TestScheduler scheduler = new TestScheduler();
        final List<String> starts = new ArrayList<>();
        final List<String> results = new ArrayList<>();
        queue.resume();
        TranslationTask.schedule(new CompositeDisposable(), scheduler, scheduler, queue, () -> {
            starts.add("first");
            queue.pause();
            return "finished in background";
        }, results::add, error -> results.add("error"));
        submit(queue, new CompositeDisposable(), scheduler, "second", starts);
        scheduler.triggerActions();
        assertThat(starts).containsExactly("first");
        assertThat(results).containsExactly("finished in background");
        queue.resume();
        scheduler.triggerActions();
        assertThat(starts).containsExactly("first", "second");
    }

    @Test(timeout = 15000)
    public void pausedActivityDoesNotBlockAnotherActivityOnSharedWorker() throws InterruptedException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Scheduler worker = Schedulers.from(executor);
        final TranslationQueue firstActivity = new TranslationQueue();
        final TranslationQueue secondActivity = new TranslationQueue();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch otherFinished = new CountDownLatch(1);
        final CountDownLatch resumedFinished = new CountDownLatch(1);
        final Queue<String> starts = new ConcurrentLinkedQueue<>();
        final AtomicBoolean interrupted = new AtomicBoolean();
        final CompositeDisposable owner = new CompositeDisposable();
        firstActivity.resume();
        secondActivity.resume();
        try {
            TranslationTask.schedule(owner, worker, Schedulers.trampoline(), firstActivity, () -> {
                starts.add("running");
                started.countDown();
                try {
                    release.await();
                } catch (final InterruptedException error) {
                    interrupted.set(true);
                }
                return "done";
            }, result -> { }, error -> { });
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            TranslationTask.schedule(owner, worker, Schedulers.trampoline(), firstActivity, () -> {
                starts.add("resumed");
                return "done";
            }, result -> resumedFinished.countDown(), error -> { });
            TranslationTask.schedule(owner, worker, Schedulers.trampoline(), secondActivity, () -> {
                starts.add("other activity");
                return "done";
            }, result -> otherFinished.countDown(), error -> { });
            firstActivity.pause();
            release.countDown();
            assertThat(otherFinished.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.get()).isFalse();
            assertThat(starts).containsExactly("running", "other activity");
            firstActivity.resume();
            assertThat(resumedFinished.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(starts).containsExactly("running", "other activity", "resumed");
        } finally {
            release.countDown();
            firstActivity.dispose();
            secondActivity.dispose();
            owner.dispose();
            executor.shutdownNow();
        }
    }

    private static Disposable submit(final TranslationQueue queue, final CompositeDisposable owner, final TestScheduler scheduler,
                                     final String text, final List<String> starts) {
        return TranslationTask.schedule(owner, scheduler, scheduler, queue, () -> {
            starts.add(text);
            return text;
        }, result -> { }, error -> starts.add("error"));
    }
}
