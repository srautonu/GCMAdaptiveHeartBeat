//
// This file handles different system events (connectivity) as well as different custom
// actions that are scheduled via AlarmManager. It extends WakefulBroadcastReceiver
// as wake lock may be needed to complete certain tasks.
//
package com.gcmadaptiveheartbeater.android.BackGroundServices;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.gcmadaptiveheartbeater.android.BuildConfig;
import com.gcmadaptiveheartbeater.android.Constants;
import com.gcmadaptiveheartbeater.android.NetworkUtil;
import com.gcmadaptiveheartbeater.android.SettingsUtil;

/**
 * Created by mrahman on 24-Oct-16.
 */

public class SystemEventsReceiver extends WakefulBroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) {

        String strAction = intent.getAction();
        if (null == strAction)
            return;

        System.out.println("Action: " + strAction);

        if (strAction.equalsIgnoreCase(Constants.ACTION_END_EXPERIMENT))
        {
            ServicesMgr.endExperiment(context);
            return;
        }

        //
        // No point sending/scheduling test/GCM KA if we are not connected
        //
        boolean isConnected = NetworkUtil.isConnected(context);
        if (!isConnected && !strAction.equalsIgnoreCase("android.net.conn.CONNECTIVITY_CHANGE"))
        {
            System.out.println("Ignoring action, as we are not connected.");
            return;
        }

        if (strAction.equalsIgnoreCase(Constants.ACTION_SEND_DATA_KA))
        {
            sendDataKA(context);
        }
        else if (strAction.equalsIgnoreCase(Constants.ACTION_SEND_TEST_KA)
                && SettingsUtil.getExpModel(context) == Constants.EXP_MODEL_ADAPTIVE)
        {
            sendTestKA(context);
        }
        else if (strAction.equalsIgnoreCase(Constants.ACTION_START_KA_TESTING)
                && SettingsUtil.getExpModel(context) == Constants.EXP_MODEL_ADAPTIVE)
        {
            scheduleTestKA(context, -1);
        }
        else if (strAction.equalsIgnoreCase("android.net.conn.CONNECTIVITY_CHANGE")) {
            //
            // This is an event generated by the OS when the connectivity state
            // is changed.
            //

            System.out.println("Connected: " + isConnected);

            // broadcast the trigger to update all the tabs with connectivity info
            LocalBroadcastManager.getInstance(context).sendBroadcast(
                    new Intent(Constants.ACTION_HANDLE_SETTINGS_UPDATE)
            );

            //
            // Whenever we get connected, we let the system stabilize for a minute
            // then send the Test/GCM KA as appropriate as per testing model.
            //
            if (isConnected)
            {
                int expModel = SettingsUtil.getExpModel(context);

                scheduleDataKA(context, 1);
                if (expModel == Constants.EXP_MODEL_ADAPTIVE) {
                    scheduleTestKA(context, 1);
                }
            }
        }
    }

    //
    // Currently unused. If we want to use GCM instead, we should
    // call this function for sending Data KA
    //
    private void sendGCMKA(Context context)
    {
        //
        // We are about to send GCM KA. Update the counter.
        //
        SettingsUtil.incrementDataKACount(context);
        System.out.println("Sending Data KA.");

        context.sendBroadcast(new Intent("com.google.android.intent.action.GTALK_HEARTBEAT"));
        context.sendBroadcast(new Intent("com.google.android.intent.action.MCS_HEARTBEAT"));

        // Schedule the next keep-alive.
        scheduleDataKA(context, -1);
    }

    private void sendDataKA(Context context)
    {
        startWakefulService(context, new Intent(Constants.ACTION_SEND_DATA_KA).setPackage(BuildConfig.APPLICATION_ID));
    }

    private void scheduleDataKA(Context context, int delayM)
    {
        //
        // Schedule Data KA after delay minutes. If delayM is set to negative, then
        // schedule the KA after last known good KA interval
        //
        if (delayM < 0)
        {
            SharedPreferences pref = context.getSharedPreferences(Constants.SETTINGS_FILE, 0);
            delayM = pref.getInt(Constants.DATA_KA, 1);
        }

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        //
        // Now we need to update the timer.
        //
        alarm.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayM * 60 * 1000,
                PendingIntent.getBroadcast(context, 0, new Intent(Constants.ACTION_SEND_DATA_KA), PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    private void sendTestKA(Context context)
    {
        startWakefulService(context, new Intent(Constants.ACTION_SEND_TEST_KA).setPackage(BuildConfig.APPLICATION_ID));
    }

    private void scheduleTestKA(Context context, int delayM)
    {
        //
        // Schedule Test KA connection establishment after delay minutes.
        // If delayM is set to negative, then establish immediately.
        //
        if (delayM < 0)
        {
            startWakefulService(context, new Intent(Constants.ACTION_START_KA_TESTING).setPackage(BuildConfig.APPLICATION_ID));
        }
        else
        {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            //
            // Now we need to update the timer.
            //
            alarm.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayM * 60 * 1000,
                PendingIntent.getBroadcast(context, 0, new Intent(Constants.ACTION_START_KA_TESTING), PendingIntent.FLAG_UPDATE_CURRENT)
                );
        }
    }
}
