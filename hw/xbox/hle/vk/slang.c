/* RetroArch Slang via librashader 0.12.1. A viewport-sized intermediate
 * retains hakuX gamma, rotation and letterboxing in the final pass. */
#include "hw/xbox/hle/base.h"
#include "hw/xbox/hle/hle.h"
#include "hw/xbox/hle/vk/slang.h"
#include "hw/xbox/hle/vk/back_int.h"
#define LIBRA_RUNTIME_VULKAN
#include "native/third_party/librashader.h"
#include <pthread.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static HleSlangConfig requested;
static uint64_t revision, applied, last_frame;
static bool active, failed, first = true;
static char status[2048] = "Off";
static libra_vk_filter_chain_t chain;
static VkImage image;
static VkImageView view;
static VkDeviceMemory memory;
static uint32_t width, height;
static int64_t last_ns;
static void message(const char *fmt, ...)
{
    char text[2048]; va_list args;
    va_start(args, fmt); vsnprintf(text, sizeof(text), fmt, args); va_end(args);
    pthread_mutex_lock(&mutex); strcpy(status, text); pthread_mutex_unlock(&mutex);
    hle_log("hakuX-slang", "%s", text);
}
static bool error(libra_error_t err, const char *where)
{
    if (!err) return false;
    char *text = NULL; libra_error_write(err, &text);
    message("%s: %s. Using normal presentation.", where, text ? text : "shader error");
    libra_error_free_string(&text); libra_error_free(&err); return true;
}
bool hle_slang_set(const HleSlangConfig *c)
{
    if (!c || !memchr(c->path, 0, sizeof(c->path)) || c->count > HLE_SLANG_PARAMS) return false;
    for (unsigned i=0; i<c->count; ++i)
        if (!memchr(c->params[i].name, 0, HLE_SLANG_NAME) || !isfinite(c->params[i].value)) return false;
    pthread_mutex_lock(&mutex);
    if (memcmp(&requested, c, sizeof(*c))) {
        requested=*c; ++revision; snprintf(status,sizeof(status),"Pending; resume to apply");
    }
    pthread_mutex_unlock(&mutex); return true;
}
void hle_slang_retry(void) { pthread_mutex_lock(&mutex); ++revision; pthread_mutex_unlock(&mutex); }
void hle_slang_status(char *out, size_t cap) { pthread_mutex_lock(&mutex); snprintf(out,cap,"%s",status); pthread_mutex_unlock(&mutex); }
bool hle_slang_pending(void) { pthread_mutex_lock(&mutex); bool x=revision!=applied; pthread_mutex_unlock(&mutex); return x; }
bool hle_slang_active(void) { pthread_mutex_lock(&mutex); bool x=active; pthread_mutex_unlock(&mutex); return x; }
static void publish(bool on,uint64_t rev) { pthread_mutex_lock(&mutex); active=on; applied=rev; pthread_mutex_unlock(&mutex); }
static void drop_target(void)
{
    if (view) g_hvk.t.vkDestroyImageView(g_hvk.dev,view,NULL);
    if (image) g_hvk.t.vkDestroyImage(g_hvk.dev,image,NULL);
    if (memory) g_hvk.t.vkFreeMemory(g_hvk.dev,memory,NULL);
    view=VK_NULL_HANDLE; image=VK_NULL_HANDLE; memory=VK_NULL_HANDLE; width=height=0;
}
static bool target(uint32_t w,uint32_t h)
{
    VkImageCreateInfo ci={.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,.imageType=VK_IMAGE_TYPE_2D,
        .format=VK_FORMAT_R8G8B8A8_UNORM,.extent={w,h,1},.mipLevels=1,.arrayLayers=1,
        .samples=VK_SAMPLE_COUNT_1_BIT,.tiling=VK_IMAGE_TILING_OPTIMAL,
        .usage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT,.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED};
    if (g_hvk.t.vkCreateImage(g_hvk.dev,&ci,NULL,&image)!=VK_SUCCESS) goto fail;
    VkMemoryRequirements req; g_hvk.t.vkGetImageMemoryRequirements(g_hvk.dev,image,&req);
    int type=hle_vk_memtype(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,0);
    if (type<0) goto fail;
    VkMemoryAllocateInfo ai={.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,.allocationSize=req.size,.memoryTypeIndex=(uint32_t)type};
    if (g_hvk.t.vkAllocateMemory(g_hvk.dev,&ai,NULL,&memory)!=VK_SUCCESS ||
        g_hvk.t.vkBindImageMemory(g_hvk.dev,image,memory,0)!=VK_SUCCESS) goto fail;
    VkImageViewCreateInfo vi={.sType=VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,.image=image,
        .viewType=VK_IMAGE_VIEW_TYPE_2D,.format=ci.format,.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}};
    if (g_hvk.t.vkCreateImageView(g_hvk.dev,&vi,NULL,&view)!=VK_SUCCESS) goto fail;
    width=w; height=h; first=true; last_ns=0; return true;
