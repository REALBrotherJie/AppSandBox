package com.example.appsandbox.testguest.runtime;

import android.app.AppComponentFactory;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

public class Exp003GuestComponentFactory extends AppComponentFactory {
    public static boolean factoryUsed;

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        factoryUsed = true;
        return super.instantiateApplication(cl, className);
    }
}
