/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Checks if an application wants to use permissions that can only be used by system applications.
 */
public class SystemPermissionsDetector extends Detector implements XmlScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "ProtectedPermissions",
                    "Using system app permission",
                    "Permissions with the protection level signature, privileged or signatureOrSystem "
                            + "are only granted to system apps. If an app is a regular non-system app, it will "
                            + "never be able to use these permissions.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            SystemPermissionsDetector.class, EnumSet.of(Scope.MANIFEST)));

    // List of permissions have the protection levels signature, privileged or systemOrSignature.
    // This list must be sorted alphabetically.
    @VisibleForTesting
    static final String[] SYSTEM_PERMISSIONS =
            new String[] {
                "android.intent.category.MASTER_CLEAR.permission.C2D_MESSAGE",
                "android.permission.ACCESS_ALL_EXTERNAL_STORAGE",
                "android.permission.ACCESS_AMBIENT_LIGHT_STATS",
                "android.permission.ACCESS_BROADCAST_RADIO",
                "android.permission.ACCESS_CACHE_FILESYSTEM",
                "android.permission.ACCESS_CHECKIN_PROPERTIES",
                "android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY",
                "android.permission.ACCESS_DRM_CERTIFICATES",
                "android.permission.ACCESS_EPHEMERAL_APPS",
                "android.permission.ACCESS_FM_RADIO",
                "android.permission.ACCESS_IMS_CALL_SERVICE",
                "android.permission.ACCESS_INPUT_FLINGER",
                "android.permission.ACCESS_INSTANT_APPS",
                "android.permission.ACCESS_KEYGUARD_SECURE_STORAGE",
                "android.permission.ACCESS_LOWPAN_STATE",
                "android.permission.ACCESS_MOCK_LOCATION",
                "android.permission.ACCESS_MTP",
                "android.permission.ACCESS_NETWORK_CONDITIONS",
                "android.permission.ACCESS_NOTIFICATIONS",
                "android.permission.ACCESS_PDB_STATE",
                "android.permission.ACCESS_SHORTCUTS",
                "android.permission.ACCESS_SURFACE_FLINGER",
                "android.permission.ACCESS_UCE_OPTIONS_SERVICE",
                "android.permission.ACCESS_UCE_PRESENCE_SERVICE",
                "android.permission.ACCESS_USB",
                "android.permission.ACCESS_VOICE_INTERACTION_SERVICE",
                "android.permission.ACCESS_VR_MANAGER",
                "android.permission.ACCESS_VR_STATE",
                "android.permission.ACCOUNT_MANAGER",
                "android.permission.ACTIVITY_EMBEDDING",
                "android.permission.ADD_SYSTEM_SERVICE",
                "android.permission.ALLOCATE_AGGRESSIVE",
                "android.permission.ALLOW_ANY_CODEC_FOR_PLAYBACK",
                "android.permission.ASEC_ACCESS",
                "android.permission.ASEC_CREATE",
                "android.permission.ASEC_DESTROY",
                "android.permission.ASEC_MOUNT_UNMOUNT",
                "android.permission.ASEC_RENAME",
                "android.permission.BACKUP",
                "android.permission.BACKUP_DATA",
                "android.permission.BATTERY_STATS",
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.BIND_APPWIDGET",
                "android.permission.BIND_AUTOFILL",
                "android.permission.BIND_AUTOFILL_FIELD_CLASSIFICATION_SERVICE",
                "android.permission.BIND_AUTOFILL_SERVICE",
                "android.permission.BIND_CACHE_QUOTA_SERVICE",
                "android.permission.BIND_CALL_SERVICE",
                "android.permission.BIND_CARRIER_MESSAGING_SERVICE",
                "android.permission.BIND_CARRIER_SERVICES",
                "android.permission.BIND_CHOOSER_TARGET_SERVICE",
                "android.permission.BIND_COMPANION_DEVICE_MANAGER_SERVICE",
                "android.permission.BIND_CONDITION_PROVIDER_SERVICE",
                "android.permission.BIND_CONNECTION_SERVICE",
                "android.permission.BIND_DEVICE_ADMIN",
                "android.permission.BIND_DIRECTORY_SEARCH",
                "android.permission.BIND_DREAM_SERVICE",
                "android.permission.BIND_EUICC_SERVICE",
                "android.permission.BIND_IMS_SERVICE",
                "android.permission.BIND_INCALL_SERVICE",
                "android.permission.BIND_INPUT_METHOD",
                "android.permission.BIND_INTENT_FILTER_VERIFIER",
                "android.permission.BIND_JOB_SERVICE",
                "android.permission.BIND_KEYGUARD_APPWIDGET",
                "android.permission.BIND_MIDI_DEVICE_SERVICE",
                "android.permission.BIND_NETWORK_RECOMMENDATION_SERVICE",
                "android.permission.BIND_NFC_SERVICE",
                "android.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE",
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                "android.permission.BIND_NOTIFICATION_RANKER_SERVICE",
                "android.permission.BIND_PACKAGE_VERIFIER",
                "android.permission.BIND_PRINT_RECOMMENDATION_SERVICE",
                "android.permission.BIND_PRINT_SERVICE",
                "android.permission.BIND_PRINT_SPOOLER_SERVICE",
                "android.permission.BIND_QUICK_SETTINGS_TILE",
                "android.permission.BIND_REMOTEVIEWS",
                "android.permission.BIND_REMOTE_DISPLAY",
                "android.permission.BIND_RESOLVER_RANKER_SERVICE",
                "android.permission.BIND_ROUTE_PROVIDER",
                "android.permission.BIND_RUNTIME_PERMISSION_PRESENTER_SERVICE",
                "android.permission.BIND_SCREENING_SERVICE",
                "android.permission.BIND_SETTINGS_SUGGESTIONS_SERVICE",
                "android.permission.BIND_SOUND_TRIGGER_DETECTION_SERVICE",
                "android.permission.BIND_TELECOM_CONNECTION_SERVICE",
                "android.permission.BIND_TELEPHONY_DATA_SERVICE",
                "android.permission.BIND_TELEPHONY_NETWORK_SERVICE",
                "android.permission.BIND_TEXTCLASSIFIER_SERVICE",
                "android.permission.BIND_TEXT_SERVICE",
                "android.permission.BIND_TRUST_AGENT",
                "android.permission.BIND_TV_INPUT",
                "android.permission.BIND_TV_REMOTE_SERVICE",
                "android.permission.BIND_VISUAL_VOICEMAIL_SERVICE",
                "android.permission.BIND_VOICE_INTERACTION",
                "android.permission.BIND_VPN_SERVICE",
                "android.permission.BIND_VR_LISTENER_SERVICE",
                "android.permission.BIND_WALLPAPER",
                "android.permission.BLUETOOTH_MAP",
                "android.permission.BLUETOOTH_PRIVILEGED",
                "android.permission.BLUETOOTH_STACK",
                "android.permission.BRICK",
                "android.permission.BRIGHTNESS_SLIDER_USAGE",
                "android.permission.BROADCAST_NETWORK_PRIVILEGED",
                "android.permission.BROADCAST_PACKAGE_REMOVED",
                "android.permission.BROADCAST_SCORE_NETWORKS",
                "android.permission.BROADCAST_SMS",
                "android.permission.BROADCAST_WAP_PUSH",
                "android.permission.CACHE_CONTENT",
                "android.permission.CALL_PRIVILEGED",
                "android.permission.CAMERA_DISABLE_TRANSMIT_LED",
                "android.permission.CAMERA_SEND_SYSTEM_EVENTS",
                "android.permission.CAPTURE_AUDIO_HOTWORD",
                "android.permission.CAPTURE_AUDIO_OUTPUT",
                "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT",
                "android.permission.CAPTURE_TV_INPUT",
                "android.permission.CAPTURE_VIDEO_OUTPUT",
                "android.permission.CARRIER_FILTER_SMS",
                "android.permission.CHANGE_ACCESSIBILITY_VOLUME",
                "android.permission.CHANGE_APP_IDLE_STATE",
                "android.permission.CHANGE_BACKGROUND_DATA_SETTING",
                "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
                "android.permission.CHANGE_CONFIGURATION",
                "android.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST",
                "android.permission.CHANGE_HDMI_CEC_ACTIVE_SOURCE",
                "android.permission.CHANGE_LOWPAN_STATE",
                "android.permission.CHANGE_NETWORK_STATE",
                "android.permission.CHANGE_OVERLAY_PACKAGES",
                "android.permission.CLEAR_APP_CACHE",
                "android.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS",
                "android.permission.CLEAR_APP_USER_DATA",
                "android.permission.CONFIGURE_DISPLAY_BRIGHTNESS",
                "android.permission.CONFIGURE_DISPLAY_COLOR_MODE",
                "android.permission.CONFIGURE_DISPLAY_COLOR_TRANSFORM",
                "android.permission.CONFIGURE_WIFI_DISPLAY",
                "android.permission.CONFIRM_FULL_BACKUP",
                "android.permission.CONNECTIVITY_INTERNAL",
                "android.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS",
                "android.permission.CONTROL_DISPLAY_BRIGHTNESS",
                "android.permission.CONTROL_DISPLAY_SATURATION",
                "android.permission.CONTROL_INCALL_EXPERIENCE",
                "android.permission.CONTROL_KEYGUARD",
                "android.permission.CONTROL_LOCATION_UPDATES",
                "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS",
                "android.permission.CONTROL_VPN",
                "android.permission.CONTROL_WIFI_DISPLAY",
                "android.permission.COPY_PROTECTED_DATA",
                "android.permission.CREATE_USERS",
                "android.permission.CRYPT_KEEPER",
                "android.permission.DELETE_CACHE_FILES",
                "android.permission.DELETE_PACKAGES",
                "android.permission.DEVICE_POWER",
                "android.permission.DIAGNOSTIC",
                "android.permission.DISABLE_INPUT_DEVICE",
                "android.permission.DISPATCH_NFC_MESSAGE",
                "android.permission.DISPATCH_PROVISIONING_MESSAGE",
                "android.permission.DUMP",
                "android.permission.DVB_DEVICE",
                "android.permission.FACTORY_TEST",
                "android.permission.FILTER_EVENTS",
                "android.permission.FORCE_BACK",
                "android.permission.FORCE_PERSISTABLE_URI_PERMISSIONS",
                "android.permission.FORCE_STOP_PACKAGES",
                "android.permission.FOTA_UPDATE",
                "android.permission.FRAME_STATS",
                "android.permission.FREEZE_SCREEN",
                "android.permission.GET_ACCOUNTS_PRIVILEGED",
                "android.permission.GET_APP_GRANTED_URI_PERMISSIONS",
                "android.permission.GET_APP_OPS_STATS",
                "android.permission.GET_DETAILED_TASKS",
                "android.permission.GET_INTENT_SENDER_INTENT",
                "android.permission.GET_PACKAGE_IMPORTANCE",
                "android.permission.GET_PASSWORD",
                "android.permission.GET_PROCESS_STATE_AND_OOM_SCORE",
                "android.permission.GET_TOP_ACTIVITY_INFO",
                "android.permission.GLOBAL_SEARCH",
                "android.permission.GLOBAL_SEARCH_CONTROL",
                "android.permission.GRANT_REVOKE_PERMISSIONS",
                "android.permission.GRANT_RUNTIME_PERMISSIONS",
                "android.permission.HARDWARE_TEST",
                "android.permission.HDMI_CEC",
                "android.permission.HIDE_NON_SYSTEM_OVERLAY_WINDOWS",
                "android.permission.INJECT_EVENTS",
                "android.permission.INSTALL_GRANT_RUNTIME_PERMISSIONS",
                "android.permission.INSTALL_LOCATION_PROVIDER",
                "android.permission.INSTALL_PACKAGES",
                "android.permission.INSTALL_PACKAGE_UPDATES",
                "android.permission.INSTALL_SELF_UPDATES",
                "android.permission.INSTANT_APP_FOREGROUND_SERVICE",
                "android.permission.INTENT_FILTER_VERIFICATION_AGENT",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERNAL_DELETE_CACHE_FILES",
                "android.permission.INTERNAL_SYSTEM_WINDOW",
                "android.permission.INVOKE_CARRIER_SETUP",
                "android.permission.KILL_UID",
                "android.permission.LAUNCH_TRUST_AGENT_SETTINGS",
                "android.permission.LOCAL_MAC_ADDRESS",
                "android.permission.LOCATION_HARDWARE",
                "android.permission.LOOP_RADIO",
                "android.permission.MAGNIFY_DISPLAY",
                "android.permission.MANAGE_ACTIVITY_STACKS",
                "android.permission.MANAGE_APP_OPS_MODES",
                "android.permission.MANAGE_APP_OPS_RESTRICTIONS",
                "android.permission.MANAGE_APP_TOKENS",
                "android.permission.MANAGE_AUDIO_POLICY",
                "android.permission.MANAGE_AUTO_FILL",
                "android.permission.MANAGE_BIND_INSTANT_SERVICE",
                "android.permission.MANAGE_BLUETOOTH_WHEN_PERMISSION_REVIEW_REQUIRED",
                "android.permission.MANAGE_CAMERA",
                "android.permission.MANAGE_CARRIER_OEM_UNLOCK_STATE",
                "android.permission.MANAGE_CA_CERTIFICATES",
                "android.permission.MANAGE_DEVICE_ADMINS",
                "android.permission.MANAGE_DOCUMENTS",
                "android.permission.MANAGE_FINGERPRINT",
                "android.permission.MANAGE_IPSEC_TUNNELS",
                "android.permission.MANAGE_LOWPAN_INTERFACES",
                "android.permission.MANAGE_MEDIA_PROJECTION",
                "android.permission.MANAGE_NETWORK_POLICY",
                "android.permission.MANAGE_NOTIFICATIONS",
                "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS",
                "android.permission.MANAGE_SCOPED_ACCESS_DIRECTORY_PERMISSIONS",
                "android.permission.MANAGE_SENSORS",
                "android.permission.MANAGE_SLICE_PERMISSIONS",
                "android.permission.MANAGE_SOUND_TRIGGER",
                "android.permission.MANAGE_SUBSCRIPTION_PLANS",
                "android.permission.MANAGE_USB",
                "android.permission.MANAGE_USERS",
                "android.permission.MANAGE_USER_OEM_UNLOCK_STATE",
                "android.permission.MANAGE_VOICE_KEYPHRASES",
                "android.permission.MANAGE_WIFI_WHEN_PERMISSION_REVIEW_REQUIRED",
                "android.permission.MARK_NETWORK_SOCKET",
                "android.permission.MASTER_CLEAR",
                "android.permission.MEDIA_CONTENT_CONTROL",
                "android.permission.MODIFY_ACCESSIBILITY_DATA",
                "android.permission.MODIFY_APPWIDGET_BIND_PERMISSIONS",
                "android.permission.MODIFY_AUDIO_ROUTING",
                "android.permission.MODIFY_CELL_BROADCASTS",
                "android.permission.MODIFY_DAY_NIGHT_MODE",
                "android.permission.MODIFY_NETWORK_ACCOUNTING",
                "android.permission.MODIFY_PARENTAL_CONTROLS",
                "android.permission.MODIFY_PHONE_STATE",
                "android.permission.MODIFY_QUIET_MODE",
                "android.permission.MODIFY_THEME_OVERLAY",
                "android.permission.MOUNT_FORMAT_FILESYSTEMS",
                "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
                "android.permission.MOVE_PACKAGE",
                "android.permission.NETWORK_SETTINGS",
                "android.permission.NETWORK_STACK",
                "android.permission.NET_ADMIN",
                "android.permission.NET_TUNNELING",
                "android.permission.NFC_HANDOVER_STATUS",
                "android.permission.NOTIFICATION_DURING_SETUP",
                "android.permission.NOTIFY_PENDING_SYSTEM_UPDATE",
                "android.permission.NOTIFY_TV_INPUTS",
                "android.permission.OBSERVE_APP_USAGE",
                "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS",
                "android.permission.OEM_UNLOCK_STATE",
                "android.permission.OPEN_APPLICATION_DETAILS_OPEN_BY_DEFAULT_PAGE",
                "android.permission.OVERRIDE_WIFI_CONFIG",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.PACKAGE_VERIFICATION_AGENT",
                "android.permission.PACKET_KEEPALIVE_OFFLOAD",
                "android.permission.PEERS_MAC_ADDRESS",
                "android.permission.PERFORM_CDMA_PROVISIONING",
                "android.permission.PERFORM_SIM_ACTIVATION",
                "android.permission.PROVIDE_RESOLVER_RANKER_SERVICE",
                "android.permission.PROVIDE_TRUST_AGENT",
                "android.permission.QUERY_DO_NOT_ASK_CREDENTIALS_ON_BOOT",
                "android.permission.QUERY_TIME_ZONE_RULES",
                "android.permission.READ_BLOCKED_NUMBERS",
                "android.permission.READ_CONTENT_RATING_SYSTEMS",
                "android.permission.READ_DREAM_STATE",
                "android.permission.READ_FRAME_BUFFER",
                "android.permission.READ_INPUT_STATE",
                "android.permission.READ_LOGS",
                "android.permission.READ_LOWPAN_CREDENTIAL",
                "android.permission.READ_NETWORK_USAGE_HISTORY",
                "android.permission.READ_OEM_UNLOCK_STATE",
                "android.permission.READ_PRECISE_PHONE_STATE",
                "android.permission.READ_PRINT_SERVICES",
                "android.permission.READ_PRINT_SERVICE_RECOMMENDATIONS",
                "android.permission.READ_PRIVILEGED_PHONE_STATE",
                "android.permission.READ_RUNTIME_PROFILES",
                "android.permission.READ_SEARCH_INDEXABLES",
                "android.permission.READ_SYSTEM_UPDATE_INFO",
                "android.permission.READ_WALLPAPER_INTERNAL",
                "android.permission.READ_WIFI_CREDENTIAL",
                "android.permission.REAL_GET_TASKS",
                "android.permission.REBOOT",
                "android.permission.RECEIVE_BLUETOOTH_MAP",
                "android.permission.RECEIVE_DATA_ACTIVITY_CHANGE",
                "android.permission.RECEIVE_EMERGENCY_BROADCAST",
                "android.permission.RECEIVE_MEDIA_RESOURCE_USAGE",
                "android.permission.RECEIVE_STK_COMMANDS",
                "android.permission.RECEIVE_WIFI_CREDENTIAL_CHANGE",
                "android.permission.RECOVERY",
                "android.permission.RECOVER_KEYSTORE",
                "android.permission.REGISTER_CALL_PROVIDER",
                "android.permission.REGISTER_CONNECTION_MANAGER",
                "android.permission.REGISTER_SIM_SUBSCRIPTION",
                "android.permission.REGISTER_WINDOW_MANAGER_LISTENERS",
                "android.permission.REMOTE_AUDIO_PLAYBACK",
                "android.permission.REMOVE_DRM_CERTIFICATES",
                "android.permission.REMOVE_TASKS",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.REQUEST_NETWORK_SCORES",
                "android.permission.RESET_FINGERPRINT_LOCKOUT",
                "android.permission.RESET_SHORTCUT_MANAGER_THROTTLING",
                "android.permission.RESTRICTED_VR_ACCESS",
                "android.permission.RETRIEVE_WINDOW_CONTENT",
                "android.permission.RETRIEVE_WINDOW_INFO",
                "android.permission.RETRIEVE_WINDOW_TOKEN",
                "android.permission.REVOKE_RUNTIME_PERMISSIONS",
                "android.permission.RUN_IN_BACKGROUND",
                "android.permission.SCORE_NETWORKS",
                "android.permission.SEND_EMBMS_INTENTS",
                "android.permission.SEND_RESPOND_VIA_MESSAGE",
                "android.permission.SEND_SHOW_SUSPENDED_APP_DETAILS",
                "android.permission.SEND_SMS_NO_CONFIRMATION",
                "android.permission.SERIAL_PORT",
                "android.permission.SET_ACTIVITY_WATCHER",
                "android.permission.SET_ALWAYS_FINISH",
                "android.permission.SET_ANIMATION_SCALE",
                "android.permission.SET_DEBUG_APP",
                "android.permission.SET_DISPLAY_OFFSET",
                "android.permission.SET_HARMFUL_APP_WARNINGS",
                "android.permission.SET_INPUT_CALIBRATION",
                "android.permission.SET_KEYBOARD_LAYOUT",
                "android.permission.SET_MEDIA_KEY_LISTENER",
                "android.permission.SET_ORIENTATION",
                "android.permission.SET_POINTER_SPEED",
                "android.permission.SET_PREFERRED_APPLICATIONS",
                "android.permission.SET_PROCESS_FOREGROUND",
                "android.permission.SET_PROCESS_LIMIT",
                "android.permission.SET_SCREEN_COMPATIBILITY",
                "android.permission.SET_TIME",
                "android.permission.SET_TIME_ZONE",
                "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER",
                "android.permission.SET_WALLPAPER_COMPONENT",
                "android.permission.SHOW_KEYGUARD_MESSAGE",
                "android.permission.SHUTDOWN",
                "android.permission.SIGNAL_PERSISTENT_PROCESSES",
                "android.permission.START_ANY_ACTIVITY",
                "android.permission.START_TASKS_FROM_RECENTS",
                "android.permission.STATSCOMPANION",
                "android.permission.STATUS_BAR",
                "android.permission.STATUS_BAR_SERVICE",
                "android.permission.STOP_APP_SWITCHES",
                "android.permission.STORAGE_INTERNAL",
                "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME",
                "android.permission.SUSPEND_APPS",
                "android.permission.SYSTEM_ALERT_WINDOW",
                "android.permission.TABLET_MODE",
                "android.permission.TEMPORARY_ENABLE_ACCESSIBILITY",
                "android.permission.TEST_BLACKLISTED_PASSWORD",
                "android.permission.TETHER_PRIVILEGED",
                "android.permission.TRIGGER_TIME_ZONE_RULES_CHECK",
                "android.permission.TRUST_LISTENER",
                "android.permission.TV_INPUT_HARDWARE",
                "android.permission.TV_VIRTUAL_REMOTE_CONTROLLER",
                "android.permission.UNLIMITED_SHORTCUTS_API_CALLS",
                "android.permission.UPDATE_APP_OPS_STATS",
                "android.permission.UPDATE_CONFIG",
                "android.permission.UPDATE_DEVICE_STATS",
                "android.permission.UPDATE_LOCK",
                "android.permission.UPDATE_LOCK_TASK_PACKAGES",
                "android.permission.UPDATE_TIME_ZONE_RULES",
                "android.permission.USER_ACTIVITY",
                "android.permission.USE_COLORIZED_NOTIFICATIONS",
                "android.permission.USE_DATA_IN_BACKGROUND",
                "android.permission.USE_RESERVED_DISK",
                "android.permission.VIEW_INSTANT_APPS",
                "android.permission.WATCH_APPOPS",
                "android.permission.WRITE_APN_SETTINGS",
                "android.permission.WRITE_BLOCKED_NUMBERS",
                "android.permission.WRITE_DREAM_STATE",
                "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS",
                "android.permission.WRITE_GSERVICES",
                "android.permission.WRITE_MEDIA_STORAGE",
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.WRITE_SETTINGS",
                "com.android.permission.BIND_EUICC_SERVICE",
                "com.android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS",
                "com.android.voicemail.permission.READ_VOICEMAIL",
                "com.android.voicemail.permission.WRITE_VOICEMAIL"
            };

    /** Constructs a new {@link SystemPermissionsDetector} check */
    public SystemPermissionsDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameNode != null) {
            String permissionName = nameNode.getValue();
            if (Arrays.binarySearch(SYSTEM_PERMISSIONS, permissionName) >= 0) {

                if (permissionName.equals("android.permission.CHANGE_NETWORK_STATE")) {
                    // This permission was briefly (*only* in API level 23) marked
                    // signature permission; it was fixed again in the next API level.
                    return;
                }

                if (permissionName.equals("android.permission.SYSTEM_ALERT_WINDOW")) {
                    // Even though it's a signature permission you *can* get it by
                    // sending an intent with action ACTION_MANAGE_OVERLAY_PERMISSION; see
                    // https://developer.android.com/reference/android/Manifest.permission.html#SYSTEM_ALERT_WINDOW
                    // Therefore, don't flag it here.
                    return;
                }

                if (permissionName.equals("android.permission.REQUEST_INSTALL_PACKAGES")) {
                    // Despite have protection level, it appears to be valid and required
                    // in some scenarios; see bug 73857733 and
                    // https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html
                    return;
                }

                // Special cases: some permissions were added as signature permissions later;
                // look for these and allow it.
                int max = getLastNonSignatureApiLevel(permissionName);
                if (max != -1) {
                    //  android:maxSdkVersion="22"
                    Attr maxAttribute = element.getAttributeNodeNS(ANDROID_URI, "maxSdkVersion");
                    if (maxAttribute != null) {
                        try {
                            int maxValue = Integer.parseInt(maxAttribute.getValue());
                            if (maxValue <= max) {
                                return;
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }

                context.report(
                        ISSUE,
                        element,
                        context.getLocation(nameNode),
                        "Permission is only granted to system apps");
            }
        }
    }

    private static int getLastNonSignatureApiLevel(@NonNull String name) {
        switch (name) {
            case "android.permission.READ_LOGS":
                return 15;
            case "android.permission.SET_ALWAYS_FINISH":
                return 15;
            case "android.permission.SET_ANIMATION_SCALE":
                return 15;
            case "android.permission.SET_DEBUG_APP":
                return 15;
            case "android.permission.SET_PROCESS_LIMIT":
                return 15;
            case "android.permission.SIGNAL_PERSISTENT_PROCESSES":
                return 15;
            case "android.permission.CHANGE_CONFIGURATION":
                return 16;
            case "android.permission.MOUNT_FORMAT_FILESYSTEMS":
                return 16;
            case "android.permission.MOUNT_UNMOUNT_FILESYSTEMS":
                return 16;
            case "com.android.voicemail.permission.READ_VOICEMAIL":
                return 21;
            case "com.android.voicemail.permission.WRITE_VOICEMAIL":
                return 21;
            case "android.permission.ACCESS_MOCK_LOCATION":
                return 22;
            case "android.permission.CHANGE_NETWORK_STATE":
                return 22;
            case "android.permission.CLEAR_APP_CACHE":
                return 22;
            case "android.permission.SYSTEM_ALERT_WINDOW":
                return 22;
            case "android.permission.WRITE_SETTINGS":
                return 22;
            case "android.permission.REQUEST_INSTALL_PACKAGES":
                return 25;
            case "android.permission.SET_TIME_ZONE":
                return 25;
            default:
                return -1;
        }
    }
}
