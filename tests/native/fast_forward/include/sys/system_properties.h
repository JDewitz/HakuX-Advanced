/* Host-only shim: the fast-forward test does not use Android properties. */
#ifndef HAKUX_TEST_SYSTEM_PROPERTIES_H
#define HAKUX_TEST_SYSTEM_PROPERTIES_H
#define PROP_VALUE_MAX 92
static inline int __system_property_get(const char *key, char *value)
{
    value[0] = 0;
    return 0;
}
#endif
