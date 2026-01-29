/*
 * gambac-warp.c — wp_pointer_warp_v1 helper for Gambac
 *
 * Provides cursor warping on Wayland compositors that support the
 * wp_pointer_warp_v1 protocol.  Loaded from Java via LWJGL's
 * SharedLibrary and called through JNI function-pointer invocations.
 *
 * Exported symbols:
 *   gambac_warp_init(wl_display*)          → int (1 = supported)
 *   gambac_warp_supported()                → int (1 = yes)
 *   gambac_warp_cursor(wl_surface*, x, y)  → void  (x,y in wl_fixed_t)
 *   gambac_warp_destroy()                  → void
 */

#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <wayland-client.h>

/* ------------------------------------------------------------------ */
/* wp_pointer_warp_v1 protocol stubs (hand-written from the spec)     */
/* ------------------------------------------------------------------ */

/*
 * Interface layout for wp_pointer_warp_v1:
 *   request 0: destroy        ()
 *   request 1: warp_pointer   (object<wl_surface>, object<wl_pointer>,
 *                               fixed x, fixed y, uint serial)
 *   No events.
 */

static const struct wl_interface *warp_pointer_arg_types[] = {
    &wl_surface_interface,   /* surface */
    &wl_pointer_interface,   /* pointer */
    NULL,                    /* x  (fixed) */
    NULL,                    /* y  (fixed) */
    NULL,                    /* serial (uint) */
};

static const struct wl_message wp_pointer_warp_v1_requests[] = {
    { "destroy",      "",         NULL },
    { "warp_pointer", "ooffu",    warp_pointer_arg_types },
};

static const struct wl_interface wp_pointer_warp_v1_interface = {
    "wp_pointer_warp_v1", 1,
    2, wp_pointer_warp_v1_requests,
    0, NULL,
};

/* ------------------------------------------------------------------ */
/* State                                                              */
/* ------------------------------------------------------------------ */

static struct wl_display  *g_display  = NULL;
static struct wl_registry *g_registry = NULL;
static struct wl_seat     *g_seat     = NULL;
static struct wl_pointer  *g_pointer  = NULL;
static void               *g_warp     = NULL;   /* wp_pointer_warp_v1 proxy */
static uint32_t            g_enter_serial = 0;
static int                 g_supported = 0;
static uint32_t            g_warp_name = 0;
static uint32_t            g_seat_name = 0;

/* ------------------------------------------------------------------ */
/* Pointer listener — only care about enter serial                    */
/* ------------------------------------------------------------------ */

static void pointer_enter(void *data, struct wl_pointer *pointer,
                          uint32_t serial, struct wl_surface *surface,
                          wl_fixed_t sx, wl_fixed_t sy)
{
    (void)data; (void)pointer; (void)surface; (void)sx; (void)sy;
    g_enter_serial = serial;
}

static void pointer_leave(void *data, struct wl_pointer *p,
                          uint32_t serial, struct wl_surface *s)
{ (void)data; (void)p; (void)serial; (void)s; }

static void pointer_motion(void *data, struct wl_pointer *p,
                           uint32_t time, wl_fixed_t sx, wl_fixed_t sy)
{ (void)data; (void)p; (void)time; (void)sx; (void)sy; }

static void pointer_button(void *data, struct wl_pointer *p,
                           uint32_t serial, uint32_t time,
                           uint32_t button, uint32_t state)
{ (void)data; (void)p; (void)serial; (void)time; (void)button; (void)state; }

static void pointer_axis(void *data, struct wl_pointer *p,
                         uint32_t time, uint32_t axis, wl_fixed_t value)
{ (void)data; (void)p; (void)time; (void)axis; (void)value; }

static const struct wl_pointer_listener pointer_listener = {
    .enter  = pointer_enter,
    .leave  = pointer_leave,
    .motion = pointer_motion,
    .button = pointer_button,
    .axis   = pointer_axis,
};

