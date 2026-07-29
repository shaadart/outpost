package com.bitchat.android.identity

import kotlin.random.Random

object IdentityGenerator {

    private val ADJECTIVES = listOf(
        "Brave", "Silent", "Fierce", "Wild", "Swift", "Bold", "Calm", "Dark", "Deep",
        "Fast", "Free", "Good", "High", "Iron", "Keen", "Long", "Pure", "Rare", "Real",
        "Rich", "Safe", "True", "Vast", "Warm", "Wise", "Cool", "Fair", "Fine", "Firm",
        "Glad", "Grand", "Great", "Hard", "Just", "Kind", "Light", "Mild", "Neat", "New"
    )

    private val NOUNS = listOf(
        "Falcon", "Wolf", "Bear", "Hawk", "Fox", "Lion", "Eagle", "Tiger", "Shark", "Snake",
        "Owl", "Stag", "Raven", "Crow", "Dove", "Swan", "Crane", "Gull", "Lark", "Wren",
        "Finch", "Quail", "Snipe", "Swift", "Tern", "Kite", "Osprey", "Heron", "Egret",
        "Ibis", "Stork", "Coot", "Rail", "Grebe", "Loon", "Teal", "Pintail", "Wigeon", "Scaup"
    )

    /**
     * Generates a random phonetic identifier (e.g., "BraveFalcon").
     */
    fun generatePhoneticIdentifier(): String {
        val adjective = ADJECTIVES[Random.nextInt(ADJECTIVES.size)]
        val noun = NOUNS[Random.nextInt(NOUNS.size)]
        return "$adjective$noun"
    }
}
