package app.ailiaili.core.network

internal actual fun unsupportedStreamQns(streams: List<StreamProbe>): Set<Int> =
    app.ailiaili.core.model.unplayableQns

internal actual val usesLibVlc: Boolean = true
