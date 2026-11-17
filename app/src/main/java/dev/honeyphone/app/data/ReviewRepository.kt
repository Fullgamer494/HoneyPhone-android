package dev.honeyphone.app.data

import dev.honeyphone.app.model.DemoScenario
import dev.honeyphone.app.model.ReviewPresentation

interface ReviewRepository {
    fun scenarios(): List<DemoScenario>
    fun loadDemo(checkId: String): ReviewPresentation
}
interface LiveReviewRepository {
    fun availabilityMessage(): String
}
