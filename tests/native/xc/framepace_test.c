/* Deterministic real-entry tests for title scoping, timing and lifecycle.
 * cc -I. tests/native/xc/framepace_test.c native/core/framepace.c
 *    -Wl,--wrap=clock_gettime,--wrap=clock_nanosleep -o framepace_test
 */
#include "native/core/framepace.h"
#include <assert.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <time.h>

static uint64_t raw = 1000000000ull;
static bool paused;
static unsigned sleeps;
static bool pause_on_sleep;
static bool present_on_sleep;

bool nc_paused(void) { return paused; }
void nc_log(const char *tag, const char *fmt, ...) { (void)tag; (void)fmt; }

int __wrap_clock_gettime(clockid_t clock, struct timespec *ts)
{
    assert(clock == CLOCK_MONOTONIC);
    ts->tv_sec = (time_t)(raw / 1000000000ull);
    ts->tv_nsec = (long)(raw % 1000000000ull);
    return 0;
}

int __wrap_clock_nanosleep(clockid_t clock, int flags, const struct timespec *ts, struct timespec *remaining)
{
    (void)remaining;
    assert(clock == CLOCK_MONOTONIC && flags == TIMER_ABSTIME);
    uint64_t until = (uint64_t)ts->tv_sec * 1000000000ull + (uint64_t)ts->tv_nsec;
    assert(until >= raw && until - raw <= 2000000ull);
    raw = until;
    sleeps++;
    if (pause_on_sleep) paused = true;
    if (present_on_sleep) {
        present_on_sleep = false;
        assert(nc_framepace_present());
    }
    return 0;
}

static void first_frame(unsigned fps)
{
    paused = false;
    pause_on_sleep = present_on_sleep = false;
    nc_framepace_request(fps);
    nc_framepace_boot(0x4c410013);
    sleeps = 0;
    assert(nc_framepace_present());
    nc_framepace_wait();
    assert(sleeps == 0);
}

int main(void)
{
    nc_framepace_request(60);
    nc_framepace_boot(0x4d530004);
    assert(!nc_framepace_present());
    nc_framepace_wait();
    assert(sleeps == 0);
    nc_framepace_request(120);
    nc_framepace_boot(0x4c410013);
    assert(!nc_framepace_present());

    for (unsigned fps = 30; fps <= 60; fps += 30) {
        first_frame(fps);
        uint64_t start = raw;
        for (unsigned n = 0; n < fps; n++) {
            assert(nc_framepace_present());
            nc_framepace_wait();
        }
        assert(raw - start == (1000000000ull / fps) * fps);
        /* No guest-clock hold API is linked: pacing advances host time. */
        start = raw;
        nc_framepace_wait();
        assert(raw == start);
    }

    first_frame(60);
    for (unsigned n = 0; n < 1000; n++) assert(nc_framepace_present());
    uint64_t start = raw;
    nc_framepace_wait();
    assert(raw - start == 250000000ull); /* Bounded queued-work debt. */

    first_frame(60);
    assert(nc_framepace_present());
    pause_on_sleep = true;
    nc_framepace_wait();
    assert(paused && sleeps == 1);
    pause_on_sleep = paused = false;
    sleeps = 0;
    assert(nc_framepace_present());
    nc_framepace_wait();
    assert(sleeps == 0); /* Resume without paying stale pacing debt. */

    first_frame(60);
    raw += 1000000000ull;
    assert(nc_framepace_present());
    sleeps = 0;
    nc_framepace_wait();
    assert(sleeps == 0); /* No catch-up sleep after a long idle. */

    assert(nc_framepace_present());
    present_on_sleep = true;
    start = raw;
    nc_framepace_wait();
    assert(raw - start == 2 * (1000000000ull / 60));
    nc_framepace_request(0);
    nc_framepace_boot(0x4c410013);
    assert(!nc_framepace_present());
    puts("Frame pacing title scope, rates, queued frames, pause and idle tests passed.");
}
