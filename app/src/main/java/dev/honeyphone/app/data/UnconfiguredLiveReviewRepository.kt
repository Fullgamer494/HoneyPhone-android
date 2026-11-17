package dev.honeyphone.app.data

class UnconfiguredLiveReviewRepository : LiveReviewRepository {
    override fun availabilityMessage(): String =
        "La lectura de Wi-Fi y portal se conecta en la integración Android. Esta vista todavía no realizó una revisión."
}
