package com.example.appsandbox.testguest.runtime;

import com.example.appsandbox.testguest.R;

/**
 * EXP-002 only: exposes compiled resource IDs from inside the Guest APK.
 * Host code resolves this class at runtime and never compiles against Guest R.
 */
public final class Exp002ResourceIds {
    private Exp002ResourceIds() {
    }

    public static int stringId() {
        return R.string.exp002_string;
    }

    public static int rawId() {
        return R.raw.exp002_payload;
    }

    public static int colorId() {
        return R.color.exp002_color;
    }

    public static int drawableId() {
        return R.drawable.exp002_drawable;
    }

    public static int layoutId() {
        return R.layout.exp002_test_layout;
    }

    public static int configValueId() {
        return R.string.exp002_config_value;
    }

    public static int collisionId() {
        return R.string.exp002_collision;
    }
}
