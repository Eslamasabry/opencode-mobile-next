package io.github.eslamasabry.oc.feature.factsbook.presentation

import io.github.eslamasabry.oc.engine.factsbook.FactsBookEngine
import io.github.eslamasabry.oc.engine.factsbook.FactsBookGateway
import io.github.eslamasabry.oc.engine.factsbook.FbResult

/**
 * Cache-first: a page's read as the node last answered it (nothing is sent), for its first frame. Null with nothing
 * kept; the page then loads live exactly as before. The live read always follows and replaces it in place.
 */
internal suspend fun <T> FactsBookEngine.keptRead(read: suspend FactsBookGateway.() -> FbResult<T>): T? =
    (keptNode?.read() as? FbResult.Ok)?.value
