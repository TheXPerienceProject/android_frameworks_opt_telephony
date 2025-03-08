/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony;

// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
import android.content.ContentValues;

// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
import com.android.internal.telephony.uicc.AdnCapacity;
import com.android.internal.telephony.uicc.AdnRecord;

/**
 * Interface for applications to access the ICC phone book.
 */
interface IIccPhoneBook {

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * @param efid the EF id of a ADN-like SIM
     * @return List of AdnRecord
     */
    @UnsupportedAppUsage
    List<AdnRecord> getAdnRecordsInEf(int efid);

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * @param efid the EF id of a ADN-like SIM
     * @param subId user preferred subId
     * @return List of AdnRecord
     */
    @UnsupportedAppUsage
    List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid);

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    @UnsupportedAppUsage
    boolean updateAdnRecordsInEfBySearch(int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2);

// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param subId user preferred subId
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param values including ADN,EMAIL,ANR to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    boolean updateAdnRecordsInEfBySearchForSubscriber(int subId,
// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
            int efid, in ContentValues values, String pin2);

// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook
     *
     * @param subId user preferred subId
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param values including ADN,EMAIL,ANR to be updated
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, in ContentValues values,
            int index, String pin2);

    /**
     * Get the max munber of records in efid
     *
     * @param efid the EF id of a ADN-like SIM
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    @UnsupportedAppUsage
    int[] getAdnRecordsSize(int efid);

    /**
     * Get the max munber of records in efid
     *
     * @param efid the EF id of a ADN-like SIM
     * @param subId user preferred subId
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    @UnsupportedAppUsage
    int[] getAdnRecordsSizeForSubscriber(int subId, int efid);

// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    /**
     * Get the capacity of ADN records
     *
// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
     * @return AdnCapacity
// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
     */
// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    AdnCapacity getAdnRecordsCapacity();
// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.

    /**
     * Get the capacity of ADN records
     *
     * @param subId user preferred subId
// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
     * @return AdnCapacity
// QTI_BEGIN: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
     */
// QTI_END: 2018-03-08: Telephony: SimPhoneBook: Add ANR/EMAIL support for USIM phonebook.
    AdnCapacity getAdnRecordsCapacityForSubscriber(int subId);
}
