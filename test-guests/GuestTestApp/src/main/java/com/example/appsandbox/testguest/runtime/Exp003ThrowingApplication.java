package com.example.appsandbox.testguest.runtime;

import android.app.Application;

public final class Exp003ThrowingApplication extends Application {
    public Exp003ThrowingApplication() {
        throw new IllegalStateException("EXP003B0 constructor failure");
    }
}
