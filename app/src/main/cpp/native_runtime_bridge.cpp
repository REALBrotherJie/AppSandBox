#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <stdlib.h>

#include <atomic>
#include <mutex>
#include <string>

namespace {

constexpr const char* kTag = "AppSandbox.M9.Native";

struct Binding {
    std::string package_name;
    std::string instance_id;
    std::string credential_root;
    std::string device_root;
    int process_slot = -1;
};

std::mutex g_lock;
Binding g_binding;
std::atomic<bool> g_ready{false};
std::atomic<uint64_t> g_guest_rewrites{0};
thread_local bool g_translation_denied = false;

using OpenFunction = int (*)(const char*, int, ...);
using OpenAtFunction = int (*)(int, const char*, int, ...);

bool needs_mode(int flags) {
    return (flags & O_CREAT) != 0
#ifdef O_TMPFILE
        || (flags & O_TMPFILE) == O_TMPFILE
#endif
        ;
}

bool match_namespace(const std::string& path, const std::string& prefix, std::string* suffix) {
    if (path == prefix) {
        suffix->clear();
        return true;
    }
    if (path.size() > prefix.size() && path.compare(0, prefix.size(), prefix) == 0 &&
        path[prefix.size()] == '/') {
        *suffix = path.substr(prefix.size() + 1);
        return true;
    }
    return false;
}

bool is_logical_namespace(const char* raw) {
    if (raw == nullptr) return false;
    const std::string path(raw);
    std::string suffix;
    return match_namespace(path, "/data/data/" + g_binding.package_name, &suffix) ||
        match_namespace(path, "/data/user/0/" + g_binding.package_name, &suffix) ||
        match_namespace(path, "/data/user_de/0/" + g_binding.package_name, &suffix);
}

std::string translate(const char* raw) {
    g_translation_denied = false;
    if (raw == nullptr || raw[0] != '/' || !g_ready.load(std::memory_order_acquire)) {
        return raw == nullptr ? std::string() : std::string(raw);
    }
    std::lock_guard<std::mutex> guard(g_lock);
    const std::string path(raw);
    std::string suffix;
    const std::string data_data = "/data/data/" + g_binding.package_name;
    const std::string user_zero = "/data/user/0/" + g_binding.package_name;
    const std::string user_de = "/data/user_de/0/" + g_binding.package_name;
    const std::string* root = nullptr;
    if (match_namespace(path, data_data, &suffix) || match_namespace(path, user_zero, &suffix)) {
        root = &g_binding.credential_root;
    } else if (match_namespace(path, user_de, &suffix)) {
        root = &g_binding.device_root;
    } else {
        return path;
    }
    size_t cursor = 0;
    while (cursor <= suffix.size()) {
        const size_t slash = suffix.find('/', cursor);
        const std::string component = suffix.substr(cursor, slash == std::string::npos ? std::string::npos : slash - cursor);
        if (component == "..") {
            errno = EACCES;
            g_translation_denied = true;
            __android_log_print(ANDROID_LOG_WARN, kTag, "PATH_DENY traversal logical=%s", raw);
            return {};
        }
        if (slash == std::string::npos) break;
        cursor = slash + 1;
    }
    std::string translated = *root;
    if (!suffix.empty()) translated += "/" + suffix;
    g_guest_rewrites.fetch_add(1, std::memory_order_relaxed);
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "PATH_REWRITE slot=%d instance=%s logical=%s physical=%s",
        g_binding.process_slot, g_binding.instance_id.c_str(), raw, translated.c_str());
    return translated;
}

std::string reverse_map(const std::string& physical) {
    std::lock_guard<std::mutex> guard(g_lock);
    const auto map_root = [&](const std::string& root, const std::string& logical) -> std::string {
        if (physical == root) return logical;
        if (physical.size() > root.size() && physical.compare(0, root.size(), root) == 0 && physical[root.size()] == '/') {
            return logical + physical.substr(root.size());
        }
        return {};
    };
    std::string mapped = map_root(g_binding.credential_root, "/data/user/0/" + g_binding.package_name);
    if (!mapped.empty()) return mapped;
    mapped = map_root(g_binding.device_root, "/data/user_de/0/" + g_binding.package_name);
    return mapped.empty() ? physical : mapped;
}

bool denied() { return g_translation_denied; }

extern "C" __attribute__((visibility("default"))) int appsandbox_open(const char* path, int flags, ...) {
    mode_t mode = 0;
    if (needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_openat, AT_FDCWD, physical.c_str(), flags, mode));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_openat(int dirfd, const char* path, int flags, ...) {
    mode_t mode = 0;
    if (needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_openat, dirfd, physical.c_str(), flags, mode));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_open_fortify(const char* path, int flags) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_openat, AT_FDCWD, physical.c_str(), flags, 0));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_openat_fortify(int dirfd, const char* path, int flags) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_openat, dirfd, physical.c_str(), flags, 0));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_stat(const char* path, struct stat* out) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_newfstatat, AT_FDCWD, physical.c_str(), out, 0));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_lstat(const char* path, struct stat* out) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_newfstatat, AT_FDCWD, physical.c_str(), out, AT_SYMLINK_NOFOLLOW));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_access(const char* path, int mode) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_faccessat, AT_FDCWD, physical.c_str(), mode));
}

