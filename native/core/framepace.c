#include "native/core/framepace.h"
#include "native/core/irq.h"
#include "native/core/nc.h"

#include <errno.h>
#include <time.h>

#define REPUBLIC_COMMANDO_TITLE_ID 0x4c410013u
#define MAX_DEBT_NS 250000000ull
#define WAIT_SLICE_NS 2000000ull

static unsigned requested_fps;
static uint64_t period_ns;
static uint64_t produced;
/* Guest CPU only. Host time is deliberately outside guest-clock holds. */
static uint64_t consumed, deadline_ns;

static uint64_t now_ns(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

void nc_framepace_request(unsigned fps)
{
    requested_fps = fps == 30 || fps == 60 ? fps : 0;
}

void nc_framepace_boot(uint32_t title_id)
{
    const unsigned fps = title_id == REPUBLIC_COMMANDO_TITLE_ID ? requested_fps : 0;
    consumed = deadline_ns = 0;
    __atomic_store_n(&produced, 0, __ATOMIC_RELAXED);
    __atomic_store_n(&period_ns, fps ? 1000000000ull / fps : 0, __ATOMIC_RELEASE);
    if (requested_fps) {
        nc_log("hakuX-native", "present pacing: title=%08x requested=%u active=%u FPS; guest clock advances",
               title_id, requested_fps, fps);
    }
}

bool nc_framepace_present(void)
{
    if (!__atomic_load_n(&period_ns, __ATOMIC_ACQUIRE)) {
        return false;
    }
    __atomic_fetch_add(&produced, 1, __ATOMIC_RELEASE);
    return true;
}

void nc_framepace_wait(void)
{
    const uint64_t period = __atomic_load_n(&period_ns, __ATOMIC_ACQUIRE);
    if (!period) {
        return;
    }
    for (;;) {
        const uint64_t count = __atomic_load_n(&produced, __ATOMIC_ACQUIRE);
        if (nc_paused()) {
            consumed = count;
            deadline_ns = 0;
            return;
        }
        const uint64_t now = now_ns();
        if (count != consumed) {
            uint64_t frames = count - consumed;
            consumed = count;
            if (!deadline_ns || now > deadline_ns + MAX_DEBT_NS) {
                deadline_ns = now;
                frames--;  /* First frame after boot or a long idle is immediate. */
            }
            /* Bounded debt permits pause/stop responsiveness after a burst.
             * A queued burst can exceed the target briefly; steady guest
             * production pays its frame time at subsequent safe points. */
            const uint64_t debt = frames > MAX_DEBT_NS / period ? MAX_DEBT_NS : frames * period;
            deadline_ns += debt;
            if (deadline_ns > now + MAX_DEBT_NS) {
                deadline_ns = now + MAX_DEBT_NS;
            }
        }
        if (now >= deadline_ns) {
            return;
        }
        const uint64_t until = deadline_ns < now + WAIT_SLICE_NS ? deadline_ns : now + WAIT_SLICE_NS;
        const struct timespec ts = {(time_t)(until / 1000000000ull), (long)(until % 1000000000ull)};
        const int result = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &ts, NULL);
        if (result != 0 && result != EINTR) {
            return;
        }
    }
}
