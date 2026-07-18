package com.github.biltudas1.sequence.util

import com.github.biltudas1.sequence.BuildConfig

object AppConstants {
    val GITHUB_REPO_URL = BuildConfig.GITHUB_REPO_URL
    val LICENSE_URL = BuildConfig.LICENSE_URL
    val GITHUB_RELEASES_API_URL = BuildConfig.GITHUB_RELEASES_API_URL
    val DEFAULT_STUN_SERVER = BuildConfig.DEFAULT_STUN_SERVER
    val COMPATIBLE_SERVER_MAJOR_VERSION = BuildConfig.COMPATIBLE_SERVER_MAJOR_VERSION

    object Routes {
        const val LOGIN = "login?showConfig={showConfig}"
        const val EMAIL_LOGIN = "email_login"
        const val EMAIL_REGISTER = "email_register"
        const val REGISTRATION_SUCCESS = "registration_success"
        const val PERMISSIONS = "permissions"
        const val CONTACTS = "contacts"
        const val SETTINGS = "settings?showConfig={showConfig}"
        const val ABOUT = "about"
        const val CONTACT_DETAIL = "contact_detail/{email}?firstName={firstName}&lastName={lastName}"
        const val CALL_SETTINGS = "call_settings"
        const val AUDIO_QUALITY = "audio_quality"
        const val DATA_USAGE = "data_usage"
        const val WEBRTC_CONFIG = "webrtc_config"
        const val ROOM_ENTRY = "room_entry"
        const val LOGS = "logs"
        const val WEBRTC_CALL = "webrtc_call/{roomId}?serverUrl={serverUrl}&name={name}&email={email}&isExternal={isExternal}&creationTime={creationTime}&isOutgoing={isOutgoing}"
    }

    object Api {
        const val VERSION = "version"
        const val CONTACTS = "contacts"
        const val USERS_REGISTER = "users/register"
        const val USERS_LOGIN = "users/login"
        const val CONTACTS_ADD = "contacts/add"
        const val CONTACTS_REMOVE = "contacts/remove"
        const val TOKEN_REFRESH = "token/refresh"
        const val USERS_FCM_TOKEN = "users/fcm-token"
        const val USERS_PRIVACY = "users/privacy"
        const val USERS_LOGOUT = "users/logout"
        const val USERS_APP_VERSION = "users/app-version"
        const val VOICECALL_SEND = "voicecall/send"
        const val VOICECALL_END = "voicecall/end"
        const val VOICECALL_BUSY = "voicecall/busy"
    }
}
