.class public final Lsovereign/bridge/MainRoomStart;
.super Ljava/lang/Object;

# Internal frame-transition bridge for the owner's own APK.
# Instrument AFTER CRunApp.startTheFrame() invokes CRun.initRunLoop().
# No screenshots, no OCR, no inter-app guessing about game-start delay.

.method public static emit(LApplication/CRunApp;)V
    .registers 9

    # Main Room is frame index 5 in application.ccn.
    iget v0, p0, LApplication/CRunApp;->currentFrame:I
    const/4 v1, 0x5
    if-ne v0, v1, :done

    # MMFRuntime is an Android Activity; static 'inst' exists in owner's DEX.
    sget-object v0, LRuntime/MMFRuntime;->inst:LRuntime/MMFRuntime;
    if-eqz v0, :done

    new-instance v1, Landroid/content/Intent;
    const-string v2, "dev.sovereign.fnaf.INTERNAL_MAIN_ROOM_START"
    invoke-direct {v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v2, "dev.sovereign.fnaf"
    invoke-virtual {v1, v2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    const-string v2, "frame_index"
    const/4 v3, 0x5
    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;I)Landroid/content/Intent;

    # Preserve the underlying Clickteam engine timestamp for diagnostics.
    iget-object v3, p0, LApplication/CRunApp;->run:LRunLoop/CRun;
    if-eqz v3, :send
    iget-wide v4, v3, LRunLoop/CRun;->rhTimerOld:J
    const-string v2, "engine_wall_ms"
    invoke-virtual {v1, v2, v4, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

:send
    # Both apps share Android's monotonic clock timebase.
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtimeNanos()J
    move-result-wide v4
    const-string v2, "epoch_elapsed_ns"
    invoke-virtual {v1, v2, v4, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    invoke-virtual {v0, v1}, LRuntime/MMFRuntime;->sendBroadcast(Landroid/content/Intent;)V

:done
    return-void
.end method
