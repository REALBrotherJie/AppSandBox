package com.example.appsandbox.hiddenbridge;

import android.os.Environment;

import java.io.File;

/**
 * Replacement for {@code Environment.sCurrentUser}: every ContextImpl builds its app-specific
 * external directories through these methods, so mapping them here covers Application, Activity,
 * Service and Provider contexts alike.
 */
public final class MappedUserEnvironment extends Environment.UserEnvironment {
    public interface PathMapper {
        File[] map(String packageName, File[] dirs);
    }

    private final PathMapper mapper;

    public MappedUserEnvironment(int userId, PathMapper mapper) {
        super(userId);
        this.mapper = mapper;
    }

    @Override
    public File[] buildExternalStorageAppDataDirs(String packageName) {
        return mapper.map(packageName, super.buildExternalStorageAppDataDirs(packageName));
    }

    @Override
    public File[] buildExternalStorageAppMediaDirs(String packageName) {
        return mapper.map(packageName, super.buildExternalStorageAppMediaDirs(packageName));
    }

    @Override
    public File[] buildExternalStorageAppObbDirs(String packageName) {
        return mapper.map(packageName, super.buildExternalStorageAppObbDirs(packageName));
    }

    @Override
    public File[] buildExternalStorageAppFilesDirs(String packageName) {
        return mapper.map(packageName, super.buildExternalStorageAppFilesDirs(packageName));
    }

    @Override
    public File[] buildExternalStorageAppCacheDirs(String packageName) {
        return mapper.map(packageName, super.buildExternalStorageAppCacheDirs(packageName));
    }
}
