package com.example.appsandbox.testguest.runtime;

public class Exp003OnCreateThrowingApplication extends android.app.Application {
    public static boolean throwOnCreate = true;
    @Override public void onCreate() {
        if (throwOnCreate) throw new RuntimeException("EXP003C onCreate failure");
    }
}
