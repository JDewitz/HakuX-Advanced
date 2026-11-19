#ifndef NC_FRAMEPACE_H
#define NC_FRAMEPACE_H

#include <stdbool.h>
#include <stdint.h>

/* Startup override is checked against the loaded XBE certificate at boot.
 * Zero clears the override; Republic Commando defaults to 60 FPS. */
void nc_framepace_request(unsigned fps);
void nc_framepace_boot(uint32_t title_id);
/* Decoder thread records presents; only the guest CPU waits at a kernel
 * safe point. The decoder and render thread must never sleep for this cap. */
bool nc_framepace_present(void);
void nc_framepace_wait(void);

#endif
