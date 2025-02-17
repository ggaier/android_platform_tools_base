/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_ALLOW_BACKUP;
import static com.android.SdkConstants.ATTR_FULL_BACKUP_CONTENT;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.ATTR_VERSION_CODE;
import static com.android.SdkConstants.ATTR_VERSION_NAME;
import static com.android.SdkConstants.DRAWABLE_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.SdkConstants.TAG_PERMISSION_GROUP;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_LIBRARY;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.tools.lint.checks.GradleDetector.GMS_GROUP_ID;
import static com.android.utils.XmlUtils.getFirstSubTagByName;
import static com.android.utils.XmlUtils.getNextTagByName;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.MavenRepositories;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.ide.common.resources.ResourceRepository;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.StringHelper;
import com.android.utils.XmlUtils;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Checks for issues in AndroidManifest files such as declaring elements in the wrong order. */
public class ManifestDetector extends Detector implements XmlScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestDetector.class, Scope.MANIFEST_SCOPE);

    /** Wrong order of elements in the manifest */
    public static final Issue ORDER =
            Issue.create(
                    "ManifestOrder",
                    "Incorrect order of elements in manifest",
                    "The <application> tag should appear after the elements which declare "
                            + "which version you need, which features you need, which libraries you "
                            + "need, and so on. In the past there have been subtle bugs (such as "
                            + "themes not getting applied correctly) when the `<application>` tag appears "
                            + "before some of these other elements, so it's best to order your "
                            + "manifest in the logical dependency order.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue USES_SDK =
            Issue.create(
                            "UsesMinSdkAttributes",
                            "Minimum SDK and target SDK attributes not defined",
                            "The manifest should contain a `<uses-sdk>` element which defines the "
                                    + "minimum API Level required for the application to run, "
                                    + "as well as the target version (the highest API level you have tested "
                                    + "the version for).",
                            Category.CORRECTNESS,
                            9,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/guide/topics/manifest/uses-sdk-element.html");

    /** Using a targetSdkVersion that isn't recent */
    public static final Issue TARGET_NEWER =
            Issue.create(
                            "OldTargetApi",
                            "Target SDK attribute is not targeting latest version",
                            "When your application runs on a version of Android that is more recent than your "
                                    + "`targetSdkVersion` specifies that it has been tested with, various compatibility "
                                    + "modes kick in. This ensures that your application continues to work, but it may "
                                    + "look out of place. For example, if the `targetSdkVersion` is less than 14, your "
                                    + "app may get an option button in the UI.\n"
                                    + "\n"
                                    + "To fix this issue, set the `targetSdkVersion` to the highest available value. Then "
                                    + "test your app to make sure everything works correctly. You may want to consult "
                                    + "the compatibility notes to see what changes apply to each version you are adding "
                                    + "support for: "
                                    + "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html "
                                    + "as well as follow this guide:\n"
                                    + "https://developer.android.com/distribute/best-practices/develop/target-sdk.html",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/distribute/best-practices/develop/target-sdk.html")
                    .addMoreInfo(
                            "http://developer.android.com/reference/android/os/Build.VERSION_CODES.html");

    /** Using multiple {@code <uses-sdk>} elements */
    public static final Issue MULTIPLE_USES_SDK =
            Issue.create(
                            "MultipleUsesSdk",
                            "Multiple `<uses-sdk>` elements in the manifest",
                            "The `<uses-sdk>` element should appear just once; the tools will **not** merge the "
                                    + "contents of all the elements so if you split up the attributes across multiple "
                                    + "elements, only one of them will take effect. To fix this, just merge all the "
                                    + "attributes from the various elements into a single <uses-sdk> element.",
                            Category.CORRECTNESS,
                            6,
                            Severity.FATAL,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/guide/topics/manifest/uses-sdk-element.html");

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue WRONG_PARENT =
            Issue.create(
                            "WrongManifestParent",
                            "Wrong manifest parent",
                            "The `<uses-library>` element should be defined as a direct child of the "
                                    + "`<application>` tag, not the `<manifest>` tag or an `<activity>` tag. Similarly, "
                                    + "a `<uses-sdk>` tag must be declared at the root level, and so on. This check "
                                    + "looks for incorrect declaration locations in the manifest, and complains "
                                    + "if an element is found in the wrong place.",
                            Category.CORRECTNESS,
                            6,
                            Severity.FATAL,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/guide/topics/manifest/manifest-intro.html");

    /** Missing a {@code <uses-sdk>} element */
    public static final Issue DUPLICATE_ACTIVITY =
            Issue.create(
                    "DuplicateActivity",
                    "Activity registered more than once",
                    "An activity should only be registered once in the manifest. If it is "
                            + "accidentally registered more than once, then subtle errors can occur, "
                            + "since attribute declarations from the two elements are not merged, so "
                            + "you may accidentally remove previous declarations.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** Documentation URL for app backup. */
    private static final String BACKUP_DOCUMENTATION_URL =
            "https://developer.android.com/training/backup/autosyncapi.html";

    /** Not explicitly defining allowBackup */
    public static final Issue ALLOW_BACKUP =
            Issue.create(
                            "AllowBackup",
                            "AllowBackup/FullBackupContent Problems",
                            "The `allowBackup` attribute determines if an application's data can be backed up "
                                    + "and restored. It is documented at "
                                    + "http://developer.android.com/reference/android/R.attr.html#allowBackup\n"
                                    + "\n"
                                    + "By default, this flag is set to `true`. When this flag is set to `true`, "
                                    + "application data can be backed up and restored by the user using `adb backup` "
                                    + "and `adb restore`.\n"
                                    + "\n"
                                    + "This may have security consequences for an application. `adb backup` allows "
                                    + "users who have enabled USB debugging to copy application data off of the "
                                    + "device. Once backed up, all application data can be read by the user. "
                                    + "`adb restore` allows creation of application data from a source specified "
                                    + "by the user. Following a restore, applications should not assume that the "
                                    + "data, file permissions, and directory permissions were created by the "
                                    + "application itself.\n"
                                    + "\n"
                                    + "Setting `allowBackup=\"false\"` opts an application out of both backup and "
                                    + "restore.\n"
                                    + "\n"
                                    + "To fix this warning, decide whether your application should support backup, "
                                    + "and explicitly set `android:allowBackup=(true|false)\"`.\n"
                                    + "\n"
                                    + "If not set to false, and if targeting API 23 or later, lint will also warn "
                                    + "that you should set `android:fullBackupContent` to configure auto backup.",
                            Category.SECURITY,
                            3,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(BACKUP_DOCUMENTATION_URL)
                    .addMoreInfo(
                            "http://developer.android.com/reference/android/R.attr.html#allowBackup");

    /** Conflicting permission names */
    public static final Issue UNIQUE_PERMISSION =
            Issue.create(
                    "UniquePermission",
                    "Permission names are not unique",
                    "The unqualified names or your permissions must be unique. The reason for this "
                            + "is that at build time, the `aapt` tool will generate a class named `Manifest` "
                            + "which contains a field for each of your permissions. These fields are named "
                            + "using your permission unqualified names (i.e. the name portion after the last "
                            + "dot).\n"
                            + "\n"
                            + "If more than one permission maps to the same field name, that field will "
                            + "arbitrarily name just one of them.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** Using a resource for attributes that do not allow it */
    public static final Issue SET_VERSION =
            Issue.create(
                            "MissingVersion",
                            "Missing application name/version",
                            "You should define the version information for your application.\n"
                                    + "`android:versionCode`: An integer value that represents the version of the "
                                    + "application code, relative to other versions.\n"
                                    + "\n"
                                    + "`android:versionName`: A string value that represents the release version of "
                                    + "the application code, as it should be shown to users.",
                            Category.CORRECTNESS,
                            2,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/tools/publishing/versioning.html#appversioning");

    /** Using a resource for attributes that do not allow it */
    public static final Issue ILLEGAL_REFERENCE =
            Issue.create(
                    "IllegalResourceRef",
                    "Name and version must be integer or string, not resource",
                    "For the `versionCode` attribute, you have to specify an actual integer "
                            + "literal; you cannot use an indirection with a `@dimen/name` resource. "
                            + "Similarly, the `versionName` attribute should be an actual string, not "
                            + "a string resource url.",
                    Category.CORRECTNESS,
                    8,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Declaring a uses-feature multiple time */
    public static final Issue DUPLICATE_USES_FEATURE =
            Issue.create(
                    "DuplicateUsesFeature",
                    "Feature declared more than once",
                    "A given feature should only be declared once in the manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Not explicitly defining application icon */
    public static final Issue APPLICATION_ICON =
            Issue.create(
                            "MissingApplicationIcon",
                            "Missing application icon",
                            "You should set an icon for the application as whole because there is no "
                                    + "default. This attribute must be set as a reference to a drawable resource "
                                    + "containing the image (for example `@drawable/icon`).",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://developer.android.com/tools/publishing/preparing.html#publishing-configure");

    /** Malformed Device Admin */
    public static final Issue DEVICE_ADMIN =
            Issue.create(
                    "DeviceAdmin",
                    "Malformed Device Admin",
                    "If you register a broadcast receiver which acts as a device admin, you must also "
                            + "register an `<intent-filter>` for the action "
                            + "`android.app.action.DEVICE_ADMIN_ENABLED`, without any `<data>`, such that the "
                            + "device admin can be activated/deactivated.\n"
                            + "\n"
                            + "To do this, add\n"
                            + "`<intent-filter>`\n"
                            + "    `<action android:name=\"android.app.action.DEVICE_ADMIN_ENABLED\" />`\n"
                            + "`</intent-filter>`\n"
                            + "to your `<receiver>`.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Using a mock location in a non-debug-specific manifest file */
    public static final Issue MOCK_LOCATION =
            Issue.create(
                    "MockLocation",
                    "Using mock location provider in production",
                    "Using a mock location provider (by requiring the permission "
                            + "`android.permission.ACCESS_MOCK_LOCATION`) should **only** be done "
                            + "in debug builds (or from tests). In Gradle projects, that means you should only "
                            + "request this permission in a test or debug source set specific manifest file.\n"
                            + "\n"
                            + "To fix this, create a new manifest file in the debug folder and move "
                            + "the `<uses-permission>` element there. A typical path to a debug manifest "
                            + "override file in a Gradle project is src/debug/AndroidManifest.xml.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** Defining a value that is overridden by Gradle */
    public static final Issue GRADLE_OVERRIDES =
            Issue.create(
                    "GradleOverrides",
                    "Value overridden by Gradle build script",
                    "The value of (for example) `minSdkVersion` is only used if it is not specified in "
                            + "the `build.gradle` build scripts. When specified in the Gradle build scripts, "
                            + "the manifest value is ignored and can be misleading, so should be removed to "
                            + "avoid ambiguity.",
                    Category.CORRECTNESS,
                    4,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Using drawable rather than mipmap launcher icons */
    public static final Issue MIPMAP =
            Issue.create(
                    "MipmapIcons",
                    "Use Mipmap Launcher Icons",
                    "Launcher icons should be provided in the `mipmap` resource directory. "
                            + "This is the same as the `drawable` resource directory, except resources in "
                            + "the `mipmap` directory will not get stripped out when creating density-specific "
                            + "APKs.\n"
                            + "\n"
                            + "In certain cases, the Launcher app may use a higher resolution asset (than "
                            + "would normally be computed for the device) to display large app shortcuts. "
                            + "If drawables for densities other than the device's resolution have been "
                            + "stripped out, then the app shortcut could appear blurry.\n"
                            + "\n"
                            + "To fix this, move your launcher icons from `drawable-`dpi to `mipmap-`dpi "
                            + "and change references from @drawable/ and R.drawable to @mipmap/ and R.mipmap.\n"
                            + "In Android Studio this lint warning has a quickfix to perform this automatically.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Uses Wear Bind Listener which is deprecated */
    public static final Issue WEARABLE_BIND_LISTENER =
            Issue.create(
                            "WearableBindListener",
                            "Usage of Android Wear BIND_LISTENER is deprecated",
                            "BIND_LISTENER receives all Android Wear events whether the application needs "
                                    + "them or not. This can be inefficient and cause applications to wake up "
                                    + "unnecessarily. With Google Play Services 8.2.0 or later it is recommended to use "
                                    + "a more efficient combination of manifest listeners and api-based live "
                                    + "listeners filtered by action, path and/or path prefix. ",
                            Category.PERFORMANCE,
                            6,
                            Severity.FATAL,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "http://android-developers.blogspot.com/2016/04/deprecation-of-bindlistener.html");

    public static final Issue APP_INDEXING_SERVICE =
            Issue.create(
                            "AppIndexingService",
                            "App Indexing Background Services",
                            "Apps targeting Android 8.0 or higher can no longer rely on background services while listening for updates "
                                    + "to the on-device index. Use a `BroadcastReceiver` for the `UPDATE_INDEX` intent to continue supporting indexing in your app.",
                            Category.CORRECTNESS,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://firebase.google.com/docs/app-indexing/android/personal-content#add-a-broadcast-receiver-to-your-app");

    /** Permission name of mock location permission */
    public static final String MOCK_LOCATION_PERMISSION = "android.permission.ACCESS_MOCK_LOCATION";
    // Error message used by quick fix
    public static final String MISSING_FULL_BACKUP_CONTENT_RESOURCE =
            "Missing `<full-backup-content>` resource";

    private static final GradleCoordinate MIN_WEARABLE_GMS_VERSION =
            GradleCoordinate.parseVersionOnly("8.2.0");

    /** Constructs a new {@link ManifestDetector} check */
    public ManifestDetector() {}

    private boolean mSeenApplication;

    /** Number of times we've seen the <uses-sdk> element */
    private int mSeenUsesSdk;

    /** Activities we've encountered */
    private Set<String> mActivities;

    /** Features we've encountered */
    private Set<String> mUsesFeatures;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mSeenApplication = false;
        mSeenUsesSdk = 0;
        mActivities = new HashSet<>();
        mUsesFeatures = new HashSet<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        Element element = xmlContext.document.getDocumentElement();
        if (element != null) {
            checkDocumentElement(xmlContext, element);
        }

        if (mSeenUsesSdk == 0
                && context.isEnabled(USES_SDK)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            context.report(
                    USES_SDK,
                    Location.create(context.file),
                    "Manifest should specify a minimum API level with "
                            + "`<uses-sdk android:minSdkVersion=\"?\" />`; if it really supports "
                            + "all versions of Android set it to 1.");
        }
    }

    /**
     * Checks that the main {@code <application>} tag specifies both an icon and allowBackup,
     * possibly merged from some upstream dependency
     */
    private void checkMergedApplication(
            @NonNull XmlContext context, Element sourceApplicationElement) {
        if (context.getProject().isLibrary()) {
            return;
        }

        Project mainProject = context.getMainProject();
        Document mergedManifest = mainProject.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }
        Element root = mergedManifest.getDocumentElement();
        if (root == null) {
            return;
        }
        Element application = getFirstSubTagByName(root, TAG_APPLICATION);
        if (application == null) {
            return;
        }

        if (context.isEnabled(ALLOW_BACKUP)) {
            String allowBackup = application.getAttributeNS(ANDROID_URI, ATTR_ALLOW_BACKUP);
            Attr fullBackupNode =
                    application.getAttributeNodeNS(ANDROID_URI, ATTR_FULL_BACKUP_CONTENT);
            if (fullBackupNode != null
                    && fullBackupNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                    && context.getClient().supportsProjectResources()) {
                ResourceRepository resources =
                        context.getClient().getResourceRepository(mainProject, true, false);
                ResourceUrl url = ResourceUrl.parse(fullBackupNode.getValue());
                if (url != null
                        && !url.isFramework()
                        && resources != null
                        && !resources.hasResources(ResourceNamespace.TODO(), url.type, url.name)) {
                    Attr sourceFullBackupNode =
                            sourceApplicationElement.getAttributeNodeNS(
                                    ANDROID_URI, ATTR_FULL_BACKUP_CONTENT);
                    if (sourceFullBackupNode != null) {
                        // defined in this file, not merged from other file. Prefer it, since
                        // we have better source offsets than from manifest merges.
                        fullBackupNode = sourceFullBackupNode;
                    }
                    Location location = context.getValueLocation(fullBackupNode);
                    context.report(
                            ALLOW_BACKUP,
                            fullBackupNode,
                            location,
                            MISSING_FULL_BACKUP_CONTENT_RESOURCE);
                }
            } else if (fullBackupNode == null
                    && !VALUE_FALSE.equals(allowBackup)
                    && mainProject.getTargetSdk() >= 23) {
                if (hasGcmReceiver(application)) {
                    Location location = context.getNameLocation(sourceApplicationElement);
                    context.report(
                            ALLOW_BACKUP,
                            sourceApplicationElement,
                            location,
                            ""
                                    + "On SDK version 23 and up, your app data will be automatically "
                                    + "backed up, and restored on app install. Your GCM regid will not "
                                    + "work across restores, so you must ensure that it is excluded "
                                    + "from the back-up set. Use the attribute "
                                    + "`android:fullBackupContent` to specify an `@xml` resource which "
                                    + "configures which files to backup. More info: "
                                    + BACKUP_DOCUMENTATION_URL);
                } else {
                    Location location = context.getNameLocation(sourceApplicationElement);
                    context.report(
                            ALLOW_BACKUP,
                            sourceApplicationElement,
                            location,
                            ""
                                    + "On SDK version 23 and up, your app data will be automatically "
                                    + "backed up and restored on app install. Consider adding the "
                                    + "attribute `android:fullBackupContent` to specify an `@xml` "
                                    + "resource which configures which files to backup. More info: "
                                    + BACKUP_DOCUMENTATION_URL);
                }
            }

            if ((allowBackup == null || allowBackup.isEmpty() && mainProject.getMinSdk() >= 4)) {
                context.report(
                        ALLOW_BACKUP,
                        sourceApplicationElement,
                        context.getNameLocation(sourceApplicationElement),
                        "Should explicitly set `android:allowBackup` to `true` or "
                                + "`false` (it's `true` by default, and that can have some security "
                                + "implications for the application's data)");
            }
        }

        if (!application.hasAttributeNS(ANDROID_URI, ATTR_ICON)
                && context.isEnabled(APPLICATION_ICON)) {
            LintFix fix = fix().set(ANDROID_URI, ATTR_ICON, "@mipmap/").caretEnd().build();
            context.report(
                    APPLICATION_ICON,
                    context.getNameLocation(sourceApplicationElement),
                    "Should explicitly set `android:icon`, there is no default",
                    fix);
        }
    }

    private void checkDocumentElement(XmlContext context, Element element) {
        Attr codeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_VERSION_CODE);
        if (codeNode != null
                && codeNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                && context.isEnabled(ILLEGAL_REFERENCE)) {
            context.report(
                    ILLEGAL_REFERENCE,
                    element,
                    context.getLocation(codeNode),
                    "The `android:versionCode` cannot be a resource url, it must be "
                            + "a literal integer");
        } else if (codeNode == null
                && context.isEnabled(SET_VERSION)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            LintFix fix = fix().set(ANDROID_URI, ATTR_VERSION_CODE, "").build();
            context.report(
                    SET_VERSION,
                    element,
                    context.getNameLocation(element),
                    "Should set `android:versionCode` to specify the application version",
                    fix);
        }
        Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_VERSION_NAME);
        if (nameNode == null
                && context.isEnabled(SET_VERSION)
                // Not required in Gradle projects; typically defined in build.gradle instead
                // and inserted at build time
                && !context.getMainProject().isGradleProject()) {
            LintFix fix = fix().set(ANDROID_URI, ATTR_VERSION_NAME, "").build();
            context.report(
                    SET_VERSION,
                    element,
                    context.getNameLocation(element),
                    "Should set `android:versionName` to specify the application version",
                    fix);
        }

        checkOverride(context, element, ATTR_VERSION_CODE);
        checkOverride(context, element, ATTR_VERSION_NAME);

        Attr pkgNode = element.getAttributeNode(ATTR_PACKAGE);
        if (pkgNode != null) {
            String pkg = pkgNode.getValue();
            if (pkg.contains("${") && context.getMainProject().isGradleProject()) {
                context.report(
                        GRADLE_OVERRIDES,
                        pkgNode,
                        context.getLocation(pkgNode),
                        "Cannot use placeholder for the package in the manifest; "
                                + "set `applicationId` in `build.gradle` instead");
            }
        }
    }

    private static void checkOverride(XmlContext context, Element element, String attributeName) {
        Project project = context.getProject();
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName);
        if (project.isGradleProject() && attribute != null && context.isEnabled(GRADLE_OVERRIDES)) {
            Variant variant = project.getCurrentVariant();
            if (variant != null) {
                ProductFlavor flavor = variant.getMergedFlavor();
                String gradleValue = null;
                if (ATTR_MIN_SDK_VERSION.equals(attributeName)) {
                    if (element.hasAttributeNS(TOOLS_URI, "overrideLibrary")) {
                        // The manifest may be setting a minSdkVersion here to deliberately
                        // let the manifest merger know that a library dependency's manifest
                        // with a higher value is okay: this value wins. The manifest merger
                        // should really be taking the Gradle file into account instead,
                        // but for now we filter these out; http://b.android.com/186762
                        return;
                    }
                    ApiVersion minSdkVersion = flavor.getMinSdkVersion();
                    gradleValue = minSdkVersion != null ? minSdkVersion.getApiString() : null;
                } else if (ATTR_TARGET_SDK_VERSION.equals(attributeName)) {
                    ApiVersion targetSdkVersion = flavor.getTargetSdkVersion();
                    gradleValue = targetSdkVersion != null ? targetSdkVersion.getApiString() : null;
                } else if (ATTR_VERSION_CODE.equals(attributeName)) {
                    Integer versionCode = flavor.getVersionCode();
                    if (versionCode != null) {
                        gradleValue = versionCode.toString();
                    }
                } else if (ATTR_VERSION_NAME.equals(attributeName)) {
                    gradleValue = flavor.getVersionName();
                } else {
                    assert false : attributeName;
                    return;
                }

                if (gradleValue != null) {
                    String manifestValue = attribute.getValue();

                    String message =
                            String.format(
                                    "This `%1$s` value (`%2$s`) is not used; it is "
                                            + "always overridden by the value specified in the Gradle build "
                                            + "script (`%3$s`)",
                                    attributeName, manifestValue, gradleValue);
                    context.report(
                            GRADLE_OVERRIDES, attribute, context.getLocation(attribute), message);
                }
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_USES_PERMISSION,
                TAG_PERMISSION,
                "permission-tree",
                "permission-group",
                TAG_USES_SDK,
                "uses-configuration",
                TAG_USES_FEATURE,
                "supports-screens",
                "compatible-screens",
                "supports-gl-texture",
                TAG_USES_LIBRARY,
                TAG_ACTIVITY,
                TAG_SERVICE,
                TAG_PROVIDER,
                TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        Node parentNode = element.getParentNode();

        boolean isReceiver = tag.equals(TAG_RECEIVER);
        if (isReceiver) {
            checkDeviceAdmin(context, element);
        }

        if (tag.equals(TAG_USES_LIBRARY)
                || tag.equals(TAG_ACTIVITY)
                || tag.equals(TAG_SERVICE)
                || tag.equals(TAG_PROVIDER)
                || isReceiver) {
            if (!TAG_APPLICATION.equals(parentNode.getNodeName())
                    && context.isEnabled(WRONG_PARENT)) {
                context.report(
                        WRONG_PARENT,
                        element,
                        context.getNameLocation(element),
                        String.format(
                                "The `<%1$s>` element must be a direct child of the <application> element",
                                tag));
            }

            if (tag.equals(TAG_ACTIVITY)) {
                Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                if (nameNode != null) {
                    String name = nameNode.getValue();
                    if (!name.isEmpty()) {
                        String pkg = context.getMainProject().getPackage();
                        if (name.charAt(0) == '.') {
                            name = pkg + name;
                        } else if (name.indexOf('.') == -1) {
                            name = pkg + '.' + name;
                        }
                        if (mActivities.contains(name)) {
                            String message =
                                    String.format(
                                            "Duplicate registration for activity `%1$s`", name);
                            context.report(
                                    DUPLICATE_ACTIVITY,
                                    element,
                                    context.getLocation(nameNode),
                                    message);
                        } else {
                            mActivities.add(name);
                        }
                    }
                }

                checkMipmapIcon(context, element);
            } else if (tag.equals(TAG_SERVICE) && context.getMainProject().isGradleProject()) {
                Project project = context.getMainProject();
                if (project.getTargetSdk() >= 26) {
                    for (Element child : XmlUtils.getSubTagsByName(element, TAG_INTENT_FILTER)) {
                        for (Element innerChild : XmlUtils.getSubTagsByName(child, NODE_ACTION)) {
                            Attr attr = innerChild.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                            if (attr != null
                                    && "com.google.firebase.appindexing.UPDATE_INDEX"
                                            .equals(attr.getValue())) {
                                String message =
                                        "`UPDATE_INDEX` is configured as a service in your app, "
                                                + "which is no longer supported for the API level you're targeting. "
                                                + "Use a `BroadcastReceiver` instead.";
                                context.report(
                                        APP_INDEXING_SERVICE,
                                        attr,
                                        context.getLocation(attr),
                                        message);
                                break;
                            }
                        }
                    }
                }
                Attr bindListenerAttr = null;
                for (Element child : XmlUtils.getSubTagsByName(element, TAG_INTENT_FILTER)) {
                    for (Element innerChild : XmlUtils.getSubTagsByName(child, NODE_ACTION)) {
                        Attr attr = innerChild.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                        if (attr != null
                                && "com.google.android.gms.wearable.BIND_LISTENER"
                                        .equals(attr.getValue())) {
                            bindListenerAttr = attr;
                            break;
                        }
                    }
                }
                if (bindListenerAttr == null) {
                    return;
                }
                // Ensure that the play-services-wearable version dependency is >= 8.2.0
                Variant variant = project.getCurrentVariant();
                if (variant != null) {
                    Dependencies dependencies = variant.getMainArtifact().getDependencies();
                    for (AndroidLibrary library : dependencies.getLibraries()) {
                        if (hasWearableGmsDependency(library)) {
                            context.report(
                                    WEARABLE_BIND_LISTENER,
                                    bindListenerAttr,
                                    context.getLocation(bindListenerAttr),
                                    "The `com.google.android.gms.wearable.BIND_LISTENER`"
                                            + " action is deprecated.");
                            return;
                        }
                    }
                }
                // It's possible they are using an older version of play services so
                // check the build version and report an error if compileSdkVersion >= 24
                if (project.getBuildSdk() >= 24) {
                    File sdkHome = context.getClient().getSdkHome();
                    FileOp fileOp = FileOpUtils.create();
                    File repository =
                            SdkMavenRepository.GOOGLE.getRepositoryLocation(sdkHome, true, fileOp);
                    String message =
                            "The `com.google.android.gms.wearable.BIND_LISTENER`"
                                    + " action is deprecated. Please upgrade to the latest version"
                                    + " of play-services-wearable 8.2.0 or later";
                    if (repository != null) {
                        GradleCoordinate max =
                                MavenRepositories.getHighestInstalledVersion(
                                        GMS_GROUP_ID,
                                        "play-services-wearable",
                                        repository,
                                        null,
                                        false,
                                        fileOp);
                        if (max != null
                                && COMPARE_PLUS_HIGHER.compare(max, MIN_WEARABLE_GMS_VERSION) > 0) {
                            message =
                                    String.format(
                                            "The `com.google.android.gms.wearable.BIND_LISTENER` "
                                                    + "action is deprecated. Please upgrade to the latest available"
                                                    + " version of play-services-wearable: `%1$s`",
                                            max.getRevision());
                        }
                    }

                    context.report(
                            WEARABLE_BIND_LISTENER,
                            bindListenerAttr,
                            context.getLocation(bindListenerAttr),
                            message);
                }
            }

            return;
        }

        if (parentNode != element.getOwnerDocument().getDocumentElement()
                && tag.indexOf(':') == -1
                && context.isEnabled(WRONG_PARENT)) {
            context.report(
                    WRONG_PARENT,
                    element,
                    context.getNameLocation(element),
                    String.format(
                            "The `<%1$s>` element must be a direct child of the "
                                    + "`<manifest>` root element",
                            tag));
        }

        if (tag.equals(TAG_USES_SDK)) {
            mSeenUsesSdk++;

            if (mSeenUsesSdk == 2) { // Only warn when we encounter the first one
                Location location = context.getNameLocation(element);

                // Link up *all* encountered locations in the document
                NodeList elements = element.getOwnerDocument().getElementsByTagName(TAG_USES_SDK);
                Location secondary = null;
                for (int i = elements.getLength() - 1; i >= 0; i--) {
                    Element e = (Element) elements.item(i);
                    if (e != element) {
                        Location l = context.getNameLocation(e);
                        l.setSecondary(secondary);
                        l.setMessage("Also appears here");
                        secondary = l;
                    }
                }
                location.setSecondary(secondary);

                if (context.isEnabled(MULTIPLE_USES_SDK)) {
                    context.report(
                            MULTIPLE_USES_SDK,
                            element,
                            location,
                            "There should only be a single `<uses-sdk>` element in the manifest:"
                                    + " merge these together");
                }
                return;
            }

            if (!element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
                if (context.isEnabled(USES_SDK) && !context.getMainProject().isGradleProject()) {
                    context.report(
                            USES_SDK,
                            element,
                            context.getNameLocation(element),
                            "`<uses-sdk>` tag should specify a minimum API level with "
                                    + "`android:minSdkVersion=\"?\"`");
                }
            } else {
                Attr codeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION);
                if (codeNode != null
                        && codeNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                        && context.isEnabled(ILLEGAL_REFERENCE)) {
                    context.report(
                            ILLEGAL_REFERENCE,
                            element,
                            context.getLocation(codeNode),
                            "The `android:minSdkVersion` cannot be a resource url, it must be "
                                    + "a literal integer (or string if a preview codename)");
                }

                checkOverride(context, element, ATTR_MIN_SDK_VERSION);
            }

            if (!element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
                // Warn if not setting target SDK -- but only if the min SDK is somewhat
                // old so there's some compatibility stuff kicking in (such as the menu
                // button etc)
                if (context.isEnabled(USES_SDK) && !context.getMainProject().isGradleProject()) {
                    context.report(
                            USES_SDK,
                            element,
                            context.getNameLocation(element),
                            "`<uses-sdk>` tag should specify a target API level (the "
                                    + "highest verified version; when running on later versions, "
                                    + "compatibility behaviors may be enabled) with "
                                    + "`android:targetSdkVersion=\"?\"`");
                }
            } else {
                checkOverride(context, element, ATTR_TARGET_SDK_VERSION);

                if (context.isEnabled(TARGET_NEWER)) {
                    Attr targetSdkVersionNode =
                            element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                    if (targetSdkVersionNode != null) {
                        String target = targetSdkVersionNode.getValue();
                        try {
                            int api = Integer.parseInt(target);
                            int highest = context.getClient().getHighestKnownApiLevel();
                            if (api < highest) {
                                LintFix fix =
                                        fix().name("Update targetSdkVersion to " + highest)
                                                .replace()
                                                .pattern("targetSdkVersion\\s*=\\s*[\"'](.*)[\"']")
                                                .with(Integer.toString(highest))
                                                .build();
                                Location location = context.getLocation(targetSdkVersionNode);
                                context.report(
                                        TARGET_NEWER,
                                        element,
                                        location,
                                        "Not targeting the latest versions of Android; compatibility "
                                                + "modes apply. Consider testing and updating this version. "
                                                + "Consult the `android.os.Build.VERSION_CODES` javadoc for details.",
                                        fix);
                            }
                        } catch (NumberFormatException nufe) {
                            // Ignore: AAPT will enforce this.
                        }
                    }
                }
            }

            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
            if (nameNode != null
                    && nameNode.getValue().startsWith(PREFIX_RESOURCE_REF)
                    && context.isEnabled(ILLEGAL_REFERENCE)) {
                context.report(
                        ILLEGAL_REFERENCE,
                        element,
                        context.getLocation(nameNode),
                        "The `android:targetSdkVersion` cannot be a resource url, it must be "
                                + "a literal integer (or string if a preview codename)");
            }
        }

        if (tag.equals(TAG_PERMISSION) || tag.equals(TAG_PERMISSION_GROUP)) {
            ensureUniquePermission(context);
        }

        if (tag.equals(TAG_USES_PERMISSION)) {
            Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (name != null
                    && name.getValue().equals(MOCK_LOCATION_PERMISSION)
                    && context.getMainProject().isGradleProject()
                    && !isDebugOrTestManifest(context, context.file)
                    && context.isEnabled(MOCK_LOCATION)) {
                String message =
                        "Mock locations should only be requested in a test or "
                                + "debug-specific manifest file (typically `src/debug/AndroidManifest.xml`)";
                Location location = context.getLocation(name);
                context.report(MOCK_LOCATION, element, location, message);
            }
        }

        if (tag.equals(TAG_APPLICATION)) {
            mSeenApplication = true;

            checkMergedApplication(context, element);

            if (element.hasAttributeNS(ANDROID_URI, ATTR_ICON)) {
                checkMipmapIcon(context, element);
            }
        } else if (mSeenApplication) {
            if (context.isEnabled(ORDER)) {
                context.report(
                        ORDER,
                        element,
                        context.getNameLocation(element),
                        String.format("`<%1$s>` tag appears after `<application>` tag", tag));
            }

            // Don't complain for *every* element following the <application> tag
            mSeenApplication = false;
        }

        if (tag.equals(TAG_USES_FEATURE)) {
            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameNode != null) {
                String name = nameNode.getValue();
                if (!name.isEmpty()) {
                    if (mUsesFeatures.contains(name)) {
                        String message =
                                String.format("Duplicate declaration of uses-feature `%1$s`", name);
                        context.report(
                                DUPLICATE_USES_FEATURE,
                                element,
                                context.getLocation(nameNode),
                                message);
                    } else {
                        mUsesFeatures.add(name);
                    }
                }
            }
        }
    }

    private boolean checkedUniquePermissions;

    private void ensureUniquePermission(@NonNull XmlContext context) {
        // Only check this for the first encountered manifest permission tag; it will consult
        // the merged manifest to perform a global check and report errors it finds, so we don't
        // need to repeat that for each sibling permission element
        if (checkedUniquePermissions) {
            return;
        }
        checkedUniquePermissions = true;

        Project mainProject = context.getMainProject();
        Document mergedManifest = mainProject.getMergedManifest();
        if (mergedManifest == null) {
            // This only happens when there is a parse error, for example if user
            // is editing the manifest in the IDE and it's currently invalid
            return;
        }

        lookForNonUniqueNames(context, mainProject, mergedManifest, "permission", TAG_PERMISSION);

        lookForNonUniqueNames(
                context, mainProject, mergedManifest, "permission group", TAG_PERMISSION_GROUP);
    }

    private static void lookForNonUniqueNames(
            @NonNull XmlContext context,
            @NonNull Project mainProject,
            @NonNull Document mergedManifest,
            @NonNull String humanReadableName,
            @NonNull String tagName) {
        Map<String, String> nameToFull = null;
        for (Element element = getFirstSubTagByName(mergedManifest.getDocumentElement(), tagName);
                element != null;
                element = getNextTagByName(element, tagName)) {

            Attr nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameNode == null) {
                continue;
            }

            if (manifestMergerSkips(element)) {
                continue;
            }

            String name = nameNode.getValue();
            String base = name.substring(name.lastIndexOf('.') + 1);

            if (name.contains("${applicationId}")
                    && !mainProject.isLibrary()
                    && mainProject.getPackage() != null) {
                name = name.replace("${applicationId}", mainProject.getPackage());
            }

            if (name.contains("${")) {
                // Unknown manifest placeholder: don't try to enforce uniqueness; we don't
                // know whether the values turn out to be identical
                continue;
            }

            if (nameToFull == null) {
                nameToFull = Maps.newHashMap();
            } else if (nameToFull.containsKey(base) && !name.equals(nameToFull.get(base))) {
                String prevName = nameToFull.get(base);
                Location location = context.getLocation(nameNode);
                NodeList siblings = element.getParentNode().getChildNodes();
                for (int i = 0, n = siblings.getLength(); i < n; i++) {
                    Node node = siblings.item(i);
                    if (node == element) {
                        break;
                    } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element sibling = (Element) node;
                        String suffix = '.' + base;
                        if (sibling.getTagName().equals(tagName)) {
                            String b = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                            if (b.endsWith(suffix)) {
                                Location prevLocation = context.getLocation(node);
                                prevLocation.setMessage("Previous " + humanReadableName + " here");
                                location.setSecondary(prevLocation);
                                break;
                            }
                        }
                    }
                }

                String message =
                        String.format(
                                "%1$s name `%2$s` is not unique "
                                        + "(appears in both `%3$s` and `%4$s`)",
                                StringHelper.capitalize(humanReadableName), base, prevName, name);
                context.report(UNIQUE_PERMISSION, element, location, message);
            }

            nameToFull.put(base, name);
        }
    }

    /**
     * Returns true if the manifest merger will skip this element due to a tools:node action
     * attribute
     */
    private static boolean manifestMergerSkips(@NonNull Element element) {
        Attr operation = element.getAttributeNodeNS(TOOLS_URI, "node");
        if (operation != null) {
            String action = operation.getValue();
            if (action.startsWith("remove") || action.equals("replace")) {
                return true;
            }
        }
        return false;
    }

    // Method to check if the app has a gms wearable dependency that
    // matches the specific criteria i.e >= MIN_WEARABLE_GMS_VERSION
    private static boolean hasWearableGmsDependency(AndroidLibrary library) {
        MavenCoordinates mc = library.getResolvedCoordinates();
        // Annotated as non-null, but observed to be null after failed Gradle syncs
        //noinspection ConstantConditions
        if (mc != null
                && mc.getGroupId().equals(GMS_GROUP_ID)
                && mc.getArtifactId().equals("play-services-wearable")) {
            GradleCoordinate gc = GradleCoordinate.parseVersionOnly(mc.getVersion());
            if (COMPARE_PLUS_HIGHER.compare(gc, MIN_WEARABLE_GMS_VERSION) >= 0) {
                return true;
            }
        }
        for (AndroidLibrary dependency : library.getLibraryDependencies()) {
            if (hasWearableGmsDependency(dependency)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given application element has a receiver with an intent filter action for
     * GCM receive
     */
    private static boolean hasGcmReceiver(@NonNull Element application) {
        NodeList applicationChildren = application.getChildNodes();
        for (int i1 = 0, n1 = applicationChildren.getLength(); i1 < n1; i1++) {
            Node applicationChild = applicationChildren.item(i1);
            if (applicationChild.getNodeType() == Node.ELEMENT_NODE
                    && TAG_RECEIVER.equals(applicationChild.getNodeName())) {
                NodeList receiverChildren = applicationChild.getChildNodes();
                for (int i2 = 0, n2 = receiverChildren.getLength(); i2 < n2; i2++) {
                    Node receiverChild = receiverChildren.item(i2);
                    if (receiverChild.getNodeType() == Node.ELEMENT_NODE
                            && TAG_INTENT_FILTER.equals(receiverChild.getNodeName())) {
                        NodeList filterChildren = receiverChild.getChildNodes();
                        for (int i3 = 0, n3 = filterChildren.getLength(); i3 < n3; i3++) {
                            Node filterChild = filterChildren.item(i3);
                            if (filterChild.getNodeType() == Node.ELEMENT_NODE
                                    && NODE_ACTION.equals(filterChild.getNodeName())) {
                                Element action = (Element) filterChild;
                                String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                if ("com.google.android.c2dm.intent.RECEIVE".equals(name)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static void checkMipmapIcon(@NonNull XmlContext context, @NonNull Element element) {
        Attr attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_ICON);
        if (attribute == null) {
            return;
        }
        String icon = attribute.getValue();
        if (icon.startsWith(DRAWABLE_PREFIX)) {
            if (TAG_ACTIVITY.equals(element.getTagName()) && !isLaunchableActivity(element)) {
                return;
            }

            if (context.isEnabled(MIPMAP)
                    // Only complain if this app is skipping some densities
                    && context.getProject().getApplicableDensities() != null) {
                context.report(
                        MIPMAP,
                        element,
                        context.getLocation(attribute),
                        "Should use `@mipmap` instead of `@drawable` for launcher icons");
            }
        }
    }

    static boolean isLaunchableActivity(@NonNull Element activity) {
        return findLaunchableCategoryNode(activity) != null;
    }

    @Nullable
    static Attr findLaunchableCategoryNode(@NonNull Element activity) {
        if (!TAG_ACTIVITY.equals(activity.getTagName())) {
            return null;
        }

        Element child = getFirstSubTagByName(activity, TAG_INTENT_FILTER);
        while (child != null) {
            Element innerChild = getFirstSubTagByName(child, TAG_CATEGORY);
            while (innerChild != null) {
                Attr attribute = innerChild.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                if (attribute != null
                        && attribute.getValue().equals("android.intent.category.LAUNCHER")) {
                    return attribute;
                }
                innerChild = getNextTagByName(innerChild, TAG_CATEGORY);
            }
            child = getNextTagByName(child, TAG_INTENT_FILTER);
        }

        return null;
    }

    /**
     * Returns true iff the given manifest file is in a debug-specific source set, or a test source
     * set
     */
    private static boolean isDebugOrTestManifest(
            @NonNull XmlContext context, @NonNull File manifestFile) {
        AndroidProject model = context.getProject().getGradleProjectModel();
        if (model != null) {
            // Quickly check if it's the main manifest first; that's the most likely scenario
            if (manifestFile.equals(
                    model.getDefaultConfig().getSourceProvider().getManifestFile())) {
                return false;
            }

            // Debug build type?
            for (BuildTypeContainer container : model.getBuildTypes()) {
                if (container.getBuildType().isDebuggable()) {
                    if (manifestFile.equals(container.getSourceProvider().getManifestFile())) {
                        return true;
                    }
                }
            }

            // Test source set?
            for (SourceProviderContainer extra :
                    model.getDefaultConfig().getExtraSourceProviders()) {
                String artifactName = extra.getArtifactName();
                if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)
                        && manifestFile.equals(extra.getSourceProvider().getManifestFile())) {
                    return true;
                }
            }
            for (ProductFlavorContainer container : model.getProductFlavors()) {
                for (SourceProviderContainer extra : container.getExtraSourceProviders()) {
                    String artifactName = extra.getArtifactName();
                    if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)
                            && manifestFile.equals(extra.getSourceProvider().getManifestFile())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void checkDeviceAdmin(XmlContext context, Element element) {
        boolean requiredIntentFilterFound = false;
        boolean deviceAdmin = false;
        Attr locationNode = null;
        for (Element child : XmlUtils.getSubTags(element)) {
            String tagName = child.getTagName();
            if (tagName.equals(TAG_INTENT_FILTER) && !requiredIntentFilterFound) {
                boolean dataFound = false;
                boolean actionFound = false;
                for (Element filterChild : XmlUtils.getSubTags(child)) {
                    String filterTag = filterChild.getTagName();
                    if (filterTag.equals(NODE_ACTION)) {
                        String name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if ("android.app.action.DEVICE_ADMIN_ENABLED".equals(name)) {
                            actionFound = true;
                        }
                    } else if (filterTag.equals(NODE_DATA)) {
                        dataFound = true;
                    }
                }
                if (actionFound && !dataFound) {
                    requiredIntentFilterFound = true;
                }
            } else if (tagName.equals(NODE_METADATA)) {
                Attr valueNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                if (valueNode != null) {
                    String name = valueNode.getValue();
                    if ("android.app.device_admin".equals(name)) {
                        deviceAdmin = true;
                        locationNode = valueNode;
                    }
                }
            }
        }

        if (deviceAdmin && !requiredIntentFilterFound && context.isEnabled(DEVICE_ADMIN)) {
            context.report(
                    DEVICE_ADMIN,
                    locationNode,
                    context.getLocation(locationNode),
                    "You must have an intent filter for action "
                            + "`android.app.action.DEVICE_ADMIN_ENABLED`");
        }
    }
}
