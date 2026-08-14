package com.jbsan.ldapadvisor.feature.navigation

import android.net.Uri

object Routes {
    const val DASHBOARD = "dashboard"
    const val DIRECTORY = "directory"
    const val DIAGNOSTICS = "diagnostics"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val USERS = "users"
    const val GROUPS = "groups"
    const val COMPUTERS = "computers"
    const val OUS = "ous"
    const val SEARCH = "search"
    const val REPORTS = "reports"
    const val ADVISOR = "advisor"
    const val CONNECTION = "connection"
    const val HISTORY = "history"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val ROOT_DSE = "rootdse"
    const val SCHEMA = "schema"
    const val PROFILE_EDIT = "profile_edit/{profileId}"
    const val PROFILE_CREATE = "profile_create"
    const val OBJECT_DETAILS = "object/{dn}"
    const val USER_DETAIL = "user/{dn}"
    const val GROUP_DETAIL = "group/{dn}"
    const val COMPUTER_DETAIL = "computer/{dn}"
    const val CREATE_USER = "create_user"
    const val COPY_USER = "copy_user/{dn}"
    const val CREATE_GROUP = "create_group"
    const val CREATE_OU = "create_ou"
    const val USER_DIAGNOSTIC = "user_diagnostic"
    const val COMPUTER_DIAGNOSTIC = "computer_diagnostic"
    const val FAVORITES = "favorites"
    const val RAW_LDAP = "raw_ldap"
    const val LICENSES = "licenses"

    fun profileEdit(id: String) = "profile_edit/${Uri.encode(id)}"
    fun objectDetails(dn: String) = "object/${Uri.encode(dn)}"
    fun userDetail(dn: String) = "user/${Uri.encode(dn)}"
    fun copyUser(dn: String) = "copy_user/${Uri.encode(dn)}"
    fun groupDetail(dn: String) = "group/${Uri.encode(dn)}"
    fun computerDetail(dn: String) = "computer/${Uri.encode(dn)}"
    fun computerDiagnostic(query: String = "") =
        if (query.isBlank()) COMPUTER_DIAGNOSTIC else "computer_diagnostic?q=${Uri.encode(query)}"
}

val PrimaryTabs = listOf(
    Routes.DASHBOARD,
    Routes.DIRECTORY,
    Routes.DIAGNOSTICS,
    Routes.PROFILES,
)
