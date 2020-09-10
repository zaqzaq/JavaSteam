package cn.freshz.javasteam.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {

    private final static ExecutorService EXECUTORS= new ThreadPoolExecutor(100, 100,
            5L, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>());


    public static void async(Runnable runnable){
        EXECUTORS.submit(runnable);
    }

}