/* ------------------------------------------------------------------ */
/* Seat listener — grab the pointer capability                        */
/* ------------------------------------------------------------------ */

static void seat_capabilities(void *data, struct wl_seat *seat,
                              uint32_t caps)
{
    (void)data;
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && !g_pointer) {
        g_pointer = wl_seat_get_pointer(seat);
        wl_pointer_add_listener(g_pointer, &pointer_listener, NULL);
    }
}

static void seat_name(void *data, struct wl_seat *seat, const char *name)
{ (void)data; (void)seat; (void)name; }

static const struct wl_seat_listener seat_listener = {
    .capabilities = seat_capabilities,
    .name         = seat_name,
};

/* ------------------------------------------------------------------ */
/* Registry listener                                                  */
/* ------------------------------------------------------------------ */

static void registry_global(void *data, struct wl_registry *registry,
                            uint32_t name, const char *interface,
                            uint32_t version)
{
    (void)data; (void)version;
    if (strcmp(interface, "wp_pointer_warp_v1") == 0) {
        g_warp = wl_registry_bind(registry, name,
                                  &wp_pointer_warp_v1_interface, 1);
        g_warp_name = name;
        g_supported = 1;
    } else if (strcmp(interface, "wl_seat") == 0 && !g_seat) {
        g_seat = wl_registry_bind(registry, name,
                                  &wl_seat_interface, 1);
        g_seat_name = name;
        wl_seat_add_listener(g_seat, &seat_listener, NULL);
    }
}

static void registry_global_remove(void *data, struct wl_registry *registry,
                                   uint32_t name)
{
    (void)data; (void)registry;
    if (name == g_warp_name) {
        g_supported = 0;
    }
}

static const struct wl_registry_listener registry_listener = {
    .global        = registry_global,
    .global_remove = registry_global_remove,
};

/* ------------------------------------------------------------------ */
/* Exported API                                                       */
/* ------------------------------------------------------------------ */

__attribute__((visibility("default")))
int gambac_warp_init(struct wl_display *display)
{
    if (!display) return 0;
    g_display = display;

    g_registry = wl_display_get_registry(display);
    if (!g_registry) return 0;

    wl_registry_add_listener(g_registry, &registry_listener, NULL);

    /* Two roundtrips: first to receive globals, second to receive
       seat capabilities and pointer events */
    wl_display_roundtrip(display);
    wl_display_roundtrip(display);

    fprintf(stderr, "[Gambac] wp_pointer_warp_v1: %s\n",
            g_supported ? "supported" : "not supported");
    return g_supported;
}

__attribute__((visibility("default")))
int gambac_warp_supported(void)
{
    return g_supported;
}

__attribute__((visibility("default")))
void gambac_warp_cursor(struct wl_surface *surface,
                        wl_fixed_t x, wl_fixed_t y)
{
    if (!g_supported || !g_warp || !surface || !g_pointer) return;

    /* request opcode 1: warp_pointer(surface, pointer, x, y, serial) */
    wl_proxy_marshal((struct wl_proxy *)g_warp, 1,
                     surface, g_pointer, x, y, g_enter_serial);
    wl_display_flush(g_display);
}

__attribute__((visibility("default")))
void gambac_warp_destroy(void)
{
    if (g_warp) {
        /* request opcode 0: destroy */
        wl_proxy_marshal((struct wl_proxy *)g_warp, 0);
        wl_proxy_destroy((struct wl_proxy *)g_warp);
        g_warp = NULL;
    }
    if (g_pointer) {
        wl_pointer_destroy(g_pointer);
        g_pointer = NULL;
    }
    if (g_seat) {
        wl_seat_destroy(g_seat);
        g_seat = NULL;
    }
    if (g_registry) {
        wl_registry_destroy(g_registry);
        g_registry = NULL;
    }
    g_display = NULL;
    g_supported = 0;
    g_enter_serial = 0;
}
