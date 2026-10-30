#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

static const char *GUEST_BASH = "/data/data/com.termux/files/usr/bin/bash";

static void mkdir_p(const char *path) {
    char buffer[PATH_MAX * 2];
    size_t length = strlen(path);
    if (length == 0 || length >= sizeof(buffer)) return;
    memcpy(buffer, path, length + 1);
    for (char *cursor = buffer + 1; *cursor != '\0'; cursor++) {
        if (*cursor != '/') continue;
        *cursor = '\0';
        mkdir(buffer, 0777);
        *cursor = '/';
    }
    mkdir(buffer, 0777);
}

int main(int argc, char **argv) {
    const char *files = getenv("CODECELL_FILES");
    const char *native = getenv("CODECELL_NATIVE");
    if (files == NULL || native == NULL) {
        execv("/system/bin/sh", argv);
        perror("execv /system/bin/sh");
        return 127;
    }

    char root[PATH_MAX];
    char proot[PATH_MAX];
    char loader[PATH_MAX];
    char tmp[PATH_MAX];
    char libs[PATH_MAX];
    snprintf(root, sizeof(root), "%s/termux", files);
    snprintf(proot, sizeof(proot), "%s/libproot.so", native);
    snprintf(loader, sizeof(loader), "%s/libprootloader.so", native);
    snprintf(tmp, sizeof(tmp), "%s/termux-tmp", files);
    snprintf(libs, sizeof(libs), "%s/termux-libs", files);

    if (access(proot, X_OK) != 0) {
        execv("/system/bin/sh", argv);
        perror("execv /system/bin/sh");
        return 127;
    }

    char cwd[PATH_MAX];
    if (getcwd(cwd, sizeof(cwd)) == NULL) {
        snprintf(cwd, sizeof(cwd), "/");
    }
    char guest_cwd[PATH_MAX * 2];
    snprintf(guest_cwd, sizeof(guest_cwd), "%s%s", root, cwd);
    mkdir_p(guest_cwd);

    char library_path[PATH_MAX * 2];
    snprintf(library_path, sizeof(library_path), "%s:%s", libs, native);
    unsetenv("LD_PRELOAD");
    unsetenv("LD_DEBUG");
    setenv("PROOT_LOADER", loader, 1);
    setenv("PROOT_TMP_DIR", tmp, 1);
    setenv("LD_LIBRARY_PATH", library_path, 1);

    size_t capacity = (size_t)argc + 64;
    char **args = calloc(capacity, sizeof(char *));
    if (args == NULL) return 127;

    size_t index = 0;
    args[index++] = proot;
    args[index++] = "--link2symlink";
    args[index++] = "-i";
    args[index++] = "10287";
    args[index++] = "-r";
    args[index++] = root;

    const char *binds[] = {
        "/dev", "/proc", "/sys", "/apex", "/system", "/vendor", "/storage", "/sdcard",
    };
    for (size_t i = 0; i < sizeof(binds) / sizeof(binds[0]); i++) {
        args[index++] = "-b";
        args[index++] = (char *)binds[i];
    }

    if (access("/linkerconfig/ld.config.txt", R_OK) == 0) {
        char link_dir[PATH_MAX * 2];
        char link_file[PATH_MAX * 2];
        snprintf(link_dir, sizeof(link_dir), "%s/linkerconfig", root);
        mkdir_p(link_dir);
        snprintf(link_file, sizeof(link_file), "%s/ld.config.txt", link_dir);
        FILE *handle = fopen(link_file, "w");
        if (handle != NULL) {
            fclose(handle);
            args[index++] = "-b";
            args[index++] = "/linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt";
        }
    }

    args[index++] = "-w";
    args[index++] = cwd;
    args[index++] = "/data/data/com.termux/files/usr/bin/env";
    args[index++] = "-i";
    args[index++] = "HOME=/data/data/com.termux/files/home";
    args[index++] = "PREFIX=/data/data/com.termux/files/usr";
    args[index++] = "TERMUX__PREFIX=/data/data/com.termux/files/usr";
    args[index++] = "PATH=/data/data/com.termux/files/usr/bin";
    args[index++] = "TMPDIR=/data/data/com.termux/files/usr/tmp";
    args[index++] = "LANG=C.UTF-8";
    args[index++] = "TERM=xterm-256color";
    args[index++] = "DEBIAN_FRONTEND=noninteractive";
    args[index++] = "APT_LISTCHANGES_FRONTEND=none";
    args[index++] = "NEEDRESTART_MODE=a";
    args[index++] = "GIT_TERMINAL_PROMPT=0";
    args[index++] = "PIP_NO_INPUT=1";
    args[index++] = "CI=1";
    args[index++] = "ANDROID_ROOT=/system";
    args[index++] = "ANDROID_DATA=/data";
    args[index++] = "EXTERNAL_STORAGE=/sdcard";
    args[index++] = "LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib";
    args[index++] = (char *)GUEST_BASH;
    for (int i = 1; i < argc && index + 1 < capacity; i++) {
        args[index++] = argv[i];
    }
    args[index] = NULL;

    execv(proot, args);
    perror("execv proot");
    execv("/system/bin/sh", argv);
    perror("execv /system/bin/sh");
    return 127;
}
