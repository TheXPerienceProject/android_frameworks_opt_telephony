/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.annotation.NonNull;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
// QTI_BEGIN: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
import android.telephony.TelephonyManager;
// QTI_END: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
// QTI_BEGIN: 2025-01-28: Telephony: Check for simultaneous calling restriction
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
// QTI_END: 2025-01-28: Telephony: Check for simultaneous calling restriction
import android.text.TextUtils;

import com.android.internal.telephony.flags.FeatureFlags;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;


/**
 * {@hide}
 */
public abstract class CallTracker extends Handler {

    private static final boolean DBG_POLL = false;

    //***** Constants

    static final int POLL_DELAY_MSEC = 250;

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    protected int mPendingOperations;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    protected boolean mNeedsPoll;
    protected Message mLastRelevantPoll;
    protected ArrayList<Connection> mHandoverConnections = new ArrayList<Connection>();

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public CommandsInterface mCi;

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    protected boolean mNumberConverted = false;

    protected final @NonNull FeatureFlags mFeatureFlags;

    private final int VALID_COMPARE_LENGTH   = 3;

    //***** Events

    protected static final int EVENT_POLL_CALLS_RESULT             = 1;
    protected static final int EVENT_CALL_STATE_CHANGE             = 2;
    protected static final int EVENT_REPOLL_AFTER_DELAY            = 3;
    protected static final int EVENT_OPERATION_COMPLETE            = 4;
    protected static final int EVENT_GET_LAST_CALL_FAIL_CAUSE      = 5;

    protected static final int EVENT_SWITCH_RESULT                 = 8;
    protected static final int EVENT_RADIO_AVAILABLE               = 9;
    protected static final int EVENT_RADIO_NOT_AVAILABLE           = 10;
    protected static final int EVENT_CONFERENCE_RESULT             = 11;
    protected static final int EVENT_SEPARATE_RESULT               = 12;
    protected static final int EVENT_ECT_RESULT                    = 13;
    protected static final int EVENT_EXIT_ECM_RESPONSE_CDMA        = 14;
    protected static final int EVENT_CALL_WAITING_INFO_CDMA        = 15;
    protected static final int EVENT_THREE_WAY_DIAL_L2_RESULT_CDMA = 16;
    protected static final int EVENT_THREE_WAY_DIAL_BLANK_FLASH    = 20;
    protected static final int EVENT_EXIT_SCBM_RESPONSE_CDMA       = 19;

    @UnsupportedAppUsage
    public CallTracker(FeatureFlags featureFlags) {
        mFeatureFlags = featureFlags;
    }

    protected void pollCallsWhenSafe() {
        mNeedsPoll = true;

        if (checkNoOperationsPending()) {
            mLastRelevantPoll = obtainMessage(EVENT_POLL_CALLS_RESULT);
            mCi.getCurrentCalls(mLastRelevantPoll);
        }
    }

    protected void
    pollCallsAfterDelay() {
        if (mFeatureFlags.preventInvocationRepeatOfRilCallWhenDeviceDoesNotSupportVoice()) {
            if (!mCi.getHalVersion(TelephonyManager.HAL_SERVICE_VOICE)
                    .greaterOrEqual(RIL.RADIO_HAL_VERSION_1_4)) {
                log("Skip polling because HAL_SERVICE_VOICE < RADIO_HAL_VERSION_1.4");
                return;
            }
        }

        Message msg = obtainMessage();

        msg.what = EVENT_REPOLL_AFTER_DELAY;
        sendMessageDelayed(msg, POLL_DELAY_MSEC);
    }

    protected boolean
    isCommandExceptionRadioNotAvailable(Throwable e) {
        return e != null && e instanceof CommandException
                && ((CommandException)e).getCommandError()
                        == CommandException.Error.RADIO_NOT_AVAILABLE;
    }

    protected abstract void handlePollCalls(AsyncResult ar);

    protected abstract Phone getPhone();