extern "C" __attribute__((visibility("default"))) ssize_t appsandbox_readlink(const char* path, char* buffer, size_t size) {
    const bool logical_input = is_logical_namespace(path);
    const std::string physical = translate(path);
    if (denied()) return -1;
    std::string temporary(size == 0 ? 1 : size, '\0');
    const ssize_t count = static_cast<ssize_t>(syscall(SYS_readlinkat, AT_FDCWD, physical.c_str(), temporary.data(), size));
    if (count < 0) return count;
    const std::string target(temporary.data(), static_cast<size_t>(count));
    const std::string logical = logical_input ? reverse_map(target) : target;
    const size_t copied = logical.size() < size ? logical.size() : size;
    if (copied > 0) memcpy(buffer, logical.data(), copied);
    return static_cast<ssize_t>(copied);
}

extern "C" __attribute__((visibility("default"))) char* appsandbox_realpath(const char* path, char* resolved) {
    const bool logical_input = is_logical_namespace(path);
    const std::string physical = translate(path);
    if (denied()) return nullptr;
    const int fd = static_cast<int>(syscall(SYS_openat, AT_FDCWD, physical.c_str(), O_PATH | O_CLOEXEC, 0));
    if (fd < 0) return nullptr;
    char proc[64];
    snprintf(proc, sizeof(proc), "/proc/self/fd/%d", fd);
    char actual[PATH_MAX];
    const ssize_t count = static_cast<ssize_t>(syscall(SYS_readlinkat, AT_FDCWD, proc, actual, sizeof(actual) - 1));
    const int saved = errno;
    close(fd);
    if (count < 0) { errno = saved; return nullptr; }
    actual[count] = '\0';
    const std::string logical = logical_input ? reverse_map(actual) : actual;
    char* output = resolved == nullptr ? static_cast<char*>(malloc(logical.size() + 1)) : resolved;
    if (output == nullptr) { errno = ENOMEM; return nullptr; }
    memcpy(output, logical.c_str(), logical.size() + 1);
    return output;
}

extern "C" __attribute__((visibility("default"))) int appsandbox_rename(const char* old_path, const char* new_path) {
    const std::string old_physical = translate(old_path);
    if (denied()) return -1;
    const std::string new_physical = translate(new_path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_renameat, AT_FDCWD, old_physical.c_str(), AT_FDCWD, new_physical.c_str()));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_unlink(const char* path) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_unlinkat, AT_FDCWD, physical.c_str(), 0));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_mkdir(const char* path, mode_t mode) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_mkdirat, AT_FDCWD, physical.c_str(), mode));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_rmdir(const char* path) {
    const std::string physical = translate(path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_unlinkat, AT_FDCWD, physical.c_str(), AT_REMOVEDIR));
}

extern "C" __attribute__((visibility("default"))) int appsandbox_symlink(const char* target, const char* link_path) {
    const std::string physical_target = translate(target);
    if (denied()) return -1;
    const std::string physical_link = translate(link_path);
    if (denied()) return -1;
    return static_cast<int>(syscall(SYS_symlinkat, physical_target.c_str(), AT_FDCWD, physical_link.c_str()));
}

bool patch_entry(void* target, void* replacement) {
    if (target == nullptr || replacement == nullptr) return false;
    const long page_size = sysconf(_SC_PAGESIZE);
    const uintptr_t address = reinterpret_cast<uintptr_t>(target);
    void* page = reinterpret_cast<void*>(address & ~static_cast<uintptr_t>(page_size - 1));
    if (mprotect(page, static_cast<size_t>(page_size), PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "mprotect RWX failed target=%p errno=%d", target, errno);
        return false;
    }
#if defined(__aarch64__)
    const uint32_t instructions[] = {0x58000050u, 0xd61f0200u};  // ldr x16, #8; br x16
    memcpy(target, instructions, sizeof(instructions));
    memcpy(reinterpret_cast<void*>(address + sizeof(instructions)), &replacement, sizeof(replacement));
    __builtin___clear_cache(reinterpret_cast<char*>(target), reinterpret_cast<char*>(target) + 16);
#elif defined(__x86_64__)
    unsigned char jump[12] = {0x48, 0xb8};  // movabs rax, replacement; jmp rax
    memcpy(jump + 2, &replacement, sizeof(replacement));
    jump[10] = 0xff;
    jump[11] = 0xe0;
    memcpy(target, jump, sizeof(jump));
    __builtin___clear_cache(reinterpret_cast<char*>(target), reinterpret_cast<char*>(target) + sizeof(jump));
#else
#error Unsupported ABI
#endif
    if (mprotect(page, static_cast<size_t>(page_size), PROT_READ | PROT_EXEC) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "mprotect RX restore failed target=%p errno=%d", target, errno);
        return false;
    }
    return true;
}

