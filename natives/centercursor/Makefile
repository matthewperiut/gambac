WAYLAND_PROTOS = /usr/share/wayland-protocols
SCANNER = wayland-scanner
WL_CFLAGS = $(shell pkg-config --cflags wayland-client)
WL_LIBS   = $(shell pkg-config --libs wayland-client) -lrt

PROTO_XML = $(WAYLAND_PROTOS)/unstable/pointer-constraints/pointer-constraints-unstable-v1.xml
GEN_HDR = pointer-constraints-client-protocol.h
GEN_SRC = pointer-constraints-protocol.c

ARCH := $(shell uname -m)
ifeq ($(ARCH),aarch64)
    OUTPUT_DIR = ../../src/main/resources/natives/linux-aarch64
else
    OUTPUT_DIR = ../../src/main/resources/natives/linux-x86_64
endif

all: $(OUTPUT_DIR)/libcenter.so
	@rm -f $(GEN_HDR) $(GEN_SRC)

$(OUTPUT_DIR)/libcenter.so: main.c
	@mkdir -p $(OUTPUT_DIR)
	$(SCANNER) client-header $(PROTO_XML) $(GEN_HDR)
	$(SCANNER) private-code $(PROTO_XML) $(GEN_SRC)
	$(CC) -shared -fPIC -fvisibility=hidden -o $@ main.c $(GEN_SRC) $(WL_CFLAGS) $(WL_LIBS)

clean:
	rm -f $(OUTPUT_DIR)/libcenter.so $(GEN_HDR) $(GEN_SRC)

.PHONY: all clean
