#include "native/core/advanced.h"
#include <pthread.h>
#include <stdio.h>
#include <string.h>

/* Explicit allowlist: UI cannot alter arbitrary Android properties/environment. */
static struct { const char *name; char value[92]; bool set; } props[] = {
    {"debug.hakux.fex_x87"}, {"debug.hakux.fex_pc"}, {"debug.hakux.fex_afp"},
    {"debug.hakux.quirks"}, {"debug.hakux.present"}, {"debug.hakux.perf"},
    {"debug.hakux.perfsec"}, {"debug.hakux.memscan"}, {"debug.hakux.texdump"},
    {"debug.hakux.rtdump"}
};
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static char report[32768];
static size_t used;
static char memory[16][92];
static unsigned head, count;

int nc_advanced_get(const char *name, char *value)
{
    if (!name || !value) return -1;
    int n = -1;
    pthread_mutex_lock(&lock);
    for (size_t i = 0; i < sizeof(props)/sizeof(props[0]); i++) {
        if (props[i].set && !strcmp(name, props[i].name)) {
            strcpy(value, props[i].value);
            n = (int)strlen(value);
            break;
        }
    }
    pthread_mutex_unlock(&lock);
    return n;
}

bool nc_advanced_set(const char *name, const char *value)
{
    if (!name || !value || strlen(value) >= sizeof(props[0].value)) return false;
    bool ok = false;
    pthread_mutex_lock(&lock);
    if (!strcmp(name, "debug.hakux.memscan")) {
        if (count < 16) {
            strcpy(memory[(head + count) % 16], value);
            count++;
            ok = true;
        }
        pthread_mutex_unlock(&lock);
        return ok;
    }
    for (size_t i = 0; i < sizeof(props)/sizeof(props[0]); i++) {
        if (!strcmp(name, props[i].name)) {
            strcpy(props[i].value, value);
            props[i].set = true;
            ok = true;
            break;
        }
    }
    pthread_mutex_unlock(&lock);
    return ok;
}

bool nc_advanced_take_memory(char *value)
{
    pthread_mutex_lock(&lock);
    bool ok = count != 0;
    if (ok) {
        strcpy(value, memory[head]);
        head = (head + 1) % 16;
        count--;
    }
    pthread_mutex_unlock(&lock);
    return ok;
}

void nc_advanced_log(const char *tag, const char *line)
{
    if (strcmp(tag, "hakuX-native-scan")) return;
    pthread_mutex_lock(&lock);
    size_t len = strlen(line);
    if (len > sizeof(report) - 2) len = sizeof(report) - 2;
    while (used + len + 2 > sizeof(report)) {
        char *end = memchr(report, '\n', used);
        size_t drop = end ? (size_t)(end - report) + 1 : used;
        memmove(report, report + drop, used - drop);
        used -= drop;
    }
    memcpy(report + used, line, len);
    used += len;
    report[used++] = '\n'; report[used] = 0;
    pthread_mutex_unlock(&lock);
}

void nc_advanced_report(char *out, size_t cap)
{
    if (!cap) return;
    pthread_mutex_lock(&lock);
    snprintf(out, cap, "%s", report);
    pthread_mutex_unlock(&lock);
}
