package javax.inject;

public interface Provider<T> {
    String FIXTURE_ORIGIN = "r15-guest";
    T get();
}
