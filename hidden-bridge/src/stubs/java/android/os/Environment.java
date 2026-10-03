package android.os;

import java.io.File;

/** Compile-time stub of the hidden members used by hidden-bridge; never packaged. */
public class Environment {
    public static class UserEnvironment {
        public UserEnvironment(int userId) {
            throw new RuntimeException("stub");
        }

        public File[] getExternalDirs() {
            throw new RuntimeException("stub");
        }

        public File[] buildExternalStorageAppDataDirs(String packageName) {
            throw new RuntimeException("stub");
        }

        public File[] buildExternalStorageAppMediaDirs(String packageName) {
            throw new RuntimeException("stub");
        }

        public File[] buildExternalStorageAppObbDirs(String packageName) {
            throw new RuntimeException("stub");
        }

        public File[] buildExternalStorageAppFilesDirs(String packageName) {
            throw new RuntimeException("stub");
        }

        public File[] buildExternalStorageAppCacheDirs(String packageName) {
            throw new RuntimeException("stub");
        }
    }
}
