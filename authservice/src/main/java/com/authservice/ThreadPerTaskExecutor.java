//package com.authservice;
package com.authservice.server;

import java.util.concurrent.Executor;

public class ThreadPerTaskExecutor implements Executor {
    @Override
    public void execute(Runnable r) {
        new Thread(r).start();
    }
}