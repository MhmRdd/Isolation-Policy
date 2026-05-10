# IsolPolicy

LSPosed module that denies the `useAppZygote` service-spawn path on a user-selected list of packages.

## Background

On Android 10+ a service can declare `android:useAppZygote="true"` together with `android:isolatedProcess="true"`. The platform forks the service from a per-app **App Zygote** rather than the global zygote. Whatever code an app puts in its `ZygotePreload` runs inside the `app_zygote` SELinux domain before the dyntransition to `isolated_app`. That domain is granted `selinux_check_context` and `selinux_check_access` by AOSP's [`system/sepolicy/private/app_zygote.te`](https://android.googlesource.com/platform/system/sepolicy/+/master/private/app_zygote.te), so the preload can ask the kernel to validate arbitrary SELinux labels and answer arbitrary access-vector queries via `/sys/fs/selinux/{context,access}`. An untrusted app domain has neither permission.

LSPosed disclosed this surface as [DirtySepolicy](https://github.com/LSPosed/DirtySepolicy). The same mechanism can also be abused to fingerprint a device's SELinux policy from a regular app (root detection, hook detection, custom policy detection).

## What this module does

The single hook is on `com.android.server.am.HostingRecord#usesAppZygote()` inside `system_server`. When the calling service belongs to a denied package, the hook returns `false`. AOSP's `ProcessList.startProcessLocked` then falls through to the regular `Process.start(...)` branch, the service binds normally and runs in `isolated_app`, and the `ZygotePreload` callback never gets a chance to run in `app_zygote`. No bind failures, no `onNullBinding`, no crashes — the loophole is just gone for those packages.

System packages and the module's own package are exempt from the deny list to avoid touching anything the host OEM relies on.

## Transport

Configuration travels from the app to the system_server hook over a custom `Binder`, mirroring [FuseFixer](https://github.com/5ec1cff/FuseFixer):

1. App broadcasts `ACTION_REQUEST_BINDER` carrying a `PendingIntent`.
2. The hook validates `pi.getCreatorPackage() == APPLICATION_ID`, then replies with `ACTION_DELIVER_BINDER` carrying a `PolicyService` Binder.
3. The app keeps the `IBinder` and `transact()`s `TRX_GET_POLICY` / `TRX_SET_POLICY` / `TRX_PING` / `TRX_GET_VERSION` directly.

`PolicyService.onTransact` rejects callers whose UID is not the one that originally requested the binder.

## OEM compatibility

Because the hook only flips `usesAppZygote()` for packages the user explicitly listed, OEM gating layers running deeper in the bind path (Oplus's `IOplusAppStartupManager.shouldPreventStartService`, the autostart manager, etc.) keep behaving exactly as the OEM intends for everything else. The module is strictly additive.

## References

- LSPosed/DirtySepolicy: https://github.com/LSPosed/DirtySepolicy
- 5ec1cff/FuseFixer: https://github.com/5ec1cff/FuseFixer (transport layout)
- AOSP `app_zygote.te`: https://android.googlesource.com/platform/system/sepolicy/+/master/private/app_zygote.te (line 42, `selinux_check_context` / `selinux_check_access`)

### Verified against

OnePlus, OxygenOS 15, build `CPH2447_15.0.0.831(EX01)`.
The OEM gating implementation that motivated this module lives in `/system_ext/framework/oplus-services.jar`:

- `com.android.server.am.ActiveServicesExtImpl#interceptBindServiceLockedBeforeConnection` is the hook the AOSP `ActiveServices.bindServiceLocked` calls via Oplus's `ExtLoader.type(IActiveServicesExt.class)` indirection.
- It dispatches to `OplusAppStartupManager#shouldPreventStartService(..., "bindService")`, whose non-system-app branch falls through to `isAllowStartFromBindService` → `isAllowStartFromService`, i.e. the autostart-manager policy that decides whether a third-party app may bring up a new service process.
- Empty / unfamiliar packages without an LRU process record are denied; previously-resident apps and OEM-allowlisted ones are allowed. That is why a freshly-installed App-Zygote PoC sees `bindIsolatedService` return `false` while LSPosed/DirtySepolicy passes the bind and crashes only later from the `handleAppZygoteStart` informational hook.

## License

Apache 2.0.
