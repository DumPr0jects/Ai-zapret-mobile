package com.zapretmobile.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "zapret_vpn_service"
    const val NOTIFICATION_ID_VPN_ACTIVE = 1001
    const val NOTIFICATION_ID_ERROR = 1002
    const val EXTRA_STRATEGY_ID = "extra_strategy_id"
    const val PREFS_NAME = "zapret_prefs"
    const val PREF_KEY_STRATEGY = "preferred_strategy"
    
    const val FILE_NAME_DPI_PROXY = "dpi-proxy"
    const val FILE_NAME_SING_BOX = "sing-box"
    const val FILE_NAME_SING_BOX_CONFIG = "sing-box.json"
    
    const val VPN_ADDRESS = "10.10.10.1"
    const val VPN_PREFIX_LENGTH = 32
    const val VPN_MTU = 1500
    const val DNS_SERVER_PRIMARY = "1.1.1.1"
}
