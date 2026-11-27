package app.ailiaili.feature.player

import app.ailiaili.core.model.Quality
import app.ailiaili.core.model.unplayableQns

internal fun mergeQualityCatalog(previous: List<Quality>, current: List<Quality>): List<Quality> =
    (current + previous).distinctBy { it.qn }.filterNot { it.qn in unplayableQns }.sortedByDescending { it.qn }
