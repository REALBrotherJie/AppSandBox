package com.example.zeroadapt;

import java.io.Serializable;

public final class TestSerializable implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String value;
    public TestSerializable(String value) { this.value = value; }
}
