#include "native/core/advanced.h"
#include "hw/xbox/hle/prop.h"
#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
static void *writer(void *arg) {
    for (int i = 0; i < 20000; i++) assert(nc_advanced_set("debug.hakux.present", i % 2 ? "sgsr fxaa" : "bilinear nofxaa"));
    return NULL;
}
int main(void) {
    char value[92], out[32768], long_value[100];
    assert(nc_advanced_get("debug.hakux.present", value) == -1);
    assert(nc_advanced_get(NULL, value) == -1);
    assert(!nc_advanced_set("arbitrary.property", "1"));
    memset(long_value, 'x', 99); long_value[99] = 0;
    assert(!nc_advanced_set("debug.hakux.present", long_value));
    assert(nc_advanced_set("debug.hakux.present", ""));
    assert(nc_advanced_get("debug.hakux.present", value) == 0 && !value[0]);
    assert(nc_advanced_set("debug.hakux.present", "sgsr fxaa"));
    HleProp command = {0};
    assert(nc_advanced_set("debug.hakux.perf", "first"));
    assert(hle_prop_new(&command, "debug.hakux.perf", value));
    assert(!hle_prop_new(&command, "debug.hakux.perf", value));
    assert(nc_advanced_set("debug.hakux.perf", "second"));
    assert(hle_prop_new(&command, "debug.hakux.perf", value));
    assert(nc_advanced_set("debug.hakux.perf", ""));
    assert(!hle_prop_new(&command, "debug.hakux.perf", value));
    pthread_t th; assert(!pthread_create(&th, NULL, writer, NULL));
    for (int i = 0; i < 20000; i++) {
        assert(nc_advanced_get("debug.hakux.present", value) > 0);
        assert(!strcmp(value, "sgsr fxaa") || !strcmp(value, "bilinear nofxaa"));
    }
    pthread_join(th, NULL);
    for (int i = 0; i < 16; i++) { snprintf(value, sizeof(value), "u32=%d,%d", i, i); assert(nc_advanced_set("debug.hakux.memscan", value)); }
    assert(!nc_advanced_set("debug.hakux.memscan", "snap"));
    for (int i = 0; i < 16; i++) {
        char expected[92]; snprintf(expected, sizeof(expected), "u32=%d,%d", i, i);
        assert(nc_advanced_take_memory(value)); assert(!strcmp(value, expected));
    }
    assert(!nc_advanced_take_memory(value));
    nc_advanced_log("other", "not collected"); nc_advanced_report(out, sizeof(out)); assert(!out[0]);
    for (int i = 0; i < 2000; i++) nc_advanced_log("hakuX-native-scan", "bounded scanner output: 0123456789");
    nc_advanced_report(out, sizeof(out)); assert(strlen(out) < sizeof(out));
    assert(strstr(out, "bounded scanner output"));
    char tiny[4]; nc_advanced_report(tiny, sizeof(tiny)); assert(tiny[3] == 0);
    puts("PASS: property allowlist, empty masking, atomic concurrent reads, command FIFO/backpressure and bounded results");
}
