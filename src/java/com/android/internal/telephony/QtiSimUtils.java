/*
 * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.internal.telephony;

import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccIoResult;

public class QtiSimUtils {
    private static final String LOG_TAG = "QtiSimUtils";

    private static final String PROPERTY_PERSO_UNLOCK_TEMP_FEATURE =
            "persist.vendor.radio.temp_unlock_feature";

    public static void updatePersoTempUnlockStatusIfRequired(IccCardStatus iccCardStatus) {
        if (!SystemProperties.getBoolean(PROPERTY_PERSO_UNLOCK_TEMP_FEATURE, false) ||
                iccCardStatus == null) {
            return;
        }
        Log.d(LOG_TAG, "updatePersoTempUnlockStatusIfRequired");

        int length = iccCardStatus.mApplications.length;
        for (int i = 0; i < length; i++) {
            IccCardApplicationStatus iccCardAppStatus = iccCardStatus.mApplications[i];
            if (iccCardAppStatus == null) return;

            if (iccCardAppStatus.app_type == IccCardApplicationStatus.AppType.APPTYPE_ISIM) {
                continue; //Ignore ISIM as it is not generally used.
            }

            if (iccCardAppStatus.app_state == IccCardApplicationStatus.AppState.APPSTATE_READY) {
                if (iccCardAppStatus.perso_substate == IccCardApplicationStatus.PersoSubState.
                        PERSOSUBSTATE_SIM_TEMPORARY_UNLOCKED) {
                    // When app state is Ready and perso substate is temporary unlocked, which means
                    // the ready state is only for temporary period, so override ready state with
                    // subscription perso to retain the network lock.
                    iccCardAppStatus.app_state =
                            IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO;
                } else if (iccCardAppStatus.perso_substate == IccCardApplicationStatus.
                        PersoSubState.PERSOSUBSTATE_SIM_PERMANENT_UNLOCKED) {
                    // Perso SubState permanent unlocked means the device is fully unlocked to use
                    // so consider it as normal perso substate keeping it to ready.
                    iccCardAppStatus.perso_substate =
                            IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_READY;
                }
            }
        }
        Log.d(LOG_TAG, "IccCardStatus: " + iccCardStatus);
    }


}
