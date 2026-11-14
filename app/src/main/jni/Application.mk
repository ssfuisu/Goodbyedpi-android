APP_OPTIM := release
APP_PLATFORM := android-24
APP_CFLAGS := -O3 -DPKGNAME=io/github/ssfuisu/goodbyedpi/vpn -DCLSNAME=TProxy
APP_LDFLAGS := -Wl,-z,max-page-size=16384
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
NDK_TOOLCHAIN_VERSION := clang