bool patch_optional(const char* symbol, void* replacement, bool required = true) {
    void* target = dlsym(RTLD_DEFAULT, symbol);
    const bool ok = target == nullptr ? !required : patch_entry(target, replacement);
    __android_log_print(ok ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, kTag,
        "HOOK_SYMBOL name=%s target=%p installed=%d", symbol, target, ok);
    return ok;
}

bool install_open_hooks() {
    void* open_target = dlsym(RTLD_DEFAULT, "open");
    void* openat_target = dlsym(RTLD_DEFAULT, "openat");
    void* open2_target = dlsym(RTLD_DEFAULT, "__open_2");
    void* openat2_target = dlsym(RTLD_DEFAULT, "__openat_2");
    const bool open_ok = patch_entry(open_target, reinterpret_cast<void*>(appsandbox_open));
    const bool openat_ok = patch_entry(openat_target, reinterpret_cast<void*>(appsandbox_openat));
    const bool open2_ok = open2_target == nullptr || patch_entry(open2_target, reinterpret_cast<void*>(appsandbox_open_fortify));
    const bool openat2_ok = openat2_target == nullptr || patch_entry(openat2_target, reinterpret_cast<void*>(appsandbox_openat_fortify));
    bool path_ok = true;
    path_ok &= patch_optional("stat", reinterpret_cast<void*>(appsandbox_stat));
    path_ok &= patch_optional("lstat", reinterpret_cast<void*>(appsandbox_lstat));
    path_ok &= patch_optional("access", reinterpret_cast<void*>(appsandbox_access));
    path_ok &= patch_optional("readlink", reinterpret_cast<void*>(appsandbox_readlink));
    path_ok &= patch_optional("realpath", reinterpret_cast<void*>(appsandbox_realpath));
    path_ok &= patch_optional("rename", reinterpret_cast<void*>(appsandbox_rename));
    path_ok &= patch_optional("unlink", reinterpret_cast<void*>(appsandbox_unlink));
    path_ok &= patch_optional("mkdir", reinterpret_cast<void*>(appsandbox_mkdir));
    path_ok &= patch_optional("rmdir", reinterpret_cast<void*>(appsandbox_rmdir));
    path_ok &= patch_optional("symlink", reinterpret_cast<void*>(appsandbox_symlink));
    __android_log_print(open_ok && openat_ok && open2_ok && openat2_ok && path_ok ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, kTag,
        "HOOK_INSTALL open=%p/%d openat=%p/%d __open_2=%p/%d __openat_2=%p/%d",
        open_target, open_ok, openat_target, openat_ok, open2_target, open2_ok, openat2_target, openat2_ok);
    return open_ok && openat_ok && open2_ok && openat2_ok && path_ok;
}

std::string utf(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_appsandbox_runtime_NativeRuntimeBridge_nativeBindAndInstall(
    JNIEnv* env, jclass, jstring package_name, jstring instance_id, jint process_slot,
    jstring credential_root, jstring device_root) {
    std::lock_guard<std::mutex> guard(g_lock);
    const Binding requested{utf(env, package_name), utf(env, instance_id), utf(env, credential_root),
        utf(env, device_root), process_slot};
    if (g_ready.load(std::memory_order_acquire)) {
        const bool same = g_binding.package_name == requested.package_name &&
            g_binding.instance_id == requested.instance_id && g_binding.process_slot == requested.process_slot &&
            g_binding.credential_root == requested.credential_root && g_binding.device_root == requested.device_root;
        __android_log_print(same ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, kTag,
            "PROCESS_BIND existing=%s/%s/p%d requested=%s/%s/p%d same=%d",
            g_binding.package_name.c_str(), g_binding.instance_id.c_str(), g_binding.process_slot,
            requested.package_name.c_str(), requested.instance_id.c_str(), requested.process_slot, same);
        return same ? JNI_TRUE : JNI_FALSE;
    }
    if (requested.package_name.empty() || requested.instance_id.empty() || requested.process_slot < 0 ||
        requested.credential_root.empty() || requested.device_root.empty()) {
        return JNI_FALSE;
    }
    g_binding = requested;
    if (!install_open_hooks()) {
        g_binding = Binding{};
        return JNI_FALSE;
    }
    g_ready.store(true, std::memory_order_release);
    __android_log_print(ANDROID_LOG_INFO, kTag,
        "EARLY_NATIVE_IO_READY package=%s instance=%s slot=%d cp=%s dp=%s",
        g_binding.package_name.c_str(), g_binding.instance_id.c_str(), g_binding.process_slot,
        g_binding.credential_root.c_str(), g_binding.device_root.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_appsandbox_runtime_NativeRuntimeBridge_nativeIsReady(JNIEnv*, jclass) {
    return g_ready.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_appsandbox_runtime_NativeRuntimeBridge_nativeGuestRewriteCount(JNIEnv*, jclass) {
    return static_cast<jlong>(g_guest_rewrites.load(std::memory_order_relaxed));
}
