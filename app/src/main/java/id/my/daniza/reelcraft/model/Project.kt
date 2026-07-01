package id.my.daniza.reelcraft.model

data class Project(
    val id: String,
    val name: String,
    val thumbnailPath: String? = null,
    val durationUs: Long = 0L,
    val dateCreatedMs: Long = System.currentTimeMillis(),
    val dateModifiedMs: Long = System.currentTimeMillis(),
    val clips: List<Clip> = emptyList(),
    val musicTrack: MusicTrack? = null,
    val aspectRatio: AspectRatio = AspectRatio.SixteenNine,
    val volume: Float = 1f
) {
    val effectiveDurationUs: Long
        get() {
            if (clips.isEmpty()) return 0L
            var total = 0L
            for (i in clips.indices) {
                total += clips[i].effectiveDurationUs
                if (i < clips.size - 1) {
                    val t = clips[i].transitionOut
                    if (t != null && t.type != TransitionType.None) {
                        total -= t.durationUs
                    }
                }
            }
            return total
        }
}

enum class AspectRatio(val label: String, val width: Int, val height: Int) {
    SixteenNine("16:9", 16, 9),
    FourThree("4:3", 4, 3),
    OneOne("1:1", 1, 1),
    NineSixteen("9:16", 9, 16)
}
