<!--
  ~ Copyright 2018 The Android Open Source Project
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<androidx.preference.PreferenceScreen
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <Preference
        app:key="messages_header"
        app:title="@string/messages_header"
        app:icon="@drawable/messages"
        app:fragment="${packageName}.${activityClass}$MessagesFragment"/>

    <Preference
        app:key="sync_header"
        app:title="@string/sync_header"
        app:icon="@drawable/sync"
        app:fragment="${packageName}.${activityClass}$SyncFragment"/>

</androidx.preference.PreferenceScreen>
