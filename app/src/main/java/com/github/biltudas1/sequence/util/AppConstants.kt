package com.github.biltudas1.sequence.util

import com.github.biltudas1.sequence.BuildConfig

object AppConstants {
    const val GITHUB_REPO_URL = "https://github.com/BiltuDas1/Sequence"
    const val LICENSE_URL = "https://github.com/BiltuDas1/Sequence/blob/main/LICENSE"
    const val GITHUB_RELEASES_API_URL = "https://api.github.com/repos/BiltuDas1/Sequence/releases"
    const val DEFAULT_STUN_SERVER = "stun:stun.l.google.com:19302"
    const val COMPATIBLE_SERVER_MAJOR_VERSION = BuildConfig.COMPATIBLE_SERVER_MAJOR_VERSION

    object Routes {
        const val LOGIN = "login"
        const val PERMISSIONS = "permissions"
        const val CONTACTS = "contacts"
        const val SETTINGS = "settings"
        const val ABOUT = "about"
        const val CALL_SETTINGS = "call_settings"
        const val AUDIO_QUALITY = "audio_quality"
        const val DATA_USAGE = "data_usage"
        const val WEBRTC_CONFIG = "webrtc_config"
        const val ROOM_ENTRY = "room_entry"
        const val LOGS = "logs"
        const val WEBRTC_CALL = "webrtc_call/{roomId}?serverUrl={serverUrl}&name={name}&email={email}&isExternal={isExternal}&creationTime={creationTime}"
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
        const val VOICECALL_SEND = "voicecall/send"
        const val VOICECALL_END = "voicecall/end"
        const val VOICECALL_BUSY = "voicecall/busy"
    }
}
