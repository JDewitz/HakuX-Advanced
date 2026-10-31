/* Config/status are thread-safe; GPU calls belong to the presenter thread. */
#ifndef HLE_SLANG_H
#define HLE_SLANG_H
#include "hw/xbox/hle/vk/vkdev.h"
#define HLE_SLANG_PARAMS 64
#define HLE_SLANG_PATH 1024
#define HLE_SLANG_NAME 128
typedef struct HleSlangConfig {
    char path[HLE_SLANG_PATH];
    unsigned count;
    struct { char name[HLE_SLANG_NAME]; float value; } params[HLE_SLANG_PARAMS];
} HleSlangConfig;
bool hle_slang_set(const HleSlangConfig *config);
void hle_slang_retry(void);
void hle_slang_status(char *out, size_t cap);
bool hle_slang_parameters(const char *path, char *out, size_t cap);
bool hle_slang_pending(void);
bool hle_slang_active(void);
void hle_slang_prepare(uint32_t w, uint32_t h, bool (*wait)(void));
bool hle_slang_draw(VkCommandBuffer cb, VkImage input, uint32_t w, uint32_t h,
                    uint64_t frame, VkImageView *output);
#endif
