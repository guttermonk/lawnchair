# Known issues

Issues that look like Lawnchair bugs but originate below it, with enough evidence
recorded that they don't need re-investigating.

## Single-frame recents card at the left edge when swiping home

**Symptom.** Swiping up from an app to the home screen occasionally shows a brief
sliver of another app at the left edge of the screen, over an otherwise
fully-drawn home screen. It is intermittent and lasts one frame.

**Not a Lawnchair bug.** The sliver is a task thumbnail belonging to the *system*
recents carousel, drawn by whichever package owns `config_recentsComponentName`
(on AOSP/GrapheneOS that is `com.android.launcher3`). No Lawnchair code runs
during the offending frame.

**Applies when** Lawnchair is the home app but *not* the recents provider — i.e.
any normal, non-system install where `LawnchairApp.isRecentsEnabled()` is false —
and gesture navigation is in use. It does not reproduce with the preinstalled
launcher set as home.

### Why a third-party home is required

With a third-party home, a swipe-to-home becomes a two-transition sequence that
the preinstalled launcher never performs:

1. The swipe starts a **transient recents launch** — the recents task moves to
   front, the app moves to back. This happens at gesture *start*, before the
   destination is known.
2. Quickstep then launches home as a **separate** activity, because home is a
   different app.
3. That second transition becomes ready while the first is still animating, so
   WMShell **merges** them.
4. At finish, the recents surface is cleaned up via the finish-WCT, and for one
   frame the carousel is still composited over the already-settled home screen.

When home and recents are the same app there is no second transition to merge
and no separate recents surface to dismiss, which is why the preinstalled
launcher is unaffected.

### Captured evidence

Pixel 5 (redfin), GrapheneOS, Android 14 (SDK 34), gesture navigation. The
glitch frame was captured at 08:45:28.806 and sits inside the teardown window:

```
08:45:27.967  ShellRecents: RecentsTransitionHandler.startRecentsTransition
08:45:28.018  #5359 TO_FRONT  Task{#1106372 type=recents}
08:45:28.116  #5360 requested: OPEN, triggerTask = app.lawnchair/.LawnchairLauncher
08:45:28.220  #5360 ready while #5359 still animating -> merge
08:45:28.234  Transition was merged: (#5360) into (#5359)
08:45:28.793  finishInner: toHome=false userLeave=true willFinishToHome=true state=1
08:45:28.796  Finish Transition (#5359)
08:45:28.805  Loading animations: layout params pkg=com.android.launcher3
08:45:28.806  <-- glitch frame
08:45:28.811  Finish Transition (#5360)
```

Note the ~154 ms between the recents launch and the home decision: the recents
transition is not the result of the gesture being *classified* as "recents". It
is the mechanism the home animation itself is built on, since quickstep needs
control of the outgoing app's surface. No amount of gesture differentiation
avoids it.

### Upstream status

Reported to Google independently by the Niagara Launcher team as
[issue #227692206](https://issuetracker.google.com/issues/227692206), "Home
gesture shows 'Recent apps' when navigating to home screen", and documented at
<https://help.niagaralauncher.app/article/96-screen-flicker-on-returning-home>.

A fix exists in later AOSP. `wmshell/aconfig/multitasking.aconfig` carries:

```
name: "enable_recents_bookend_transition"
description: "Use a finish-transition to clean up recents instead of the finish-WCT"
bug: "346588978"
metadata { purpose: PURPOSE_BUGFIX }
```

Replacing the finish-WCT cleanup with an explicit finish-transition addresses
exactly the mechanism above. Corroborating this, Android 14 logs
`finishInner: toHome=... userLeave=... willFinishToHome=... state=...` while the
newer sources also log `hasPausingTasks` and a `reason` string.

Unverified: whether that flag is enabled by default in any shipping release, and
whether it resolves this precise symptom rather than a neighbouring one. The
match on description and code path is strong but was not confirmed on a device.

### Workarounds

- **3-button navigation.** A Home *button* press does not use the recents
  animation, so the transient recents launch never happens. Costs gesture nav.
- **Lawnchair as the system recents provider** (system-app install). Restores
  the integrated path. Requires modifying `config_recentsComponentName` and
  granting signature-level permissions, which on a verified-boot OS such as
  GrapheneOS means building and signing the OS yourself.

Neither is proportionate to a one-frame cosmetic artifact. There is no
configuration-level fix, and none is possible from within Lawnchair.