fail:
    drop_target(); message("Shader output allocation failed. Using normal presentation."); return false;
}
void hle_slang_prepare(uint32_t w,uint32_t h,bool (*wait)(void))
{
    HleSlangConfig c; uint64_t rev;
    pthread_mutex_lock(&mutex); c=requested; rev=revision; pthread_mutex_unlock(&mutex);
    if (rev!=applied) {
        if (!wait()) return;
        if (chain) error(libra_vk_filter_chain_free(&chain),"Free preset");
        drop_target(); failed=false; publish(false,rev);
        if (!c.path[0]) { message("Off"); return; }
        if (!g_hvk.caps.vk13) { failed=true; message("Slang requires native Vulkan 1.3 in this build. Using normal presentation."); return; }
        libra_shader_preset_t preset=NULL;
        if (error(libra_preset_create(c.path,&preset),"Load preset")) { failed=true; return; }
        filter_chain_vk_opt_t opt={.version=LIBRASHADER_CURRENT_VERSION,.frames_in_flight=3,.use_dynamic_rendering=true,.disable_cache=true};
        libra_device_vk_t dev={.physical_device=g_hvk.phys,.instance=g_hvk.inst,.device=g_hvk.dev,.queue=g_hvk.queue,.entry=vkGetInstanceProcAddr};
        /* Initialization uploads LUTs and waits; serialize access to the shared queue. */
        hle_vk_queue_lock(); libra_error_t err=libra_vk_filter_chain_create(&preset,dev,&opt,&chain); hle_vk_queue_unlock();
        if (preset) error(libra_preset_free(&preset),"Free preset source");
        if (error(err,"Compile preset")) { failed=true; return; }
        uint32_t passes=0;
        if (error(libra_vk_filter_chain_get_active_pass_count(&chain,&passes),"Read pass count") || !passes) {
            if (!passes) message("Preset has no active passes. Using normal presentation.");
            error(libra_vk_filter_chain_free(&chain),"Free preset"); failed=true; return;
        }
        for (unsigned i=0;i<c.count;++i) if (error(libra_vk_filter_chain_set_param(&chain,c.params[i].name,c.params[i].value),"Set parameter")) {
            error(libra_vk_filter_chain_free(&chain),"Free preset"); failed=true; return;
        }
        message("Active: %s",strrchr(c.path,'/')?strrchr(c.path,'/')+1:c.path);
    }
    if (!chain || failed || !w || !h) return;
    if (!image || width!=w || height!=h) {
        if (!wait()) return;
        drop_target();
        if (!target(w,h)) { failed=true; publish(false,rev); return; }
    }
    publish(true,rev);
}
bool hle_slang_draw(VkCommandBuffer cb,VkImage input,uint32_t w,uint32_t h,uint64_t frame,VkImageView *output)
{
    if (!chain || failed || !image) return false;
    if (!first && frame==last_frame) { *output=view; return true; }
    VkImageMemoryBarrier b={.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask=first?0:VK_ACCESS_SHADER_READ_BIT,.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
        .oldLayout=first?VK_IMAGE_LAYOUT_UNDEFINED:VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        .newLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED,.image=image,.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}};
    g_hvk.t.vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,0,0,NULL,0,NULL,1,&b);
    int64_t now=hle_now_ns();
    uint32_t delta=last_ns && now-last_ns<250000000ll?(uint32_t)((now-last_ns)/1000000ll):17;
    frame_vk_opt_t opt={.version=LIBRASHADER_CURRENT_VERSION,.clear_history=first,.frame_direction=1,
        .total_subframes=1,.current_subframe=1,.aspect_ratio=(float)width/height,.frames_per_second=60.0f,.frametime_delta=delta,.brightness_nits=200.0f};
    libra_image_vk_t src={input,VK_FORMAT_R8G8B8A8_UNORM,w,h},dst={image,VK_FORMAT_R8G8B8A8_UNORM,width,height};
    if (error(libra_vk_filter_chain_frame(&chain,cb,frame,src,dst,NULL,NULL,&opt),"Render preset")) {
        failed=true; publish(false,applied); return false; /* caller discards partial recording */
    }
    b.srcAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT; b.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
    b.oldLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL; b.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    g_hvk.t.vkCmdPipelineBarrier(cb,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,0,0,NULL,0,NULL,1,&b);
    first=false; last_frame=frame; last_ns=now; *output=view; return true;
}
bool hle_slang_parameters(const char *path,char *out,size_t cap)
{
    libra_shader_preset_t p=NULL; libra_preset_param_list_t list={0};
    if (!cap) return false;
    out[0]=0; libra_error_t err=libra_preset_create(path,&p);
    if (err) { libra_error_free(&err); return false; }
    err=libra_preset_get_runtime_params(&p,&list);
    if (err) { libra_error_free(&err); libra_preset_free(&p); return false; }
    size_t used=0;
    for (size_t i=0;i<list.length && i<HLE_SLANG_PARAMS;++i) {
        const libra_preset_param_t *a=&list.parameters[i];
        if (strlen(a->name)>=HLE_SLANG_NAME || strpbrk(a->name,"\t\r\n") || strpbrk(a->description,"\t\r\n")) continue;
        int n=snprintf(out+used,cap-used,"%s\t%s\t%.9g\t%.9g\t%.9g\t%.9g\n",a->name,a->description,a->initial,a->minimum,a->maximum,a->step);
        if (n<0 || (size_t)n>=cap-used) { out[used]=0; break; } used+=(size_t)n;
    }
    libra_preset_free_runtime_params(list); libra_preset_free(&p); return true;
}
