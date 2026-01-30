#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <wayland-client.h>
#include "pointer-constraints-client-protocol.h"

static struct wl_pointer *lib_ptr;
static struct zwp_pointer_constraints_v1 *lib_pcon;
static struct wl_seat *lib_seat;
static struct wl_event_queue *lib_queue;
static int lib_inited;

struct warp_data {
    int x, y;
    struct wl_surface *surf;
    int done;
};

static struct warp_data wd;

static void lib_lock_locked(void *d, struct zwp_locked_pointer_v1 *lk) {
    zwp_locked_pointer_v1_set_cursor_position_hint(lk,
        wl_fixed_from_int(wd.x), wl_fixed_from_int(wd.y));
    wl_surface_commit(wd.surf);
    zwp_locked_pointer_v1_destroy(lk);
    wl_surface_commit(wd.surf);
    wd.done = 1;
}
static void lib_lock_unlocked(void *d, struct zwp_locked_pointer_v1 *lk) {
    (void)d; (void)lk;
}
static const struct zwp_locked_pointer_v1_listener lib_lock_ls = { lib_lock_locked, lib_lock_unlocked };

static void lp_enter(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf, wl_fixed_t x, wl_fixed_t y) {}
static void lp_leave(void *d, struct wl_pointer *p, uint32_t s, struct wl_surface *sf) {}
static void lp_motion(void *d, struct wl_pointer *p, uint32_t t, wl_fixed_t x, wl_fixed_t y) {}
static void lp_button(void *d, struct wl_pointer *p, uint32_t s, uint32_t t, uint32_t b, uint32_t st) {}
static void lp_axis(void *d, struct wl_pointer *p, uint32_t t, uint32_t a, wl_fixed_t v) {}
static const struct wl_pointer_listener lp_ls = { lp_enter, lp_leave, lp_motion, lp_button, lp_axis };

static void lib_seat_caps(void *d, struct wl_seat *s, uint32_t c) {
    if (c & WL_SEAT_CAPABILITY_POINTER) {
        lib_ptr = wl_seat_get_pointer(s);
        wl_proxy_set_queue((struct wl_proxy *)lib_ptr, lib_queue);
        wl_pointer_add_listener(lib_ptr, &lp_ls, NULL);
    }
}
static void lib_seat_name(void *d, struct wl_seat *s, const char *n) {}
static const struct wl_seat_listener lib_seat_ls = { lib_seat_caps, lib_seat_name };

static void lib_reg_global(void *d, struct wl_registry *r, uint32_t n, const char *i, uint32_t v) {
    if (!strcmp(i, "wl_seat")) {
        lib_seat = wl_registry_bind(r, n, &wl_seat_interface, 1);
        wl_proxy_set_queue((struct wl_proxy *)lib_seat, lib_queue);
        wl_seat_add_listener(lib_seat, &lib_seat_ls, NULL);
    } else if (!strcmp(i, "zwp_pointer_constraints_v1")) {
        lib_pcon = wl_registry_bind(r, n, &zwp_pointer_constraints_v1_interface, 1);
        wl_proxy_set_queue((struct wl_proxy *)lib_pcon, lib_queue);
    }
}
static void lib_reg_remove(void *d, struct wl_registry *r, uint32_t n) {}
static const struct wl_registry_listener lib_reg_ls = { lib_reg_global, lib_reg_remove };

static void lib_init(struct wl_display *dpy) {
    if (lib_inited) return;
    lib_queue = wl_display_create_queue(dpy);
    struct wl_registry *reg = wl_display_get_registry(dpy);
    wl_proxy_set_queue((struct wl_proxy *)reg, lib_queue);
    wl_registry_add_listener(reg, &lib_reg_ls, NULL);
    wl_display_roundtrip_queue(dpy, lib_queue);
    wl_display_roundtrip_queue(dpy, lib_queue);
    lib_inited = 1;
}

/* ── Library entry points ── */

__attribute__((visibility("default")))
void wl_setup_warp(void *wl_display, void *wl_surface, int x, int y) {
    struct wl_display *dpy = wl_display;
    lib_init(dpy);
    wd.x = x;
    wd.y = y;
    wd.surf = wl_surface;
    wd.done = 0;
}

__attribute__((visibility("default")))
void wl_finish_warp(void *wl_display) {
    struct wl_display *dpy = wl_display;
    if (!lib_queue || !lib_pcon || !lib_ptr || !wd.surf) return;

    /* Ensure our pointer has focus on the surface. */
    wl_display_roundtrip_queue(dpy, lib_queue);

    wd.done = 0;

    struct zwp_locked_pointer_v1 *lk = zwp_pointer_constraints_v1_lock_pointer(
        lib_pcon, wd.surf, lib_ptr, NULL,
        ZWP_POINTER_CONSTRAINTS_V1_LIFETIME_ONESHOT);
    wl_proxy_set_queue((struct wl_proxy *)lk, lib_queue);
    zwp_locked_pointer_v1_add_listener(lk, &lib_lock_ls, &wd);

    /* Roundtrip until the lock callback fires. */
    int tries = 0;
    while (!wd.done && tries < 5) {
        wl_display_roundtrip_queue(dpy, lib_queue);
        tries++;
    }
    if (!wd.done) {
        zwp_locked_pointer_v1_destroy(lk);
    }

    /* Final roundtrip to let compositor process the unlock. */
    wl_display_roundtrip_queue(dpy, lib_queue);
}
