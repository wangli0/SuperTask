package net.ruoxu;

import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Process;

import net.ruoxu.bean.MessageBean;
import net.ruoxu.utils.ExecutorUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wangli on 16/11/24.
 */
public  class SuperTask {

    private final String TAG = getClass().getSimpleName();
    private static final int MESSAGE_POST_RESULT = 0x1;
    private static final int MESSAGE_POST_PROGRESS = 0x2;
    private static Handler sHandler;

    private final AtomicBoolean mCancelled = new AtomicBoolean(); //是否取消了任务
    private final AtomicBoolean mTaskInvoked = new AtomicBoolean(); //任务是否被成功调用


    private volatile Status mStatus = Status.PENDING; //默认状态，等待

    private final FutureTask<Void> mFuture;  //泛型Void是Callable创建的线程返回的结果,这里弃用

    private CallBack mCallBack;

    private static Handler getHandler() {
        synchronized (SuperTask.class) {
            if (sHandler == null) {
                sHandler = new ToggleHandler();
            }
            return sHandler;
        }
    }


    public SuperTask(CallBack callBack){
        this.mCallBack = callBack;

        getHandler();//确保handler在UI线程创建，SuperTask必须在UI线程创建

        mFuture = new FutureTask<Void>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                mTaskInvoked.set(true);
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

                mCallBack.doInBackgroud();
                Binder.flushPendingCommands();
                sendMsgToUIThread();
                return null;  //这个返回结果 仅用于 done()方法 mFuture.get()获取
            }
        }){
            @Override
            protected void done() {
               boolean wasTaskInvokedValue = mTaskInvoked.get();
                if (!wasTaskInvokedValue) { // mTaskInvoked.set(true);未调用即被cancel掉
                    sendMsgToUIThread();
                }

            }
        };
    }

    private void sendMsgToUIThread() {
        MessageBean messageBean = new MessageBean(this);
        Message message = getHandler().obtainMessage(MESSAGE_POST_RESULT, messageBean);
        message.sendToTarget();

    }

    public final SuperTask execute() {

        if (mStatus != Status.PENDING) {

            switch (mStatus) {
                case RUNNING:
                    throw new IllegalStateException("Cannot execute task: the task is already running.");
                case FINISHED:
                    throw new IllegalStateException("Cannot execute task: the task has already been executed (a task can be executed only once)");
            }
        }

        mStatus = Status.RUNNING;

        mCallBack.before();

        ExecutorUtils.defaultExecutor().execute(mFuture);

        return this;

    }

    public void finish() {
        //UI线程
        boolean isCancelled = mCancelled.get();
        if (isCancelled) {
            mCallBack.cancel();
        } else {
            mCallBack.after();
        }
        mStatus = Status.FINISHED;


    }


    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);  //设置标志位 取消Task
        return mFuture.cancel(mayInterruptIfRunning);
    }


}