    protected Connection getHoConnection(DriverCall dc) {
        for (Connection hoConn : mHandoverConnections) {
            log("getHoConnection - compare number: hoConn= " + hoConn.toString());
            if (hoConn.getAddress() != null && hoConn.getAddress().contains(dc.number)) {
                log("getHoConnection: Handover connection match found = " + hoConn.toString());
                return hoConn;
            }
        }
        for (Connection hoConn : mHandoverConnections) {
            log("getHoConnection: compare state hoConn= " + hoConn.toString());
            if (hoConn.getStateBeforeHandover() == Call.stateFromDCState(dc.state)) {
                log("getHoConnection: Handover connection match found = " + hoConn.toString());
                return hoConn;
            }
        }
        return null;
    }

    protected void notifySrvccState(Call.SrvccState state, ArrayList<Connection> c) {
        if (state == Call.SrvccState.STARTED && c != null) {
            // SRVCC started. Prepare handover connections list
            mHandoverConnections.addAll(c);
        } else if (state != Call.SrvccState.COMPLETED) {
            // SRVCC FAILED/CANCELED. Clear the handover connections list
            // Individual connections will be removed from the list in handlePollCalls()
            mHandoverConnections.clear();
        }
        log("notifySrvccState: state=" + state.name() + ", mHandoverConnections= "
                + mHandoverConnections.toString());
    }

    protected void handleRadioAvailable() {
        pollCallsWhenSafe();
    }

    /**
     * Obtain a complete message that indicates that this operation
     * does not require polling of getCurrentCalls(). However, if other
     * operations that do need getCurrentCalls() are pending or are
     * scheduled while this operation is pending, the invocation
     * of getCurrentCalls() will be postponed until this
     * operation is also complete.
     */
    protected Message
    obtainNoPollCompleteMessage(int what) {
        mPendingOperations++;
        mLastRelevantPoll = null;
        return obtainMessage(what);
    }

    /**
     * @return true if we're idle or there's a call to getCurrentCalls() pending
     * but nothing else
     */
    private boolean
    checkNoOperationsPending() {
        if (DBG_POLL) log("checkNoOperationsPending: pendingOperations=" +
                mPendingOperations);
        return mPendingOperations == 0;
    }

    protected String convertNumberIfNecessary(Phone phone, String dialNumber) {
        if (dialNumber == null) {
            return dialNumber;
        }
        String[] convertMaps = null;
        CarrierConfigManager configManager = (CarrierConfigManager)
                phone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle bundle = configManager.getConfigForSubId(phone.getSubId());
        if (bundle != null) {
            convertMaps = (shouldPerformInternationalNumberRemapping(phone, bundle))
                    ? bundle.getStringArray(CarrierConfigManager
                            .KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY)
                    : bundle.getStringArray(CarrierConfigManager
                            .KEY_DIAL_STRING_REPLACE_STRING_ARRAY);
        }
        if (convertMaps == null) {
            // By default no replacement is necessary
            log("convertNumberIfNecessary convertMaps is null");
            return dialNumber;
        }

        log("convertNumberIfNecessary Roaming"
            + " convertMaps.length " + convertMaps.length
            + " dialNumber.length() " + dialNumber.length());

        if (convertMaps.length < 1 || dialNumber.length() < VALID_COMPARE_LENGTH) {
            return dialNumber;
        }

        String[] entry;
        String outNumber = "";
        for(String convertMap : convertMaps) {
            log("convertNumberIfNecessary: " + convertMap);
            // entry format is  "dialStringToReplace:dialStringReplacement"
            entry = convertMap.split(":");
            if (entry != null && entry.length > 1) {
                String dsToReplace = entry[0];
                String dsReplacement = entry[1];
                if (!TextUtils.isEmpty(dsToReplace) && dialNumber.equals(dsToReplace)) {
                    // Needs to be converted
                    if (!TextUtils.isEmpty(dsReplacement) && dsReplacement.endsWith("MDN")) {
                        String mdn = phone.getLine1Number();
                        if (!TextUtils.isEmpty(mdn)) {
                            if (mdn.startsWith("+")) {
                                outNumber = mdn;
                            } else {
                                outNumber = dsReplacement.substring(0, dsReplacement.length() -3)
                                        + mdn;
                            }
                        }
                    } else {
                        outNumber = dsReplacement;
                    }
                    break;
                }
            }
        }

        if (!TextUtils.isEmpty(outNumber)) {
            log("convertNumberIfNecessary: convert service number");
            mNumberConverted = true;
            return outNumber;
        }

        return dialNumber;

    }

