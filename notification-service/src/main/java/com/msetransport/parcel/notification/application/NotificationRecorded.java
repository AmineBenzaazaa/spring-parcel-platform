package com.msetransport.parcel.notification.application;

import com.msetransport.parcel.notification.domain.Notification;

/** Published inside the sending transaction; listeners that push to the UI run after commit. */
public record NotificationRecorded(Notification notification) {
}
