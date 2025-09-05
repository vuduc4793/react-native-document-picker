//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package android.os;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** @deprecated */
@Deprecated
public abstract class AsyncTask<Params, Progress, Result> {
    /** @deprecated */
    @Deprecated
    public static final Executor SERIAL_EXECUTOR = null;
    /** @deprecated */
    @Deprecated
    public static final Executor THREAD_POOL_EXECUTOR = null;

    /** @deprecated */
    @Deprecated
    public AsyncTask() {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final Status getStatus() {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected abstract Result doInBackground(Params... var1);

    /** @deprecated */
    @Deprecated
    protected void onPreExecute() {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected void onPostExecute(Result result) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected void onProgressUpdate(Progress... values) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected void onCancelled(Result result) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected void onCancelled() {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final boolean isCancelled() {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final boolean cancel(boolean mayInterruptIfRunning) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final Result get() throws ExecutionException, InterruptedException {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final Result get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final AsyncTask<Params, Progress, Result> execute(Params... params) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public final AsyncTask<Params, Progress, Result> executeOnExecutor(Executor exec, Params... params) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public static void execute(Runnable runnable) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    protected final void publishProgress(Progress... values) {
        throw new RuntimeException("Stub!");
    }

    /** @deprecated */
    @Deprecated
    public static enum Status {
        /** @deprecated */
        @Deprecated
        PENDING,
        /** @deprecated */
        @Deprecated
        RUNNING,
        /** @deprecated */
        @Deprecated
        FINISHED;
    }
}