    /**
     * Helper function to determine if the phones service is in ROAMING_TYPE_INTERNATIONAL.
     * @param phone object that contains the service state.
     * @param bundle object that contains the bundle with mapped dial strings.
     * @return  true if the phone is in roaming state with a set bundle. Otherwise, false.
     */
    private boolean shouldPerformInternationalNumberRemapping(Phone phone,
            PersistableBundle bundle) {
        if (phone == null || phone.getDefaultPhone() == null) {
            log("shouldPerformInternationalNumberRemapping: phone was null");
            return false;
        }

        if (bundle.getStringArray(CarrierConfigManager
                .KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY) == null) {
            log("shouldPerformInternationalNumberRemapping: did not set the "
                    + "KEY_INTERNATIONAL_ROAMING_DIAL_STRING_REPLACE_STRING_ARRAY");
            return false;
        }

        return phone.getDefaultPhone().getServiceState().getVoiceRoamingType()
                == ServiceState.ROAMING_TYPE_INTERNATIONAL;
    }

// QTI_BEGIN: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
    /**
// QTI_END: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
// QTI_BEGIN: 2025-01-28: Telephony: Check for simultaneous calling restriction
     * Determines whether incoming call is a pseudo-DSDA call.
     * Returns false immediately for:
     *     1. SINGLE SIM configuration
     *     2. If the other SUB which receives the incoming call does not have any calls
     *     3. DSDA (part of the other phone account's calling restriction)
     * Returns true if
     *     1. If the SUB which receives the incoming call does not have any other calls
     *        and the other SUB has calls, otherwise returns false
// QTI_END: 2025-01-28: Telephony: Check for simultaneous calling restriction
// QTI_BEGIN: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
     */
    protected boolean isPseudoDsdaCall() {
        TelephonyManager telephony = TelephonyManager.from(getPhone().getContext());
// QTI_END: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
// QTI_BEGIN: 2025-01-28: Telephony: Check for simultaneous calling restriction
        if (telephony.getActiveModemCount() <= PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            return false;
        }
        Phone otherOffhookPhone = getOtherOffhookPhone();
        // If the other SUB is not offhook, then no need to check further
        // Ex cases which are handled (and vice versa):
        //  1) SUB1: INCOMING
        //     SUB2: IDLE
        //  2) SUB1: OFFHOOK + INCOMING
        //     SUB2: IDLE
        if (otherOffhookPhone == null) {
            return false;
        }
        if (containedInSimultaneousRestriction(otherOffhookPhone)) {
            log("containsPhoneAccount in simultaneous restriction, so do not treat as pseudo dsda");
            return false;
        }
        // For cases where the UE is not configured for DSDA, identify pseudo-DSDA cases
        boolean hasCallOnSameSub = getPhone().getDefaultPhone().getState() ==
                PhoneConstants.State.OFFHOOK;
        // ex: SUB1: ACTIVE
        //     SUB2: INCOMING --> treat as pseudo-DSDA
        if (!hasCallOnSameSub) {
            log("This is a pseudo dsda call");
            return true;
        }
        // ex: SUB1: OFFHOOK + INCOMING
        //     SUB2: OFFHOOK --> do not treat as pseudo-DSDA since there is already a call on SUB1
        return false;
    }

