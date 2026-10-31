#ifndef NC_ADVANCED_H
#define NC_ADVANCED_H
#include <stddef.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C" {
#endif
/* -1: no local override; 0: explicitly empty (masks an adb property). */
int nc_advanced_get(const char *name, char *value);
bool nc_advanced_set(const char *name, const char *value);
bool nc_advanced_take_memory(char *value);
void nc_advanced_log(const char *tag, const char *line);
void nc_advanced_report(char *out, size_t cap);
#ifdef __cplusplus
}
#endif
#endif
