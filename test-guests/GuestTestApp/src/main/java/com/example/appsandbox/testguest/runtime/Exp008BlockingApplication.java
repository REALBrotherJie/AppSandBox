package com.example.appsandbox.testguest.runtime;

import android.app.Application;
import java.nio.charset.StandardCharsets;

public final class Exp008BlockingApplication extends Application {
    @Override
    public void onCreate() {
        try (java.io.OutputStream out = openFileOutput("exp008-oncreate-entered", MODE_PRIVATE)) {
            out.write("ENTERED".getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("EXP008 blocking marker failed", error);
        }
        if (getFileStreamPath("exp008-self-terminate").isFile()) {
            Thread terminator = new Thread(() -> {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            }, "exp008-self-terminate");
            terminator.setDaemon(true);
            terminator.start();
        }
        for (;;) {
            try {
                Thread.sleep(60_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