    // helper function to check and return the offhook phone if it is
    // not the phone where the incoming call resides
    private Phone getOtherOffhookPhone() {
        for (Phone phone: PhoneFactory.getPhones()) {
            if (phone.getSubId() != getPhone().getSubId() &&
                    phone.getState() == PhoneConstants.State.OFFHOOK) {
                return phone;
            }
        }
        return null;
    }

    // Checks if device is configured for DSDA, where the offhook phone account
    // is a part of the incoming phone account's simultaneous restriction
    private boolean containedInSimultaneousRestriction(Phone offhook) {
        Context context = getPhone().getContext();
        final TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        final TelephonyManager telephonyManager = TelephonyManager.from(context);
        Phone ringing = getPhone().getDefaultPhone();

        if (offhook == null || ringing == null) {
            log("Either offhook or ringing call is null, which is not expected");
            return false;
        }
        PhoneAccountHandle ringingAccount = telephonyManager != null ?
                telephonyManager.getPhoneAccountHandleForSubscriptionId(ringing.getSubId()) : null;
        PhoneAccountHandle offhookAccount = telephonyManager != null ?
                telephonyManager.getPhoneAccountHandleForSubscriptionId(offhook.getSubId()) : null;

        if (ringingAccount == null || offhookAccount == null) {
            log("Null phone account handle for ringing phone");
            return false;
        }

        PhoneAccount pa = telecomManager != null ? telecomManager.getPhoneAccount(offhookAccount) :
                null;
        if (pa != null && pa.hasSimultaneousCallingRestriction()) {
            if (pa.getSimultaneousCallingRestriction().contains(ringingAccount)) {
                log("contains phone account in simultaneous calling restriction");
                return true;
// QTI_END: 2025-01-28: Telephony: Check for simultaneous calling restriction
// QTI_BEGIN: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
            }
        }
        return false;
    }

// QTI_END: 2020-04-22: Telephony: CS: VoWiFi Dual Voice Call feature for CS Voice
    private boolean compareGid1(Phone phone, String serviceGid1) {
        String gid1 = phone.getGroupIdLevel1();
        int gid_length = serviceGid1.length();
        boolean ret = true;

        if (serviceGid1 == null || serviceGid1.equals("")) {
            log("compareGid1 serviceGid is empty, return " + ret);
            return ret;
        }
        // Check if gid1 match service GID1
        if (!((gid1 != null) && (gid1.length() >= gid_length) &&
                gid1.substring(0, gid_length).equalsIgnoreCase(serviceGid1))) {
            log(" gid1 " + gid1 + " serviceGid1 " + serviceGid1);
            ret = false;
        }
        log("compareGid1 is " + (ret?"Same":"Different"));
        return ret;
    }

    /**
     * Get the ringing connections which during SRVCC handover.
     */
    public Connection getRingingHandoverConnection() {
        for (Connection hoConn : mHandoverConnections) {
            if (hoConn.getCall().isRinging()) {
                return hoConn;
            }
        }
        return null;
    }

    //***** Overridden from Handler
    @Override
    public abstract void handleMessage (Message msg);
    public abstract void registerForVoiceCallStarted(Handler h, int what, Object obj);
    public abstract void unregisterForVoiceCallStarted(Handler h);
    @UnsupportedAppUsage
    public abstract void registerForVoiceCallEnded(Handler h, int what, Object obj);
    public abstract void unregisterForVoiceCallEnded(Handler h);
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public abstract PhoneConstants.State getState();
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    protected abstract void log(String msg);

    /**
     * Called when the call tracker should attempt to reconcile its calls against its underlying
     * phone implementation and cleanup any stale calls.
     */
    public void cleanupCalls() {
        // no base implementation
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CallTracker:");
        pw.println(" mPendingOperations=" + mPendingOperations);
        pw.println(" mNeedsPoll=" + mNeedsPoll);
        pw.println(" mLastRelevantPoll=" + mLastRelevantPoll);
    }
}
