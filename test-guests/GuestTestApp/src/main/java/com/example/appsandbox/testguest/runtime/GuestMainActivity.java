package com.example.appsandbox.testguest.runtime;

public final class GuestMainActivity extends android.app.Activity {
    private static int constructionCount;

    public GuestMainActivity() {
        constructionCount++;
    }

    public static int constructionCount() {
        return constructionCount;
    }
}
