package com.example.appsandbox.testguest.runtime;

public final class GuestProbe {
    public GuestProbe() {
    }

    public String ping(String input) {
        return GuestHelper.appendMarker("guest:" + input);
    }
}
