package app.ailiaili.feature.player

import app.ailiaili.core.model.Episode
import app.ailiaili.core.model.QueueItem
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.VideoPage

internal sealed interface NextPlayback {
    val title: String

    data class Part(val part: VideoPage) : NextPlayback {
        override val title get() = part.part.ifBlank { "P${part.page}" }
    }

    data class Queue(val index: Int, val item: QueueItem) : NextPlayback {
        override val title get() = item.title.ifBlank { item.bvid }
    }

    data class Collection(val episode: Episode) : NextPlayback {
        override val title get() = episode.title.ifBlank { episode.bvid }
    }

    data class Related(val index: Int, val video: VideoItem) : NextPlayback {
        override val title get() = video.title.ifBlank { video.bvid }
    }
}
