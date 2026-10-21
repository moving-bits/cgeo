package cgeo.geocaching.utils.offlinetranslate;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

/** A cancellable translation which never interrupts an already running native call. */
final class TranslationTask implements Disposable {
    private final CompositeDisposable owner;
    private final Scheduler scheduler;
    private final Scheduler callbackScheduler;
    private final TranslationQueue queue;
    private Callable<String> action;
    private Consumer<String> onSuccess;
    private Consumer<Exception> onError;
    private Disposable work;
    private Disposable delivery;
    private boolean running;
    private boolean callbackStarted;
    private boolean disposed;

    private TranslationTask(final CompositeDisposable owner, final Scheduler scheduler, final Scheduler callbackScheduler, final TranslationQueue queue,
                            final Callable<String> action, final Consumer<String> onSuccess, final Consumer<Exception> onError) {
        this.owner = owner;
        this.scheduler = scheduler;
        this.callbackScheduler = callbackScheduler;
        this.queue = queue;
        this.action = action;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    static Disposable schedule(final CompositeDisposable owner, final Scheduler scheduler, final Scheduler callbackScheduler,
                               final Callable<String> action, final Consumer<String> onSuccess, final Consumer<Exception> onError) {
        return schedule(owner, scheduler, callbackScheduler, action, onSuccess, onError, 0, TimeUnit.MILLISECONDS);
    }

    static Disposable schedule(final CompositeDisposable owner, final Scheduler scheduler, final Scheduler callbackScheduler,
                               final Callable<String> action, final Consumer<String> onSuccess, final Consumer<Exception> onError,
                               final long delay, final TimeUnit unit) {
        return schedule(owner, scheduler, callbackScheduler, null, action, onSuccess, onError, delay, unit);
    }

    static Disposable schedule(final CompositeDisposable owner, final Scheduler scheduler, final Scheduler callbackScheduler,
                               final TranslationQueue queue, final Callable<String> action,
                               final Consumer<String> onSuccess, final Consumer<Exception> onError) {
        return schedule(owner, scheduler, callbackScheduler, queue, action, onSuccess, onError, 0, TimeUnit.MILLISECONDS);
    }

    static Disposable schedule(final CompositeDisposable owner, final Scheduler scheduler, final Scheduler callbackScheduler,
                               final TranslationQueue queue, final Callable<String> action,
                               final Consumer<String> onSuccess, final Consumer<Exception> onError, final long delay, final TimeUnit unit) {
        final TranslationTask task = new TranslationTask(owner, scheduler, callbackScheduler, queue, action, onSuccess, onError);
        if (owner.add(task) && (queue == null || queue.add(task))) {
            task.scheduleWork(delay, unit);
        }
        return task;
    }

    void scheduleWork(final long delay, final TimeUnit unit) {
        if (isDisposed()) {
            return;
        }
        final Disposable scheduled = scheduler.scheduleDirect(this::execute, delay, unit);
        synchronized (this) {
            if (disposed && !running) {
                scheduled.dispose();
            } else {
                work = scheduled;
            }
        }
    }

    synchronized Callable<String> claim() {
        if (disposed || action == null) {
            return null;
        }
        running = true;
        final Callable<String> translate = action;
        action = null;
        return translate;
    }

    private void execute() {
        final Callable<String> translate = queue == null ? claim() : queue.startOrDefer(this);
        if (translate == null) {
            return;
        }
        String result = null;
        Exception failure = null;
        try {
            result = translate.call();
        } catch (final Throwable error) {
            if (error instanceof VirtualMachineError || error instanceof ThreadDeath) {
                dispose();
                throw (Error) error;
            }
            failure = error instanceof Exception ? (Exception) error : new RuntimeException(error);
        } finally {
            synchronized (this) {
                running = false;
                work = null;
            }
        }
        final String translated = result;
        final Exception translationError = failure;
        synchronized (this) {
            if (disposed) {
                return;
            }
        }
        final Disposable callback = callbackScheduler.scheduleDirect(() -> complete(translated, translationError));
        synchronized (this) {
            if (disposed && !callbackStarted) {
                callback.dispose();
            } else if (!disposed) {
                delivery = callback;
            }
        }
    }

    private void complete(final String result, final Exception failure) {
        final Consumer<String> success;
        final Consumer<Exception> error;
        synchronized (this) {
            if (disposed) {
                return;
            }
            callbackStarted = true;
            success = onSuccess;
            error = onError;
            disposed = true;
            onSuccess = null;
            onError = null;
            delivery = null;
        }
        owner.delete(this);
        if (queue != null) {
            queue.remove(this);
        }
        // Invoke application callbacks outside the task lock to avoid lock inversion on abort.
        if (failure == null) {
            success.accept(result);
        } else {
            error.accept(failure);
        }
    }

    @Override
    public void dispose() {
        synchronized (this) {
            disposed = true;
            action = null;
            onSuccess = null;
            onError = null;
            if (!running && work != null) {
                work.dispose();
            }
            if (!callbackStarted && delivery != null) {
                delivery.dispose();
            }
            work = null;
            delivery = null;
        }
        owner.delete(this);
        if (queue != null) {
            queue.remove(this);
        }
    }

    @Override
    public synchronized boolean isDisposed() {
        return disposed;
    }
}
